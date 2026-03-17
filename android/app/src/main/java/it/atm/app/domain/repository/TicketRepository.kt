package it.atm.app.domain.repository

import it.atm.app.data.remote.rest.Ticket
import it.atm.app.data.remote.rest.TicketQrCodeResponse
import it.atm.app.data.remote.rest.TicketValidateCoordinates
import kotlinx.coroutines.flow.Flow


interface TicketRepository {
    suspend fun fetchTickets(token: String, deviceUid: String): Result<List<Ticket>>
    fun getCachedTickets(): Flow<List<Ticket>>
    suspend fun fetchTicketQrCode(token: String, deviceUid: String, ticketId: String, validationId: String?): Result<TicketQrCodeResponse>
    suspend fun validateTicket(
        token: String,
        deviceUid: String,
        ticketId: String,
        coordinates: TicketValidateCoordinates? = null,
        runsNr: Int? = null,
        integratedProgressive: Int? = null,
        qrCodeValidationValue: String? = null,
        textValidationValue: String? = null,
        beaconValidationValue: String? = null
    ): Result<Unit>
}
