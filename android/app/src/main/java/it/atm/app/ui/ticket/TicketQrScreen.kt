package it.atm.app.ui.ticket

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.atm.app.ui.components.rememberShimmerBrush
import kotlinx.coroutines.launch

@Composable
fun TicketQrScreen(
    viewModel: TicketQrViewModel,
    onDismiss: () -> Unit
) {
    val qrBitmap by viewModel.qrBitmap.collectAsState()
    val title by viewModel.title.collectAsState()
    val subtitle by viewModel.subtitle.collectAsState()
    val statusLabel by viewModel.statusLabel.collectAsState()
    val statusColor by viewModel.statusColor.collectAsState()
    val qrMessage by viewModel.qrMessage.collectAsState()
    val isLoadingQr by viewModel.isLoadingQr.collectAsState()
    val errorMessage by viewModel.error.collectAsState()
    val canValidate by viewModel.canValidate.collectAsState()
    val isValidating by viewModel.isValidating.collectAsState()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f
        window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window?.attributes?.apply { screenBrightness = originalBrightness }
        }
    }

    val headerAlpha = remember { Animatable(0f) }
    val qrAlpha = remember { Animatable(0f) }
    val buttonsAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { headerAlpha.animateTo(1f, tween(400)) }
        launch {
            kotlinx.coroutines.delay(150L)
            qrAlpha.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f))
        }
        launch {
            kotlinx.coroutines.delay(300L)
            buttonsAlpha.animateTo(1f, tween(300))
        }
    }

    val shimmerBrush = rememberShimmerBrush()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = headerAlpha.value; translationY = (1f - headerAlpha.value) * 20f }
            ) {
                Icon(Icons.Rounded.DirectionsBus, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(24.dp))
                Text(title.ifBlank { "Ticket" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                if (statusLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.size(10.dp).background(statusColor, RoundedCornerShape(50)))
                        Spacer(Modifier.width(8.dp))
                        Text(statusLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = statusColor)
                    }
                }
                if (!qrMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(qrMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .graphicsLayer { alpha = qrAlpha.value; scaleX = 0.9f + qrAlpha.value * 0.1f; scaleY = 0.9f + qrAlpha.value * 0.1f }
                    .clip(MaterialTheme.shapes.large).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = qrBitmap
                val error = errorMessage
                when {
                    bitmap != null -> Image(bitmap.asImageBitmap(), "QR Code", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    isLoadingQr -> Box(Modifier.fillMaxSize().background(shimmerBrush), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.secondary)
                    }
                    error != null -> Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.DirectionsBus, null, Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Text("No QR code available for this ticket", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = buttonsAlpha.value; translationY = (1f - buttonsAlpha.value) * 20f },
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (canValidate) {
                    Button(onClick = { viewModel.validate() }, enabled = !isValidating, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) {
                        if (isValidating) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Validate Ticket") }
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) { Text("Close") }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
