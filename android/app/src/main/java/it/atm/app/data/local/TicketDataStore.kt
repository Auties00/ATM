package it.atm.app.data.local

import it.atm.app.data.local.db.TicketDao
import it.atm.app.data.local.db.TicketEntity
import it.atm.app.data.remote.rest.Ticket
import it.atm.app.auth.AccountManager
import it.atm.app.util.AppLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketDataStore @Inject constructor(
    private val accountManager: AccountManager,
    private val ticketDao: TicketDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveTickets(accountId: String, tickets: List<Ticket>) {
        AppLogger.d("DATA", "Saving %d tickets for account=%s", tickets.size, accountId)
        ticketDao.deleteByAccount(accountId)
        ticketDao.insertAll(tickets.map { ticket ->
            TicketEntity(
                accountId = accountId,
                ticketId = ticket.ticketId,
                ticketJson = json.encodeToString(Ticket.serializer(), ticket)
            )
        })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTickets(): Flow<List<Ticket>> {
        return accountManager.activeAccountId.flatMapLatest { activeId ->
            if (activeId == null) flowOf(emptyList())
            else ticketDao.observeByAccount(activeId).map { entities ->
                entities.mapNotNull { entity ->
                    try {
                        json.decodeFromString(Ticket.serializer(), entity.ticketJson)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }

    suspend fun clearAll() {
        AppLogger.d("DATA", "Clearing ticket data")
        val activeId = accountManager.activeAccountId.value ?: return
        ticketDao.deleteByAccount(activeId)
    }
}
