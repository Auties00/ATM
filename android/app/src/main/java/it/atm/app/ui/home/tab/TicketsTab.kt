package it.atm.app.ui.home.tab

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.atm.app.data.remote.rest.Ticket
import it.atm.app.data.remote.rest.TicketStatus
import it.atm.app.ui.components.CardShapeA
import it.atm.app.ui.components.CardShapeB
import it.atm.app.ui.components.rememberShimmerBrush
import it.atm.app.util.DateFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsTab(
    tickets: List<Ticket>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onTicketClick: (Ticket) -> Unit,
    modifier: Modifier = Modifier
) {
    var pullRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(pullRefreshing) {
        if (pullRefreshing) {
            delay(300)
            pullRefreshing = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Tickets",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
        )
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        PullToRefreshBox(
            isRefreshing = pullRefreshing,
            onRefresh = { pullRefreshing = true; onRefresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && tickets.isEmpty()) {
                ShimmerTicketList()
            } else if (tickets.isEmpty()) {
                TicketsEmptyState()
            } else {
                TicketList(
                    tickets = tickets,
                    onTicketClick = onTicketClick
                )
            }
        }
    }
}

@Composable
private fun TicketList(
    tickets: List<Ticket>,
    onTicketClick: (Ticket) -> Unit
) {
    val cardAnimations = remember(tickets.size) {
        List(tickets.size) { Animatable(0f) }
    }
    LaunchedEffect(tickets.size) {
        cardAnimations.forEachIndexed { index, anim ->
            launch {
                delay(index * 100L)
                anim.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 280f))
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(tickets, key = { _, t -> t.ticketId.ifBlank { t.ticketNumber } }) { index, ticket ->
            val progress = if (index < cardAnimations.size) cardAnimations[index].value else 1f
            TicketCard(
                ticket = ticket,
                shape = if (index % 2 == 0) CardShapeA else CardShapeB,
                onClick = { onTicketClick(ticket) },
                modifier = Modifier.graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * 60f
                }
            )
        }
    }
}

@Composable
private fun TicketCard(
    ticket: Ticket,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (ticket.displayStatus) {
        TicketStatus.PURCHASED -> Color(0xFFFFC627)
        TicketStatus.VALIDATED -> Color(0xFF358551)
        TicketStatus.EXPIRED -> Color(0xFFDD0000)
        TicketStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when (ticket.displayStatus) {
        TicketStatus.PURCHASED -> "Purchased"
        TicketStatus.VALIDATED -> "Active"
        TicketStatus.EXPIRED -> "Expired"
        TicketStatus.UNKNOWN -> "Unknown"
    }
    val iconBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    val iconTint = MaterialTheme.colorScheme.secondary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconBg, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.DirectionsBus, null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ticket.description.ifBlank { "Ticket" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                    if (ticket.hasQrCode) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            Icons.Outlined.QrCode2, null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!ticket.route.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Route, null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = ticket.route,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!ticket.purchaseDateTime.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CalendarMonth, null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = DateFormatter.formatDateTime(ticket.purchaseDateTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (ticket.showAmount && ticket.amount > 0) {
                Text(
                    text = "%.2f\u00A0\u20AC".format(ticket.amount),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            Icon(
                Icons.Outlined.ChevronRight, null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun TicketsEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DirectionsBus,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Text(
                text = "No tickets found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Buy a ticket or pull down to refresh",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ShimmerTicketList() {
    val brush = rememberShimmerBrush()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(4) { index ->
            val shape = if (index % 2 == 0) CardShapeA else CardShapeB
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(shape)
                    .background(brush)
            )
        }
    }
}
