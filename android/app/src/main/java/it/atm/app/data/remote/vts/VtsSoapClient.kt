package it.atm.app.data.remote.vts

import android.util.Base64
import it.atm.app.auth.AuthConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class VtsSoapClient(private val httpClient: OkHttpClient) {

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

    private fun extractXmlValue(xml: String, tag: String): String? {
        val regex = Regex("<[^>]*$tag[^>]*>([^<]+)<")
        val match = regex.find(xml)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun extractDataOutBin(xml: String): String? {
        val regex = Regex("DataOutBin[^>]*>([^<]+)<")
        val match = regex.find(xml)
        val value = match?.groupValues?.get(1)?.trim()
        return if (!value.isNullOrBlank()) value else null
    }

    private fun decodeDataOut(xml: String): String? {
        val xmlRegex = Regex("DataOutXml[^>]*>([^<]+)<")
        val xmlMatch = xmlRegex.find(xml)
        if (xmlMatch != null) {
            val b64 = xmlMatch.groupValues[1].trim()
            if (b64.isNotBlank()) {
                return try {
                    String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                } catch (e: Exception) {
                    b64
                }
            }
        }
        val binRegex = Regex("DataOutBin[^>]*>([^<]+)<")
        val binMatch = binRegex.find(xml)
        val value = binMatch?.groupValues?.get(1)?.trim()
        return if (!value.isNullOrBlank()) value else null
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
        val dataIn = buildInitSessionInput(deviceUid)
        val body = "<vtsr:vts_InitSession><vtsr:msgIn>" +
            "<vtsf:DataIn>$dataIn</vtsf:DataIn>" +
            "</vtsr:msgIn></vtsr:vts_InitSession>"
        val xml = sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_InitSession",
            body
        )
        val retCode = extractXmlValue(xml, "RetCode")
        if (retCode != null && retCode != "0") {
            val errorStr = extractXmlValue(xml, "ErrorStr") ?: "Unknown error"
            throw RuntimeException("InitSession code $retCode: $errorStr")
        }
        extractXmlValue(xml, "SessionID")
            ?: throw RuntimeException("InitSession: no SessionID in response")
    }

    suspend fun closeSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            val body = "<vtsr:vts_CloseSession><vtsr:msgIn>" +
                "<vtsf:SessionID>$sessionId</vtsf:SessionID>" +
                "</vtsr:msgIn></vtsr:vts_CloseSession>"
            sendSoap(
                "http://tempuri.org/IServiceVtsFrontend/vts_CloseSession",
                body
            )
        } catch (_: Exception) {
            // Ignore errors on close, matching Python behavior
        }
    }

    suspend fun setClientInfo(sessionId: String) = withContext(Dispatchers.IO) {
        val paramsBody =
            "<vts:ClientPreferences IdleTimeoutSec=\"600\"" +
                " UserLangType=\"IT\" ErrorMsgLangType=\"IT\"/>" +
                "<UserData PhotoImage=\"JPG\"/>"
        val body = buildRequestFunctionWithBody("vts_FuncSetClientInfo", paramsBody, sessionId)
        sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction",
            body
        )
    }

    suspend fun getClientInfo(sessionId: String, email: String = ""): String = withContext(Dispatchers.IO) {
        val params = "EMail=\"$email\" RequestPhotoImage=\"false\""
        val body = buildRequestFunction("vts_FuncGetClientInfo", params, sessionId)
        sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction",
            body
        )
    }

    suspend fun assignDeviceToUser(sessionId: String, userId: String, deviceUid: String): String = withContext(Dispatchers.IO) {
        val params = "InsertMode=\"assign\" UserId=\"$userId\" DeviceUID=\"$deviceUid\""
        val body = buildRequestFunction("vts_FuncSetClientInfo", params, sessionId)
        sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction",
            body
        )
    }

    fun parseUserIds(clientInfoXml: String): List<String> {
        val decoded = decodeDataOut(clientInfoXml) ?: clientInfoXml
        return Regex("UserId=\"([^\"]*)\"").findAll(decoded)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
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
        val params = "VTokenUID=\"$vtokenUid\" VTokenStatus=\"$status\""
        val body = buildRequestFunction("vts_FuncChangeVTokenStatus", params, sessionId)
        sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction",
            body
        )
    }

    suspend fun generateVToken(sessionId: String, vtokenUid: String, signatureCount: Int = 1) = withContext(Dispatchers.IO) {
        val params = "VTokenUID=\"$vtokenUid\" SignatureCount=\"$signatureCount\""
        val body = buildRequestFunction("vts_FuncGenerateVToken", params, sessionId)
        sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction",
            body
        )
    }

    /**
     * Replicates the native SDK's forceSynchronization:
     * 1. Setup session (SetClientInfo, GetServerInfo, GetClientInfo)
     * 2. Find user by VTS email (MD5(sub)@mooney.it)
     * 3. For each userId, assign our device and try to find + move tokens from other devices
     * 4. If no tokens found via device discovery, try getting VToken list for each userId
     *    by temporarily assigning and checking
     * 5. Generate any tokens that need generation
     */
    suspend fun setupAndAssignDevice(deviceUid: String, accessToken: String) {
        val vtsEmail = deriveVtsEmail(accessToken)
        android.util.Log.d("ATM_VTS", "Derived VTS email=$vtsEmail")
        val sessionId = initSession(deviceUid)
        try {
            setClientInfo(sessionId)
            getServerInfo(sessionId)
            val clientInfoXml = getClientInfo(sessionId, vtsEmail)
            val userIds = parseUserIds(clientInfoXml)
            android.util.Log.d("ATM_VTS", "GetClientInfo vtsEmail=$vtsEmail userIds=$userIds")

            // Assign to the last (most recent) userId — this is the one that owns the subscription.
            // IMPORTANT: only assign to ONE userId to avoid corrupting VTS device mappings.
            val targetUserId = userIds.lastOrNull()
            if (targetUserId != null) {
                assignDeviceToUser(sessionId, targetUserId, deviceUid)
                android.util.Log.d("ATM_VTS", "Assigned device to userId=$targetUserId")

                // Check for other devices under this userId and move their tokens
                val devicesXml = getDevices(sessionId)
                val allDevices = parseDeviceUids(devicesXml)
                android.util.Log.d("ATM_VTS", "Devices for userId=$targetUserId: $allDevices")
                for (srcDevice in allDevices) {
                    if (srcDevice == deviceUid) continue
                    try {
                        moveVTokens(sessionId, srcDevice, deviceUid)
                        android.util.Log.d("ATM_VTS", "Moved VTokens from $srcDevice to $deviceUid")
                    } catch (e: Exception) {
                        android.util.Log.w("ATM_VTS", "MoveVTokens from $srcDevice failed: ${e.message}")
                    }
                }

                // Check if tokens now exist on our device, generate if needed
                val vtokens = getVTokenList(sessionId, deviceUid)
                if (vtokens.isNotEmpty()) {
                    android.util.Log.d("ATM_VTS", "Found ${vtokens.size} tokens, generating...")
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
        // VTS email = MD5(OAuth sub claim) + @mooney.it
        val parts = accessToken.split(".")
        if (parts.size < 2) return ""
        val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        val subMatch = Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(payload)
        val sub = subMatch?.groupValues?.get(1) ?: return ""
        val md5 = java.security.MessageDigest.getInstance("MD5").digest(sub.toByteArray())
        val hex = md5.joinToString("") { "%02x".format(it) }
        return "$hex@mooney.it"
    }

    suspend fun getDevices(sessionId: String): String = withContext(Dispatchers.IO) {
        val body = buildRequestFunction("vts_FuncGetDevices", "", sessionId)
        sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction",
            body
        )
    }

    suspend fun moveVTokens(sessionId: String, srcDeviceUid: String, dstDeviceUid: String) = withContext(Dispatchers.IO) {
        val params = "SourceDeviceUID=\"$srcDeviceUid\" DestinationDeviceUID=\"$dstDeviceUid\""
        val body = buildRequestFunction("vts_FuncMoveVTokens", params, sessionId)
        sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction",
            body
        )
    }

    fun parseDeviceUids(xml: String): List<String> {
        val decoded = decodeDataOut(xml) ?: xml
        return Regex("DeviceUID=\"([^\"]*)\"").findAll(decoded)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    suspend fun getVTokenList(
        sessionId: String,
        deviceUid: String
    ): List<VToken> = withContext(Dispatchers.IO) {
        val params = "DeviceUID=\"$deviceUid\" ListType=\"all\" VTokenStatus=\"0\""
        val body = buildRequestFunction("vts_FuncGetVtokenList", params, sessionId)
        val xml = sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction",
            body
        )
        val decoded = decodeDataOut(xml) ?: xml
        parseVTokens(decoded)
    }

    suspend fun getVToken(
        sessionId: String,
        vtokenUid: String
    ): String? = withContext(Dispatchers.IO) {
        val params = "VTokenUID=\"$vtokenUid\""
        val body = buildRequestFunction("vts_FuncGetVtoken", params, sessionId)
        val xml = sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction",
            body
        )
        extractDataOutBin(xml)
    }

    /**
     * Calls vts_FuncGetInfoCard with the VToken binary data to get contract details
     * (subscription validity dates). Matches HAR entry 189.
     */
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
        val xml = sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction",
            body
        )
        val decoded = decodeDataOut(xml) ?: return@withContext null
        parseContractInfo(decoded)
    }

    private fun parseContractInfo(xml: String): VToken? {
        // Find the first Contract element with IsSubscription="true" or just the first contract
        val contractRegex = Regex("Contract\\s+([^>]+?)\\s*/?>")
        val contracts = contractRegex.findAll(xml).toList()
        // Prefer subscription contracts
        val contractAttrs = contracts.firstOrNull {
            it.groupValues[1].contains("IsSubscription=\"true\"")
        }?.groupValues?.get(1) ?: contracts.firstOrNull()?.groupValues?.get(1) ?: return null

        fun attr(name: String): String? {
            val m = Regex("$name=\"([^\"]*)\"").find(contractAttrs)
            return m?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        }

        return VToken(
            uid = "",
            signatureCount = 0,
            contractStartValidity = attr("StartValidityDateTime"),
            contractEndValidity = attr("EndValidityDateTime"),
            contractDescription = attr("TariffDescription")
        )
    }

    suspend fun getServerInfo(sessionId: String): QrConfig = withContext(Dispatchers.IO) {
        val params = "RequestUserData=\"true\" RequestPhotoImage=\"true\" RequestStatistics=\"true\""
        val body = buildRequestFunction("vts_FuncGetServerInfo", params, sessionId)
        val xml = sendSoap(
            "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction",
            body
        )
        val decoded = decodeDataOut(xml) ?: xml
        parseQrConfig(decoded)
    }

    private fun parseQrConfig(xml: String): QrConfig {
        val paramRegex = Regex("SdkParameter\\s+Name=\"([^\"]+)\"\\s+Value=\"([^\"]*)\"")
        var sigType = 0
        var initialKeyId = 0
        var qrCodeFormat = 1
        var signatureKeysVTID = 0
        for (match in paramRegex.findAll(xml)) {
            when (match.groupValues[1]) {
                "QRCodeSignatureType" -> sigType = match.groupValues[2].toIntOrNull() ?: 0
                "QRCodeSignatureKey" -> initialKeyId = match.groupValues[2].toIntOrNull() ?: 0
                "QRCodeFormat" -> qrCodeFormat = match.groupValues[2].toIntOrNull() ?: 1
                "QRCodeSignatureKeysVTID" -> signatureKeysVTID = match.groupValues[2].toIntOrNull() ?: 0
            }
        }
        return QrConfig(
            sigType = sigType,
            initialKeyId = initialKeyId,
            qrCodeFormat = qrCodeFormat,
            signatureKeysVTID = signatureKeysVTID
        )
    }

    private fun parseVTokens(decoded: String): List<VToken> {
        val vtokens = mutableListOf<VToken>()
        val seen = mutableSetOf<String>()
        val uidRegex = Regex("VTokenUID=\"([^\"]*)\"")
        for (match in uidRegex.findAll(decoded)) {
            val uid = match.groupValues[1]
            if (uid.isBlank() || uid in seen) continue
            seen.add(uid)
            var sc = 0
            val scRegex = Regex("VTokenUID=\"${Regex.escape(uid)}\"[^>]*SignatureCount=\"(\\d+)\"")
            val scMatch = scRegex.find(decoded)
            if (scMatch != null) {
                sc = scMatch.groupValues[1].toIntOrNull() ?: 0
            }
            vtokens.add(VToken(uid = uid, signatureCount = sc))
        }
        vtokens.sortByDescending { it.signatureCount }
        return vtokens
    }
}
