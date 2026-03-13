package it.atm.app.ui.home.tab

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.rounded.ConfirmationNumber
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.atm.app.data.remote.rest.Subscription
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Asymmetric card shapes — alternating for visual rhythm
private val SubCardShapeA = RoundedCornerShape(
    topStart = 28.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 28.dp
)
private val SubCardShapeB = RoundedCornerShape(
    topStart = 12.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 12.dp
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsTab(
    subscriptions: List<Subscription>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSubscriptionClick: (Int) -> Unit,
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
        // Title
        Text(
            text = "Subscriptions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
        )
        // Progress bar — edge-to-edge between title and content
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
            if (isLoading && subscriptions.isEmpty()) {
                ShimmerSubscriptionList()
            } else if (subscriptions.isEmpty()) {
                EmptyState()
            } else {
                SubscriptionList(
                    subscriptions = subscriptions,
                    onSubscriptionClick = onSubscriptionClick
                )
            }
        }
    }
}

@Composable
private fun SubscriptionList(
    subscriptions: List<Subscription>,
    onSubscriptionClick: (Int) -> Unit
) {
    // Staggered entrance animations — one per visible card
    val cardAnimations = remember(subscriptions.size) {
        List(subscriptions.size) { Animatable(0f) }
    }
    LaunchedEffect(subscriptions.size) {
        cardAnimations.forEachIndexed { index, anim ->
            launch {
                delay(index * 100L)
                anim.animateTo(
                    1f, spring(dampingRatio = 0.65f, stiffness = 280f)
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(subscriptions, key = { _, s -> s.cardCode + s.cardNumber }) { index, sub ->
            val progress = if (index < cardAnimations.size) cardAnimations[index].value else 1f
            SubscriptionCard(
                subscription = sub,
                shape = if (index % 2 == 0) SubCardShapeA else SubCardShapeB,
                onClick = { onSubscriptionClick(index) },
                modifier = Modifier.graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * 60f
                }
            )
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    val iconTint = MaterialTheme.colorScheme.primary

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
            // Icon with tinted background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconBg, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ConfirmationNumber, null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.title.ifBlank { "Subscription" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (subscription.cardNumber.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CreditCard, null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            subscription.cardNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (subscription.startValidity.isNotBlank() || subscription.endValidity.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CalendarMonth, null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = buildString {
                                if (subscription.startValidity.isNotBlank()) append(formatDate(subscription.startValidity))
                                if (subscription.endValidity.isNotBlank()) append(" — ${formatDate(subscription.endValidity)}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
private fun EmptyState() {
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
                imageVector = Icons.Rounded.ConfirmationNumber,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Text(
                text = "No subscriptions found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Pull down to refresh",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ShimmerSubscriptionList() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        start = Offset(shimmerOffset * 1000f, 0f),
        end = Offset(shimmerOffset * 1000f + 500f, 0f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(4) { index ->
            val shape = if (index % 2 == 0) SubCardShapeA else SubCardShapeB
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(shape)
                    .background(brush)
            )
        }
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        val out = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        fmt.parse(dateStr)?.let { out.format(it) } ?: dateStr
    } catch (_: Exception) {
        try {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val out = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            fmt.parse(dateStr)?.let { out.format(it) } ?: dateStr
        } catch (_: Exception) { dateStr }
    }
}
