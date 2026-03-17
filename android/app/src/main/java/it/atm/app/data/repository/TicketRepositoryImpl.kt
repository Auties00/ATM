package it.atm.app.data.repository

import it.atm.app.data.local.TicketDataStore
import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.data.remote.rest.Ticket
import it.atm.app.data.remote.rest.TicketQrCodeResponse
import it.atm.app.data.remote.rest.TicketValidateCoordinates
import it.atm.app.domain.repository.TicketRepository
import it.atm.app.auth.AccountManager

import it.atm.app.util.AppLogger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketRepositoryImpl @Inject constructor(
    private val restClient: AtmRestClient,
    private val ticketDataStore: TicketDataStore,
    private val accountManager: AccountManager
) : TicketRepository {

    override suspend fun fetchTickets(token: String, deviceUid: String): Result<List<Ticket>> {
        AppLogger.d("REST", "Fetching all tickets")
        return restClient.fetchTickets(token, deviceUid).map { response ->
            buildList {
                response.ticketsTPL?.let(::addAll)
                response.integratedTicketsTPL?.let(::addAll)
                response.subscriptions?.let(::addAll)
                response.ticketsTI?.let(::addAll)
                response.ticketsItabus?.let(::addAll)
                response.ticketsGT?.let(::addAll)
                response.ticketsItalo?.let(::addAll)
                response.ticketsOpenMoveGT?.let(::addAll)
                response.ticketsMaritime?.let(::addAll)
                response.ticketsTerravision?.let(::addAll)
            }
        }.also { result ->
            result.onSuccess { tickets ->
                val accountId = accountManager.activeAccountId.value ?: return@onSuccess
                ticketDataStore.saveTickets(accountId, tickets)
            }
        }
    }

    override fun getCachedTickets(): Flow<List<Ticket>> {
        return ticketDataStore.getTickets()
    }

    override suspend fun fetchTicketQrCode(
        token: String,
        deviceUid: String,
        ticketId: String,
        validationId: String?
    ): Result<TicketQrCodeResponse> {
        return restClient.fetchTicketQrCode(token, deviceUid, ticketId, validationId)
    }

    override suspend fun validateTicket(
        token: String,
        deviceUid: String,
        ticketId: String,
        coordinates: TicketValidateCoordinates?,
        runsNr: Int?,
        integratedProgressive: Int?,
        qrCodeValidationValue: String?,
        textValidationValue: String?,
        beaconValidationValue: String?
    ): Result<Unit> {
        return restClient.validateTicket(
            token, deviceUid, ticketId,
            coordinates = coordinates,
            runsNr = runsNr,
            integratedProgressive = integratedProgressive,
            qrCodeValidationValue = qrCodeValidationValue,
            textValidationValue = textValidationValue,
            beaconValidationValue = beaconValidationValue
        )
    }
}
