package it.atm.app.domain.repository

import it.atm.app.data.remote.rest.Ticket
import it.atm.app.data.remote.rest.TicketQrCodeResponse


interface TicketRepository {
    suspend fun fetchTickets(token: String, deviceUid: String): Result<List<Ticket>>
    suspend fun fetchTicketQrCode(token: String, deviceUid: String, ticketId: String, validationId: String?): Result<TicketQrCodeResponse>
    suspend fun validateTicket(token: String, deviceUid: String, ticketId: String): Result<Unit>
}
