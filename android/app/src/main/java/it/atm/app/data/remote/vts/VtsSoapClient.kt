package it.atm.app.data.remote.vts

import android.util.Base64
import it.atm.app.auth.AuthConstants
import it.atm.app.domain.model.QrConfig
import it.atm.app.domain.model.VToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import it.atm.app.util.AppLogger
import javax.inject.Inject

class VtsSoapClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    private val serverUrl = AuthConstants.VTS_URL
    private val soapMediaType = "application/soap+xml".toMediaType()

    private fun buildEnvelope(action: String, body: String): String {
        return "<soap:Envelope" +
            " xmlns:soap=\"${AuthConstants.NS_SOAP}\"" +
            " xmlns:vtsr=\"${AuthConstants.NS_VTSR}\"" +
            " xmlns:vtsf=\"${AuthConstants.NS_VTSF}\">" +
            "<soap:Header xmlns:adr=\"${AuthConstants.NS_ADDR}\">" +
            "<adr:To>$serverUrl</adr:To>" +
            "<adr:Action>$action</adr:Action>" +
            "</soap:Header>" +
            "<soap:Body>$body</soap:Body>" +
            "</soap:Envelope>"
    }

    private fun sendSoap(action: String, body: String): String {
        val envelope = buildEnvelope(action, body)
        val request = Request.Builder()
            .url(serverUrl)
            .post(envelope.toByteArray(Charsets.UTF_8).toRequestBody(soapMediaType))
            .addHeader("Content-Type", "application/soap+xml")
            .addHeader("Accept-Charset", "utf-8")
            .build()
        val response = httpClient.newCall(request).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("SOAP request failed: HTTP ${resp.code}")
            }
            resp.body?.string()
                ?: throw RuntimeException("SOAP response body is empty")
        }
    }

    private fun buildInitSessionInput(deviceUid: String): String {
        val initInput =
            "<vts:VTS_InitSessionInput xmlns:vts=\"${AuthConstants.NS_AEP}\">" +
                "<vts:Header Version=\"1\"/>" +
                "<vts:Body>" +
                "<vts:ConnectionRequest" +
                " SystemType=\"${AuthConstants.SYSTEM_TYPE}\"" +
                " SystemSubType=\"${AuthConstants.SYSTEM_SUBTYPE}\"" +
                " DeviceClass=\"SMART\"/>" +
                "<vts:DeviceInfo DeviceType=\"SMART\" DeviceSubType=\"Android\"" +
                " DeviceUID=\"$deviceUid\"/>" +
                "<vts:Authentication AuthenticationScheme=\"none\"/>" +
                "<vts:ClientPreferences IdleTimeoutSec=\"600\"" +
                " UserLangType=\"IT\" ErrorMsgLangType=\"IT\"/>" +
                "<vts:ApplicationInfo" +
                " ApplicationKeyId=\"${AuthConstants.APP_KEY}\"" +
                " ApplicationName=\"ATM\"" +
                " ApplicationVersion=\"${AuthConstants.APP_VERSION}\"" +
                " ApplicationSignature=\"${AuthConstants.CERT_SHA256_B64}\"" +
                " SdkVersion=\"${AuthConstants.SDK_VERSION}\"" +
                " ApplicationSignatureSubjectDN=\"${AuthConstants.CERT_SUBJECT_DN}\"" +
                " ApplicationSignatureIssuerDN=\"${AuthConstants.CERT_SUBJECT_DN}\"" +
                " ApplicationSignatureSerialNumber=\"${AuthConstants.CERT_SERIAL}\"/>" +
                "</vts:Body>" +
                "</vts:VTS_InitSessionInput>"
        return Base64.encodeToString(initInput.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun buildRequestFunction(func: String, params: String, sessionId: String): String {
        val requestXml =
            "<vts:VTS_RequestFunction xmlns:vts=\"${AuthConstants.NS_AEP}\">" +
                "<vts:Header Version=\"1\"/>" +
                "<vts:Body><vts:Parameters $params/></vts:Body>" +
                "</vts:VTS_RequestFunction>"
        val dataIn = Base64.encodeToString(requestXml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "<vtsr:vts_RequestFunction><vtsr:msgIn>" +
            "<vtsf:DataInXml>$dataIn</vtsf:DataInXml>" +
            "<vtsf:FunctionName>$func</vtsf:FunctionName>" +
            "<vtsf:SessionID>$sessionId</vtsf:SessionID>" +
            "</vtsr:msgIn></vtsr:vts_RequestFunction>"
    }

    private fun buildRequestFunctionWithBody(func: String, paramsBody: String, sessionId: String): String {
        val requestXml =
            "<vts:VTS_RequestFunction xmlns:vts=\"${AuthConstants.NS_AEP}\">" +
                "<vts:Header Version=\"1\"/>" +
                "<vts:Body><vts:Parameters>$paramsBody</vts:Parameters></vts:Body>" +
                "</vts:VTS_RequestFunction>"
        val dataIn = Base64.encodeToString(requestXml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "<vtsr:vts_RequestFunction><vtsr:msgIn>" +
            "<vtsf:DataInXml>$dataIn</vtsf:DataInXml>" +
            "<vtsf:FunctionName>$func</vtsf:FunctionName>" +
            "<vtsf:SessionID>$sessionId</vtsf:SessionID>" +
            "</vtsr:msgIn></vtsr:vts_RequestFunction>"
    }

    suspend fun initSession(deviceUid: String): String = withContext(Dispatchers.IO) {
        AppLogger.d("VTS","InitSession deviceUid=%s", deviceUid)
        val dataIn = buildInitSessionInput(deviceUid)
        val body = "<vtsr:vts_InitSession><vtsr:msgIn>" +
            "<vtsf:DataIn>$dataIn</vtsf:DataIn>" +
            "</vtsr:msgIn></vtsr:vts_InitSession>"
        val xml = sendSoap(AuthConstants.SOAP_INIT_SESSION, body)
        val retCode = SoapXmlParser.extractValue(xml, "RetCode")
        if (retCode != null && retCode != "0") {
            val errorStr = SoapXmlParser.extractValue(xml, "ErrorStr") ?: "Unknown error"
            throw RuntimeException("InitSession code $retCode: $errorStr")
        }
        val sessionId = SoapXmlParser.extractValue(xml, "SessionID")
            ?: throw RuntimeException("InitSession: no SessionID in response")
        AppLogger.d("VTS","InitSession success sessionId=%s", sessionId)
        sessionId
    }

    suspend fun closeSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            AppLogger.d("VTS","CloseSession sessionId=%s", sessionId)
            val body = "<vtsr:vts_CloseSession><vtsr:msgIn>" +
                "<vtsf:SessionID>$sessionId</vtsf:SessionID>" +
                "</vtsr:msgIn></vtsr:vts_CloseSession>"
            sendSoap(AuthConstants.SOAP_CLOSE_SESSION, body)
        } catch (_: Exception) {
            AppLogger.w("VTS","CloseSession failed (non-fatal)")
        }
    }

    suspend fun setClientInfo(sessionId: String) = withContext(Dispatchers.IO) {
        AppLogger.d("VTS","SetClientInfo")
        val paramsBody =
            "<vts:ClientPreferences IdleTimeoutSec=\"600\"" +
                " UserLangType=\"IT\" ErrorMsgLangType=\"IT\"/>" +
                "<UserData PhotoImage=\"JPG\"/>"
        val body = buildRequestFunctionWithBody("vts_FuncSetClientInfo", paramsBody, sessionId)
        sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
    }

    suspend fun getClientInfo(sessionId: String, email: String = ""): String = withContext(Dispatchers.IO) {
        AppLogger.d("VTS","GetClientInfo email=%s", email)
        val params = "EMail=\"$email\" RequestPhotoImage=\"false\""
        val body = buildRequestFunction("vts_FuncGetClientInfo", params, sessionId)
        sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
    }

    suspend fun assignDeviceToUser(sessionId: String, userId: String, deviceUid: String): String = withContext(Dispatchers.IO) {
        AppLogger.d("VTS","AssignDeviceToUser userId=%s deviceUid=%s", userId, deviceUid)
        val params = "InsertMode=\"assign\" UserId=\"$userId\" DeviceUID=\"$deviceUid\""
        val body = buildRequestFunction("vts_FuncSetClientInfo", params, sessionId)
        sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
    }

    suspend fun setupSession(deviceUid: String) {
        val sessionId = initSession(deviceUid)
        try {
            setClientInfo(sessionId)
            getServerInfo(sessionId)
            getClientInfo(sessionId)
        } finally {
            closeSession(sessionId)
        }
    }

    suspend fun changeVTokenStatus(sessionId: String, vtokenUid: String, status: Int = 0) = withContext(Dispatchers.IO) {
        AppLogger.d("VTS","ChangeVTokenStatus uid=%s status=%d", vtokenUid, status)
        val params = "VTokenUID=\"$vtokenUid\" VTokenStatus=\"$status\""
        val body = buildRequestFunction("vts_FuncChangeVTokenStatus", params, sessionId)
        sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
    }

    suspend fun generateVToken(sessionId: String, vtokenUid: String, signatureCount: Int = 1) = withContext(Dispatchers.IO) {
        AppLogger.d("VTS","GenerateVToken uid=%s sigCount=%d", vtokenUid, signatureCount)
        val params = "VTokenUID=\"$vtokenUid\" SignatureCount=\"$signatureCount\""
        val body = buildRequestFunction("vts_FuncGenerateVToken", params, sessionId)
        sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
    }

    suspend fun setupAndAssignDevice(deviceUid: String, accessToken: String) {
        val vtsEmail = deriveVtsEmail(accessToken)
        AppLogger.d("VTS","setupAndAssignDevice email=%s", vtsEmail)
        val sessionId = initSession(deviceUid)
        try {
            setClientInfo(sessionId)
            getServerInfo(sessionId)
            val clientInfoXml = getClientInfo(sessionId, vtsEmail)
            val userIds = SoapXmlParser.parseUserIds(clientInfoXml)
            AppLogger.d("VTS","GetClientInfo userIds=%s", userIds)

            val targetUserId = userIds.lastOrNull()
            if (targetUserId != null) {
                assignDeviceToUser(sessionId, targetUserId, deviceUid)
                AppLogger.d("VTS","Assigned device to userId=%s", targetUserId)

                val devicesXml = getDevices(sessionId)
                val allDevices = SoapXmlParser.parseDeviceUids(devicesXml)
                AppLogger.d("VTS","Devices: %s", allDevices)
                for (srcDevice in allDevices) {
                    if (srcDevice == deviceUid) continue
                    try {
                        moveVTokens(sessionId, srcDevice, deviceUid)
                        AppLogger.d("VTS","Moved VTokens from %s to %s", srcDevice, deviceUid)
                    } catch (e: Exception) {
                        AppLogger.w("VTS","MoveVTokens from %s failed: %s", srcDevice, e.message)
                    }
                }

                val vtokens = getVTokenList(sessionId, deviceUid)
                if (vtokens.isNotEmpty()) {
                    AppLogger.d("VTS","Found %d tokens, generating...", vtokens.size)
                    for (vt in vtokens) {
                        try {
                            generateVToken(sessionId, vt.uid, vt.signatureCount.coerceAtLeast(1))
                        } catch (_: Exception) {}
                    }
                }
            }
        } finally {
            closeSession(sessionId)
        }
    }

    private fun deriveVtsEmail(accessToken: String): String {
        val parts = accessToken.split(".")
        if (parts.size < 2) return ""
        val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        val sub = try {
            Json.parseToJsonElement(payload).jsonObject["sub"]?.jsonPrimitive?.content
        } catch (_: Exception) { null } ?: return ""
        val md5 = java.security.MessageDigest.getInstance("MD5").digest(sub.toByteArray())
        return "${@OptIn(ExperimentalStdlibApi::class) md5.toHexString()}@mooney.it"
    }

    suspend fun getDevices(sessionId: String): String = withContext(Dispatchers.IO) {
        val body = buildRequestFunction("vts_FuncGetDevices", "", sessionId)
        sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
    }

    suspend fun moveVTokens(sessionId: String, srcDeviceUid: String, dstDeviceUid: String) = withContext(Dispatchers.IO) {
        val params = "SourceDeviceUID=\"$srcDeviceUid\" DestinationDeviceUID=\"$dstDeviceUid\""
        val body = buildRequestFunction("vts_FuncMoveVTokens", params, sessionId)
        sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
    }

    suspend fun getVTokenList(sessionId: String, deviceUid: String): List<VToken> = withContext(Dispatchers.IO) {
        val params = "DeviceUID=\"$deviceUid\" ListType=\"all\" VTokenStatus=\"0\""
        val body = buildRequestFunction("vts_FuncGetVtokenList", params, sessionId)
        val xml = sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
        val decoded = SoapXmlParser.decodeDataOut(xml) ?: xml
        SoapXmlParser.parseVTokens(decoded)
    }

    suspend fun getVToken(sessionId: String, vtokenUid: String): String? = withContext(Dispatchers.IO) {
        val params = "VTokenUID=\"$vtokenUid\""
        val body = buildRequestFunction("vts_FuncGetVtoken", params, sessionId)
        val xml = sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
        SoapXmlParser.extractDataOutBin(xml)
    }

    suspend fun getInfoCard(sessionId: String, dataOutBin: String): VToken? = withContext(Dispatchers.IO) {
        val paramsXml =
            "<vts:VTS_RequestFunction xmlns:vts=\"${AuthConstants.NS_AEP}\">" +
                "<vts:Header Version=\"1\"/>" +
                "<vts:Body><vts:Parameters" +
                " VtObjectType=\"8\" VtFormat=\"binary\"" +
                " DumpVtHeader=\"false\" DumpVtPayload=\"true\"" +
                " DumpVtValidation=\"true\" DumpVtSignature=\"false\"" +
                " VtCheckSignature=\"false\"/>" +
                "</vts:Body></vts:VTS_RequestFunction>"
        val dataInXml = Base64.encodeToString(paramsXml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val body = "<vtsr:vts_RequestFunction><vtsr:msgIn>" +
            "<vtsf:DataInBin>$dataOutBin</vtsf:DataInBin>" +
            "<vtsf:DataInXml>$dataInXml</vtsf:DataInXml>" +
            "<vtsf:FunctionName>vts_FuncGetInfoCard</vtsf:FunctionName>" +
            "<vtsf:SessionID>$sessionId</vtsf:SessionID>" +
            "</vtsr:msgIn></vtsr:vts_RequestFunction>"
        val xml = sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
        val decoded = SoapXmlParser.decodeDataOut(xml) ?: return@withContext null
        SoapXmlParser.parseContractInfo(decoded)
    }

    suspend fun putVToken(sessionId: String, vtokenUid: String, signatureCount: Int, dataOutBin: String) = withContext(Dispatchers.IO) {
        AppLogger.d("VTS","PutVToken uid=%s sigCount=%d", vtokenUid, signatureCount)
        val params = "VTokenUID=\"$vtokenUid\" SignatureCount=\"$signatureCount\""
        val requestXml =
            "<vts:VTS_RequestFunction xmlns:vts=\"${AuthConstants.NS_AEP}\">" +
                "<vts:Header Version=\"1\"/>" +
                "<vts:Body><vts:Parameters $params/></vts:Body>" +
                "</vts:VTS_RequestFunction>"
        val dataInXml = Base64.encodeToString(requestXml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val body = "<vtsr:vts_RequestFunction><vtsr:msgIn>" +
            "<vtsf:DataInBin>$dataOutBin</vtsf:DataInBin>" +
            "<vtsf:DataInXml>$dataInXml</vtsf:DataInXml>" +
            "<vtsf:FunctionName>vts_FuncPutVToken</vtsf:FunctionName>" +
            "<vtsf:SessionID>$sessionId</vtsf:SessionID>" +
            "</vtsr:msgIn></vtsr:vts_RequestFunction>"
        sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
    }

    suspend fun getServerInfo(sessionId: String): QrConfig = withContext(Dispatchers.IO) {
        AppLogger.d("VTS","GetServerInfo")
        val params = "RequestUserData=\"true\" RequestPhotoImage=\"true\" RequestStatistics=\"true\""
        val body = buildRequestFunction("vts_FuncGetServerInfo", params, sessionId)
        val xml = sendSoap(AuthConstants.SOAP_REQUEST_FUNCTION, body)
        val decoded = SoapXmlParser.decodeDataOut(xml) ?: xml
        SoapXmlParser.parseQrConfig(decoded)
    }
}
