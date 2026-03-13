package it.atm.app.data.remote.rest

import com.google.gson.Gson
import com.google.gson.JsonObject
import it.atm.app.auth.AuthConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AtmRestClient(private val httpClient: OkHttpClient) {

    private val gson = Gson()

    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Base headers sent on all authenticated REST requests.
     * Per the real app HAR, these are: authorization, client, deviceid, appbuild, culture.
     */
    private fun buildBaseHeaders(
        token: String,
        deviceUid: String
    ): Map<String, String> {
        return mutableMapOf(
            "Authorization" to "Bearer $token",
            "client" to "${AuthConstants.CLIENT_ID};${AuthConstants.APP_VERSION}",
            "deviceid" to deviceUid,
            "appbuild" to AuthConstants.APP_BUILD,
            "culture" to "en-GB",
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "User-Agent" to "okhttp/4.4.0"
        )
    }

    /**
     * Extended headers for ticketing/passes/migration endpoints.
     * Per the real app HAR, these additionally include aepvtssdk and deviceuniqueidaep.
     */
    private fun buildTicketingHeaders(
        token: String,
        deviceUid: String
    ): Map<String, String> {
        val headers = buildBaseHeaders(token, deviceUid).toMutableMap()
        headers["aepvtssdk"] = AuthConstants.SDK_VERSION
        headers["deviceuniqueidaep"] = deviceUid
        return headers
    }

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder {
        headers.forEach { (key, value) -> addHeader(key, value) }
        return this
    }

    suspend fun syncAccount(token: String, deviceUid: String) = withContext(Dispatchers.IO) {
        val headers = buildBaseHeaders(token, deviceUid)
        val request = Request.Builder()
            .url(AuthConstants.ACCOUNT_SYNC_URL)
            .get()
            .applyHeaders(headers)
            .build()
        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("syncAccount failed: HTTP ${resp.code}")
            }
        }
    }

    suspend fun fetchChecks(token: String, deviceUid: String): ChecksResponse = withContext(Dispatchers.IO) {
        val headers = buildTicketingHeaders(token, deviceUid)
        val request = Request.Builder()
            .url("${AuthConstants.TICKETING_BASE}/Checks")
            .get()
            .applyHeaders(headers)
            .build()
        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("fetchChecks failed: HTTP ${resp.code}")
            }
            val body = resp.body?.string()
                ?: throw RuntimeException("fetchChecks: empty response body")
            gson.fromJson(body, ChecksResponse::class.java)
        }
    }

    suspend fun fetchUserCards(
        token: String,
        deviceUid: String,
        carrierCode: String
    ): List<CardItem> = withContext(Dispatchers.IO) {
        val headers = buildTicketingHeaders(token, deviceUid)
        val requestBody = gson.toJson(
            mapOf(
                "CarrierCode" to carrierCode,
                "RequestExternalTicketCardData" to true
            )
        )
        val request = Request.Builder()
            .url("${AuthConstants.PASSES_BASE}GetUserCardsV2")
            .post(requestBody.toRequestBody(jsonMediaType))
            .applyHeaders(headers)
            .build()
        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("fetchUserCards failed: HTTP ${resp.code}")
            }
            val body = resp.body?.string()
                ?: throw RuntimeException("fetchUserCards: empty response body")
            val parsed = gson.fromJson(body, UserCardsResponse::class.java)
            val cards = mutableListOf<CardItem>()
            parsed.userCards?.forEach { userCard ->
                userCard.cardsItems?.let { cards.addAll(it) }
            }
            cards
        }
    }

    suspend fun initiateMigration(
        token: String,
        deviceUid: String
    ) = withContext(Dispatchers.IO) {
        val headers = buildTicketingHeaders(token, deviceUid)
        val request = Request.Builder()
            .url("${AuthConstants.TICKETING_BASE}/Tickets/Migration")
            .post("{}".toRequestBody(jsonMediaType))
            .applyHeaders(headers)
            .build()
        val migrationClient = httpClient.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val response = migrationClient.newCall(request).execute()
        response.use { resp ->
            val body = resp.body?.string().orEmpty().trim()
            if (!resp.isSuccessful) {
                throw RuntimeException("Migration failed: HTTP ${resp.code} $body")
            }
        }
    }

    suspend fun executeAepMigration(
        token: String,
        deviceUid: String,
        carrierCode: String
    ) = withContext(Dispatchers.IO) {
        val headers = buildTicketingHeaders(token, deviceUid)
        val request = Request.Builder()
            .url("${AuthConstants.TICKETING_BASE}/Migration/ExecuteAepMigrations/$carrierCode")
            .get()
            .applyHeaders(headers)
            .build()
        val migrationClient = httpClient.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val response = migrationClient.newCall(request).execute()
        response.use { resp ->
            val body = resp.body?.string().orEmpty().trim()
            if (body.isNotEmpty()) {
                val data = gson.fromJson(body, JsonObject::class.java)
                if (data?.get("success")?.asBoolean == false) {
                    val errorCode = data.get("errorCode")?.asString ?: ""
                    val message = AuthConstants.MIGRATION_ERRORS[errorCode]
                        ?: data.get("errorMessage")?.asString
                        ?: data.get("message")?.asString
                        ?: "Unknown error ($errorCode)"
                    throw RuntimeException(message)
                }
            }

            if (!resp.isSuccessful) {
                throw RuntimeException("Unknown error")
            }
        }
    }

    // ── Ticketing ─────────────────────────────────────────────────────

    suspend fun fetchTickets(token: String, deviceUid: String): TicketsResponse = withContext(Dispatchers.IO) {
        val headers = buildTicketingHeaders(token, deviceUid)
        val request = Request.Builder()
            .url("${AuthConstants.TICKETING_BASE}/Tickets")
            .get()
            .applyHeaders(headers)
            .build()
        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("fetchTickets failed: HTTP ${resp.code}")
            }
            val body = resp.body?.string()
                ?: throw RuntimeException("fetchTickets: empty response body")
            gson.fromJson(body, TicketsResponse::class.java)
        }
    }

    suspend fun fetchTicketQrCode(
        token: String,
        deviceUid: String,
        ticketId: String,
        validationId: String?
    ): TicketQrCodeResponse = withContext(Dispatchers.IO) {
        val headers = buildTicketingHeaders(token, deviceUid)
        val path = if (validationId.isNullOrBlank()) {
            "Ticket/$ticketId/QrCode"
        } else {
            "Ticket/$ticketId/QrCode/$validationId"
        }
        val request = Request.Builder()
            .url("${AuthConstants.TICKETING_BASE}/$path")
            .get()
            .applyHeaders(headers)
            .build()
        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("fetchTicketQrCode failed: HTTP ${resp.code}")
            }
            val body = resp.body?.string()
                ?: throw RuntimeException("fetchTicketQrCode: empty response body")
            gson.fromJson(body, TicketQrCodeResponse::class.java)
        }
    }

    suspend fun validateTicket(
        token: String,
        deviceUid: String,
        ticketId: String
    ) = withContext(Dispatchers.IO) {
        val headers = buildTicketingHeaders(token, deviceUid)
        val request = Request.Builder()
            .url("${AuthConstants.TICKETING_BASE}/Ticket/$ticketId/Validate")
            .post("{}".toRequestBody(jsonMediaType))
            .applyHeaders(headers)
            .build()
        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string().orEmpty()
                throw RuntimeException("validateTicket failed: HTTP ${resp.code} $errorBody")
            }
        }
    }

    suspend fun fetchAccountProfile(
        token: String,
        deviceUid: String
    ): JsonObject = withContext(Dispatchers.IO) {
        val headers = buildBaseHeaders(token, deviceUid)
        val request = Request.Builder()
            .url(AuthConstants.ACCOUNT_URL)
            .get()
            .applyHeaders(headers)
            .build()
        val accountClient = httpClient.newBuilder()
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val response = accountClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("fetchAccountProfile failed: HTTP ${resp.code}")
            }
            val body = resp.body?.string()
                ?: throw RuntimeException("fetchAccountProfile: empty response body")
            gson.fromJson(body, JsonObject::class.java)
        }
    }

    suspend fun updateAccount(
        token: String,
        deviceUid: String,
        name: String,
        surname: String,
        phone: String,
        phonePrefix: String,
        birthDate: String?
    ) = withContext(Dispatchers.IO) {
        val headers = buildBaseHeaders(token, deviceUid)
        val body = JsonObject().apply {
            addProperty("name", name)
            addProperty("surname", surname)
            addProperty("phone", phone)
            addProperty("phonePrefix", phonePrefix)
            if (birthDate != null) addProperty("birthDate", birthDate)
        }
        val request = Request.Builder()
            .url(AuthConstants.ACCOUNT_URL)
            .put(gson.toJson(body).toRequestBody(jsonMediaType))
            .applyHeaders(headers)
            .build()
        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string()
                val message = if (errorBody != null) {
                    try {
                        val json = gson.fromJson(errorBody, JsonObject::class.java)
                        json.get("message")?.asString
                            ?: json.get("errorMessage")?.asString
                            ?: json.get("error")?.asString
                    } catch (_: Exception) { null }
                } else null
                throw RuntimeException(message ?: "Update failed (HTTP ${resp.code})")
            }
        }
    }
}
