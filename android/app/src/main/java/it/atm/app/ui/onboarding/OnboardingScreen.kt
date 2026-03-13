package it.atm.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.atm.app.data.remote.rest.CardItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardShapeA = RoundedCornerShape(
    topStart = 28.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 28.dp
)
private val CardShapeB = RoundedCornerShape(
    topStart = 12.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 12.dp
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == ImportPhase.Done) {
            viewModel.markComplete()
            onComplete()
        }
    }

    val finish = {
        viewModel.markComplete()
        onComplete()
    }

    Box {
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(64.dp))

                // ─── Header icon ───
                AnimatedContent(
                    targetState = uiState.phase,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "header_icon"
                ) { phase ->
                    val icon = when (phase) {
                        ImportPhase.Empty -> Icons.Rounded.SearchOff
                        ImportPhase.Found, ImportPhase.Transferring -> Icons.Rounded.SyncAlt
                        ImportPhase.Done -> Icons.Rounded.CheckCircle
                        else -> Icons.Rounded.DevicesOther
                    }
                    val tint = when (phase) {
                        ImportPhase.Empty -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        ImportPhase.Searching -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ImportPhase.Done -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = tint
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Title ───
                AnimatedContent(
                    targetState = uiState.phase,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "title"
                ) { phase ->
                    val title = when (phase) {
                        ImportPhase.Idle -> "Import subscriptions"
                        ImportPhase.Searching -> "Looking for subscriptions..."
                        ImportPhase.Found -> "Subscriptions found"
                        ImportPhase.Empty -> "No subscriptions found"
                        ImportPhase.Transferring -> "Transferring..."
                        ImportPhase.Done -> "Transfer complete"
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ─── Subtitle ───
                AnimatedContent(
                    targetState = uiState.phase,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "subtitle"
                ) { phase ->
                    val subtitle = when (phase) {
                        ImportPhase.Idle -> "Would you like to import your transit subscriptions from another device?"
                        ImportPhase.Searching -> "Checking your other devices"
                        ImportPhase.Found -> "Transfer these subscriptions to this device?"
                        ImportPhase.Empty -> "There are no subscriptions available for import from your other devices."
                        ImportPhase.Transferring -> "Please wait while we transfer your subscriptions"
                        ImportPhase.Done -> ""
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ─── Cards area: shimmer → real cards ───
                AnimatedVisibility(
                    visible = uiState.phase == ImportPhase.Searching,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ShimmerCards()
                }

                AnimatedVisibility(
                    visible = uiState.phase in listOf(ImportPhase.Found, ImportPhase.Transferring) && uiState.cards.isNotEmpty(),
                    enter = expandVertically() + fadeIn(tween(400)),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SubscriptionPreviewList(cards = uiState.cards)
                }

                Spacer(modifier = Modifier.weight(1f))

                // ─── Buttons ───
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (uiState.phase) {
                        ImportPhase.Idle -> {
                            Button(
                                onClick = { viewModel.startSearch() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(Icons.Rounded.SyncAlt, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Yes, import subscriptions")
                            }
                            OutlinedButton(
                                onClick = finish,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = MaterialTheme.shapes.large
                            ) { Text("Skip for now") }
                        }
                        ImportPhase.Searching -> {
                            // No action buttons while searching
                        }
                        ImportPhase.Found -> {
                            Button(
                                onClick = { viewModel.confirmImport() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(Icons.Rounded.SyncAlt, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Transfer to this device")
                            }
                            OutlinedButton(
                                onClick = finish,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = MaterialTheme.shapes.large
                            ) { Text("Skip for now") }
                        }
                        ImportPhase.Transferring -> {
                            Button(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = false,
                                shape = MaterialTheme.shapes.large
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Transferring...")
                            }
                        }
                        ImportPhase.Empty -> {
                            Button(
                                onClick = finish,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = MaterialTheme.shapes.large
                            ) { Text("Continue") }
                        }
                        ImportPhase.Done -> {}
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
        )
    }
}

// ─── Shimmer placeholder cards ───

@Composable
private fun ShimmerCards() {
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) { index ->
            val shape = if (index % 2 == 0) CardShapeA else CardShapeB
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(shape)
                    .background(brush)
            )
        }
    }
}

// ─── Real subscription preview cards ───

@Composable
private fun SubscriptionPreviewList(cards: List<CardItem>) {
    val cardAnimations = remember(cards.size) {
        List(cards.size) { Animatable(0f) }
    }
    LaunchedEffect(cards.size) {
        cardAnimations.forEachIndexed { index, anim ->
            launch {
                delay(index * 80L)
                anim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f))
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.forEachIndexed { index, card ->
            val progress = if (index < cardAnimations.size) cardAnimations[index].value else 1f
            SubscriptionPreviewCard(
                card = card,
                shape = if (index % 2 == 0) CardShapeA else CardShapeB,
                modifier = Modifier.graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * 40f
                }
            )
        }
    }
}

@Composable
private fun SubscriptionPreviewCard(
    card: CardItem,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    val title = card.lastRenewalObj?.serviceTypeDescription
        ?: card.profileType.ifBlank { "Subscription" }
    val name = listOf(card.name, card.surname).filter { it.isNotBlank() }.joinToString(" ")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ConfirmationNumber, null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.cardNumber.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CreditCard, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(5.dp))
                        Text(card.cardNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (name.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
