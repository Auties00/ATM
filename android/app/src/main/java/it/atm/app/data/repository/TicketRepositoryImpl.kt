package it.atm.app.data.repository

import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.data.remote.rest.Ticket
import it.atm.app.data.remote.rest.TicketQrCodeResponse
import it.atm.app.domain.repository.TicketRepository
import it.atm.app.util.AppResult
import it.atm.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketRepositoryImpl @Inject constructor(
    private val restClient: AtmRestClient
) : TicketRepository {

    override suspend fun fetchTickets(token: String, deviceUid: String): AppResult<List<Ticket>> {
        AppLogger.d("REST","Fetching all tickets")
        return when (val result = restClient.fetchTickets(token, deviceUid)) {
            is AppResult.Success -> {
                val response = result.data
                AppResult.Success(buildList {
                    response.ticketsTPL?.let(::addAll)
                    response.integratedTicketsTPL?.let(::addAll)
                    response.subscriptions?.let(::addAll)
                    response.ticketsTI?.let(::addAll)
                    response.ticketsItabus?.let(::addAll)
                    response.ticketsGT?.let(::addAll)
                    response.ticketsItalo?.let(::addAll)
                })
            }
            is AppResult.Error -> result
        }
    }

    override suspend fun fetchTicketQrCode(
        token: String,
        deviceUid: String,
        ticketId: String,
        validationId: String?
    ): AppResult<TicketQrCodeResponse> {
        return restClient.fetchTicketQrCode(token, deviceUid, ticketId, validationId)
    }

    override suspend fun validateTicket(token: String, deviceUid: String, ticketId: String): AppResult<Unit> {
        return restClient.validateTicket(token, deviceUid, ticketId)
    }
}
