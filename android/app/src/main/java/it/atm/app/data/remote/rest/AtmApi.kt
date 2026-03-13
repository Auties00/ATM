package it.atm.app.data.remote.rest

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface AtmApi {
    @GET("Account/Sync")
    suspend fun syncAccount(): Unit

    @POST("GetUserCardsV2")
    suspend fun fetchUserCards(@Body body: RequestBody): UserCardsResponse

    @GET("Checks")
    suspend fun fetchChecks(): ChecksResponse

    @POST("Tickets/Migration")
    suspend fun initiateMigration(@Body body: RequestBody): Unit

    @GET("Migration/ExecuteAepMigrations/{carrier}")
    suspend fun executeAepMigration(@Path("carrier") carrier: String): Unit

    @GET("Tickets")
    suspend fun fetchTickets(): TicketsResponse

    @GET("Ticket/{ticketId}/QrCode")
    suspend fun fetchTicketQrCode(@Path("ticketId") ticketId: String): TicketQrCodeResponse

    @GET("Ticket/{ticketId}/QrCode/{validationId}")
    suspend fun fetchTicketQrCodeWithValidation(
        @Path("ticketId") ticketId: String,
        @Path("validationId") validationId: String
    ): TicketQrCodeResponse

    @POST("Ticket/{ticketId}/Validate")
    suspend fun validateTicket(@Path("ticketId") ticketId: String, @Body body: RequestBody): Unit

    @GET("Account")
    suspend fun fetchAccountProfile(): AccountProfileResponse

    @PUT("Account")
    suspend fun updateAccount(@Body body: RequestBody): Unit
}
