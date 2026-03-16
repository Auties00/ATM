package it.atm.app.data.remote.rest

import it.atm.app.auth.AuthConstants
import it.atm.app.util.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import it.atm.app.util.AppLogger
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AtmRestClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    private fun buildBaseHeaders(
        token: String,
        deviceUid: String
    ): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "client" to "${AuthConstants.CLIENT_ID};${AuthConstants.APP_VERSION}",
        "deviceid" to deviceUid,
        "appbuild" to AuthConstants.APP_BUILD,
        "culture" to "en-GB",
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "User-Agent" to "okhttp/4.4.0"
    )

    private fun buildTicketingHeaders(
        token: String,
        deviceUid: String
    ): Map<String, String> = buildBaseHeaders(token, deviceUid) + mapOf(
        "aepvtssdk" to AuthConstants.SDK_VERSION,
        "deviceuniqueidaep" to deviceUid
    )

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder {
        headers.forEach { (key, value) -> addHeader(key, value) }
        return this
    }

    suspend fun syncAccount(token: String, deviceUid: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        AppLogger.d("REST","syncAccount")
        try {
            val request = Request.Builder()
                .url(AuthConstants.ACCOUNT_SYNC_URL)
                .get()
                .applyHeaders(buildBaseHeaders(token, deviceUid))
                .build()
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext AppResult.Error(RuntimeException("syncAccount failed: HTTP ${resp.code}"))
                }
                AppResult.Success(Unit)
            }
        } catch (e: Exception) {
            AppResult.Error(e)
        }
    }

    suspend fun fetchChecks(token: String, deviceUid: String): AppResult<ChecksResponse> = withContext(Dispatchers.IO) {
        AppLogger.d("REST","fetchChecks")
        try {
            val request = Request.Builder()
                .url("${AuthConstants.TICKETING_BASE}/Checks")
                .get()
                .applyHeaders(buildTicketingHeaders(token, deviceUid))
                .build()
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext AppResult.Error(RuntimeException("fetchChecks failed: HTTP ${resp.code}"))
                }
                val body = resp.body?.string() ?: return@withContext AppResult.Error(RuntimeException("fetchChecks: empty body"))
                AppResult.Success(json.decodeFromString<ChecksResponse>(body))
            }
        } catch (e: Exception) {
            AppResult.Error(e)
        }
    }

    suspend fun fetchUserCards(token: String, deviceUid: String, carrierCode: String): AppResult<List<CardItem>> = withContext(Dispatchers.IO) {
        AppLogger.d("REST","fetchUserCards carrier=%s", carrierCode)
        try {
            val requestBody = json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                buildJsonObject {
                    put("CarrierCode", JsonPrimitive(carrierCode))
                    put("RequestExternalTicketCardData", JsonPrimitive(true))
                }
            )
            val request = Request.Builder()
                .url("${AuthConstants.PASSES_BASE}GetUserCardsV2")
                .post(requestBody.toRequestBody(jsonMediaType))
                .applyHeaders(buildTicketingHeaders(token, deviceUid))
                .build()
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext AppResult.Error(RuntimeException("fetchUserCards failed: HTTP ${resp.code}"))
                }
                val body = resp.body?.string() ?: return@withContext AppResult.Error(RuntimeException("fetchUserCards: empty body"))
                val parsed = json.decodeFromString<UserCardsResponse>(body)
                val cards = mutableListOf<CardItem>()
                parsed.userCards?.forEach { userCard ->
                    userCard.cardsItems?.let { cards.addAll(it) }
                }
                AppResult.Success(cards.toList())
            }
        } catch (e: Exception) {
            AppResult.Error(e)
        }
    }

    suspend fun initiateMigration(token: String, deviceUid: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        AppLogger.d("REST","initiateMigration")
        try {
            val request = Request.Builder()
                .url("${AuthConstants.TICKETING_BASE}/Tickets/Migration")
                .post("{}".toRequestBody(jsonMediaType))
                .applyHeaders(buildTicketingHeaders(token, deviceUid))
                .build()
            val migrationClient = httpClient.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()
            val response = migrationClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty().trim()
                    return@withContext AppResult.Error(RuntimeException("Migration failed: HTTP ${resp.code} $body"))
                }
                AppResult.Success(Unit)
            }
        } catch (e: Exception) {
            AppResult.Error(e)
        }
    }

    suspend fun executeAepMigration(token: String, deviceUid: String, carrierCode: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        AppLogger.d("REST","executeAepMigration carrier=%s", carrierCode)
        try {
            val request = Request.Builder()
                .url("${AuthConstants.TICKETING_BASE}/Migration/ExecuteAepMigrations/$carrierCode")
                .get()
                .applyHeaders(buildTicketingHeaders(token, deviceUid))
                .build()
            val migrationClient = httpClient.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()
            val response = migrationClient.newCall(request).execute()
            response.use { resp ->
                val body = resp.body?.string().orEmpty().trim()
                if (body.isNotEmpty()) {
                    try {
                        val data = json.decodeFromString<MigrationResponse>(body)
                        if (data.success == false) {
                            val errorCode = data.errorCode ?: ""
                            val message = AuthConstants.MIGRATION_ERRORS[errorCode]
                                ?: data.errorMessage
                                ?: data.message
                                ?: "Unknown error ($errorCode)"
                            return@withContext AppResult.Error(RuntimeException(message))
                        }
                    } catch (_: Exception) {}
                }
                if (!resp.isSuccessful) {
                    return@withContext AppResult.Error(RuntimeException("Unknown error"))
                }
                AppResult.Success(Unit)
            }
        } catch (e: Exception) {
            AppResult.Error(e)
        }
    }

    suspend fun fetchTickets(token: String, deviceUid: String): AppResult<TicketsResponse> = withContext(Dispatchers.IO) {
        AppLogger.d("REST","fetchTickets")
        try {
            val request = Request.Builder()
                .url("${AuthConstants.TICKETING_BASE}/Tickets")
                .get()
                .applyHeaders(buildTicketingHeaders(token, deviceUid))
                .build()
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext AppResult.Error(RuntimeException("fetchTickets failed: HTTP ${resp.code}"))
                }
                val body = resp.body?.string() ?: return@withContext AppResult.Error(RuntimeException("fetchTickets: empty body"))
                AppResult.Success(json.decodeFromString<TicketsResponse>(body))
            }
        } catch (e: Exception) {
            AppResult.Error(e)
        }
    }

    suspend fun fetchTicketQrCode(
        token: String,
        deviceUid: String,
        ticketId: String,
        validationId: String?
    ): AppResult<TicketQrCodeResponse> = withContext(Dispatchers.IO) {
        AppLogger.d("REST","fetchTicketQrCode ticketId=%s", ticketId)
        try {
            val path = if (validationId.isNullOrBlank()) {
                "Ticket/$ticketId/QrCode"
            } else {
                "Ticket/$ticketId/QrCode/$validationId"
            }
            val request = Request.Builder()
                .url("${AuthConstants.TICKETING_BASE}/$path")
                .get()
                .applyHeaders(buildTicketingHeaders(token, deviceUid))
                .build()
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext AppResult.Error(RuntimeException("fetchTicketQrCode failed: HTTP ${resp.code}"))
                }
                val body = resp.body?.string() ?: return@withContext AppResult.Error(RuntimeException("fetchTicketQrCode: empty body"))
                AppResult.Success(json.decodeFromString<TicketQrCodeResponse>(body))
            }
        } catch (e: Exception) {
            AppResult.Error(e)
        }
    }

    suspend fun validateTicket(token: String, deviceUid: String, ticketId: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        AppLogger.d("REST","validateTicket ticketId=%s", ticketId)
        try {
            val request = Request.Builder()
                .url("${AuthConstants.TICKETING_BASE}/Ticket/$ticketId/Validate")
                .post("{}".toRequestBody(jsonMediaType))
                .applyHeaders(buildTicketingHeaders(token, deviceUid))
                .build()
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string().orEmpty()
                    return@withContext AppResult.Error(RuntimeException("validateTicket failed: HTTP ${resp.code} $errorBody"))
                }
                AppResult.Success(Unit)
            }
        } catch (e: Exception) {
            AppResult.Error(e)
        }
    }

    suspend fun fetchAccountProfile(token: String, deviceUid: String): AppResult<AccountProfileResponse> = withContext(Dispatchers.IO) {
        AppLogger.d("REST","fetchAccountProfile")
        try {
            val request = Request.Builder()
                .url(AuthConstants.ACCOUNT_URL)
                .get()
                .applyHeaders(buildBaseHeaders(token, deviceUid))
                .build()
            val accountClient = httpClient.newBuilder().readTimeout(10, TimeUnit.SECONDS).build()
            val response = accountClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext AppResult.Error(RuntimeException("fetchAccountProfile failed: HTTP ${resp.code}"))
                }
                val body = resp.body?.string() ?: return@withContext AppResult.Error(RuntimeException("fetchAccountProfile: empty body"))
                AppResult.Success(json.decodeFromString<AccountProfileResponse>(body))
            }
        } catch (e: Exception) {
            AppResult.Error(e)
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
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        AppLogger.d("REST","updateAccount")
        try {
            val bodyObj = buildJsonObject {
                put("name", JsonPrimitive(name))
                put("surname", JsonPrimitive(surname))
                put("phone", JsonPrimitive(phone))
                put("phonePrefix", JsonPrimitive(phonePrefix))
                if (birthDate != null) put("birthDate", JsonPrimitive(birthDate))
            }
            val request = Request.Builder()
                .url(AuthConstants.ACCOUNT_URL)
                .put(bodyObj.toString().toRequestBody(jsonMediaType))
                .applyHeaders(buildBaseHeaders(token, deviceUid))
                .build()
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string()
                    val message = if (errorBody != null) {
                        try {
                            val errJson = json.decodeFromString<MigrationResponse>(errorBody)
                            errJson.errorMessage ?: errJson.message
                        } catch (_: Exception) { null }
                    } else null
                    return@withContext AppResult.Error(RuntimeException(message ?: "Update failed (HTTP ${resp.code})"))
                }
                AppResult.Success(Unit)
            }
        } catch (e: Exception) {
            AppResult.Error(e)
        }
    }
}
