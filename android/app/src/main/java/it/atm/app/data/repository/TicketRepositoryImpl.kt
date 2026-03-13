package it.atm.app.data.repository

import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.data.remote.rest.Ticket
import it.atm.app.data.remote.rest.TicketQrCodeResponse
import it.atm.app.domain.repository.TicketRepository
import it.atm.app.util.AppResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketRepositoryImpl @Inject constructor(
    private val restClient: AtmRestClient
) : TicketRepository {

    override suspend fun fetchTickets(token: String, deviceUid: String): AppResult<List<Ticket>> {
        Timber.tag("REST").d("Fetching all tickets")
        return when (val result = restClient.fetchTickets(token, deviceUid)) {
            is AppResult.Success -> {
                val response = result.data
                val all = mutableListOf<Ticket>()
                response.ticketsTPL?.let { all.addAll(it) }
                response.integratedTicketsTPL?.let { all.addAll(it) }
                response.subscriptions?.let { all.addAll(it) }
                response.ticketsTI?.let { all.addAll(it) }
                response.ticketsItabus?.let { all.addAll(it) }
                response.ticketsGT?.let { all.addAll(it) }
                response.ticketsItalo?.let { all.addAll(it) }
                AppResult.Success(all)
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
