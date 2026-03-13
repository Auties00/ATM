package it.atm.app.data.remote.vts

import android.util.Xml
import it.atm.app.domain.model.QrConfig
import it.atm.app.domain.model.VToken
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object SoapXmlParser {

    fun extractValue(xml: String, tagLocalName: String): String? {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val local = parser.name.substringAfterLast(":")
                    if (local.equals(tagLocalName, ignoreCase = true)) {
                        val text = parser.nextText()?.trim()
                        if (!text.isNullOrBlank()) return text
                    }
                }
                event = parser.next()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun extractDataOutBin(xml: String): String? {
        return extractValue(xml, "DataOutBin")
    }

    fun decodeDataOut(xml: String): String? {
        val xmlData = extractValue(xml, "DataOutXml")
        if (!xmlData.isNullOrBlank()) {
            return try {
                String(android.util.Base64.decode(xmlData, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                xmlData
            }
        }
        val binData = extractValue(xml, "DataOutBin")
        return if (!binData.isNullOrBlank()) binData else null
    }

    fun parseUserIds(xml: String): List<String> {
        val decoded = decodeDataOut(xml) ?: xml
        return parseAttributeValues(decoded, "UserId")
    }

    fun parseDeviceUids(xml: String): List<String> {
        val decoded = decodeDataOut(xml) ?: xml
        return parseAttributeValues(decoded, "DeviceUID")
    }

    fun parseVTokens(decoded: String): List<VToken> {
        val vtokens = mutableListOf<VToken>()
        val seen = mutableSetOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(decoded))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val uid = parser.getAttributeValue(null, "VTokenUID")
                    if (!uid.isNullOrBlank() && uid !in seen) {
                        seen.add(uid)
                        val sc = parser.getAttributeValue(null, "SignatureCount")?.toIntOrNull() ?: 0
                        vtokens.add(VToken(uid = uid, signatureCount = sc))
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        vtokens.sortByDescending { it.signatureCount }
        return vtokens
    }

    fun parseContractInfo(xml: String): VToken? {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            var bestStart: String? = null
            var bestEnd: String? = null
            var bestDesc: String? = null
            var foundSubscription = false
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val local = parser.name.substringAfterLast(":")
                    if (local.equals("Contract", ignoreCase = true)) {
                        val isSub = parser.getAttributeValue(null, "IsSubscription")
                        val start = parser.getAttributeValue(null, "StartValidityDateTime")
                        val end = parser.getAttributeValue(null, "EndValidityDateTime")
                        val desc = parser.getAttributeValue(null, "TariffDescription")
                        if (isSub == "true" && !foundSubscription) {
                            bestStart = start
                            bestEnd = end
                            bestDesc = desc
                            foundSubscription = true
                        } else if (bestStart == null) {
                            bestStart = start
                            bestEnd = end
                            bestDesc = desc
                        }
                    }
                }
                event = parser.next()
            }
            if (bestStart == null && bestEnd == null && bestDesc == null) return null
            return VToken(
                uid = "",
                signatureCount = 0,
                contractStartValidity = bestStart?.takeIf { it.isNotBlank() },
                contractEndValidity = bestEnd?.takeIf { it.isNotBlank() },
                contractDescription = bestDesc?.takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            return null
        }
    }

    fun parseQrConfig(xml: String): QrConfig {
        var sigType = 0
        var initialKeyId = 0
        var qrCodeFormat = 1
        var signatureKeysVTID = 0
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val local = parser.name.substringAfterLast(":")
                    if (local.equals("SdkParameter", ignoreCase = true)) {
                        val name = parser.getAttributeValue(null, "Name")
                        val value = parser.getAttributeValue(null, "Value")
                        when (name) {
                            "QRCodeSignatureType" -> sigType = value?.toIntOrNull() ?: 0
                            "QRCodeSignatureKey" -> initialKeyId = value?.toIntOrNull() ?: 0
                            "QRCodeFormat" -> qrCodeFormat = value?.toIntOrNull() ?: 1
                            "QRCodeSignatureKeysVTID" -> signatureKeysVTID = value?.toIntOrNull() ?: 0
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return QrConfig(
            sigType = sigType,
            initialKeyId = initialKeyId,
            qrCodeFormat = qrCodeFormat,
            signatureKeysVTID = signatureKeysVTID
        )
    }

    private fun parseAttributeValues(xml: String, attrName: String): List<String> {
        val values = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val value = parser.getAttributeValue(null, attrName)
                    if (!value.isNullOrBlank() && value !in values) {
                        values.add(value)
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return values
    }
}
