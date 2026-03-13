package it.atm.app.domain.repository

import it.atm.app.data.remote.rest.Ticket
import it.atm.app.data.remote.rest.TicketQrCodeResponse
import it.atm.app.util.AppResult

interface TicketRepository {
    suspend fun fetchTickets(token: String, deviceUid: String): AppResult<List<Ticket>>
    suspend fun fetchTicketQrCode(token: String, deviceUid: String, ticketId: String, validationId: String?): AppResult<TicketQrCodeResponse>
    suspend fun validateTicket(token: String, deviceUid: String, ticketId: String): AppResult<Unit>
}
