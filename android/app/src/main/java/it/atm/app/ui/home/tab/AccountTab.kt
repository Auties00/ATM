package it.atm.app.ui.home.tab

import it.atm.app.auth.AccountManager
import it.atm.app.domain.model.UserProfile
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import it.atm.app.data.local.db.AccountEntity
import it.atm.app.ui.components.CardShapeA
import it.atm.app.ui.components.CardShapeB
import it.atm.app.ui.components.CardShapeC
import it.atm.app.ui.components.rememberShimmerBrush
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BlobAvatarShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.50f, h * 0.02f)
    cubicTo(w * 0.82f, h * 0.00f, w * 0.98f, h * 0.22f, w * 0.96f, h * 0.45f)
    cubicTo(w * 0.94f, h * 0.70f, w * 0.98f, h * 0.86f, w * 0.72f, h * 0.96f)
    cubicTo(w * 0.46f, h * 1.04f, w * 0.16f, h * 0.90f, w * 0.06f, h * 0.64f)
    cubicTo(-w * 0.02f, h * 0.40f, w * 0.04f, h * 0.12f, w * 0.26f, h * 0.04f)
    cubicTo(w * 0.38f, h * 0.00f, w * 0.44f, h * 0.03f, w * 0.50f, h * 0.02f)
    close()
}

private val IconBgShape = RoundedCornerShape(12.dp)
private val AvatarShapeSmall = RoundedCornerShape(16.dp)

private enum class EditableField {
    FirstName, LastName, DateOfBirth,
    Password, Phone
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountTab(
    profile: UserProfile,
    isSyncing: Boolean,
    isUpdating: Boolean,
    accounts: List<AccountEntity>,
    activeAccountId: String?,
    isOffline: Boolean = false,
    onUpdateProfile: (UserProfile) -> Unit,
    onLogout: (accountId: String) -> Unit,
    onSwitchAccount: (accountId: String) -> Unit,
    onAddAccount: () -> Unit,
    onShowAccountSwitcher: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingField by remember { mutableStateOf<EditableField?>(null) }
    var pullRefreshing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(pullRefreshing) {
        if (pullRefreshing) {
            delay(300)
            pullRefreshing = false
        }
    }

    val avatarScale = remember { Animatable(0f) }
    val nameAlpha = remember { Animatable(0f) }
    val card1Progress = remember { Animatable(0f) }
    val card2Progress = remember { Animatable(0f) }
    val card3Progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            avatarScale.animateTo(
                1f, spring(dampingRatio = 0.45f, stiffness = 250f)
            )
        }
        launch {
            delay(200)
            nameAlpha.animateTo(1f, spring(stiffness = 200f))
        }
        launch {
            delay(350)
            card1Progress.animateTo(
                1f, spring(dampingRatio = 0.65f, stiffness = 280f)
            )
        }
        launch {
            delay(500)
            card2Progress.animateTo(
                1f, spring(dampingRatio = 0.65f, stiffness = 280f)
            )
        }
        launch {
            delay(650)
            card3Progress.animateTo(
                1f, spring(dampingRatio = 0.65f, stiffness = 280f)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "avatar")
    val avatarRotationRaw by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(12000, easing = LinearEasing), RepeatMode.Restart
        ), label = "rotation"
    )
    val avatarRotation = kotlin.math.sin(avatarRotationRaw * 2f * Math.PI.toFloat()) * 15f
    val avatarPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "pulse"
    )

    val shimmerBrush = rememberShimmerBrush()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
        )
        if (isSyncing || isUpdating) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Round
            )
        }

        PullToRefreshBox(
            isRefreshing = pullRefreshing,
            onRefresh = { if (!isOffline) { pullRefreshing = true; onRefresh() } },
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowAccountSwitcher() }
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = avatarScale.value * avatarPulse
                        scaleY = avatarScale.value * avatarPulse
                        rotationZ = avatarRotation
                    },
                contentAlignment = Alignment.Center
            ) {
                ProfileAvatar(
                    imageUrl = profile.profileImageUrl,
                    initials = profile.initials,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = profile.displayName.ifBlank { "ATM User" },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    alpha = nameAlpha.value
                    translationY = (1f - nameAlpha.value) * 30f
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (profile.email.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = nameAlpha.value
                            translationY = (1f - nameAlpha.value) * 20f
                        }
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = profile.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = nameAlpha.value }
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.ChevronRight, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Switch account",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isSyncing && profile.name.isBlank() && profile.email.isBlank()) {
            ShimmerCard(shape = CardShapeA, height = 260.dp, brush = shimmerBrush)
            Spacer(modifier = Modifier.height(16.dp))
            ShimmerCard(shape = CardShapeB, height = 140.dp, brush = shimmerBrush)
            Spacer(modifier = Modifier.height(16.dp))
            ShimmerCard(shape = CardShapeC, height = 190.dp, brush = shimmerBrush)
        } else {
            InfoSection(
                title = "Personal info",
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                iconTint = MaterialTheme.colorScheme.primary,
                iconBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = CardShapeA,
                items = listOf(
                    FieldRow(Icons.Outlined.Badge, "First name", profile.name.ifBlank { null }, EditableField.FirstName),
                    FieldRow(Icons.Outlined.Badge, "Last name", profile.surname.ifBlank { null }, EditableField.LastName),
                    FieldRow(Icons.Outlined.Cake, "Date of birth", profile.birthDate?.take(10), EditableField.DateOfBirth),
                ),
                onFieldTap = { editingField = it },
                modifier = Modifier.graphicsLayer {
                    alpha = card1Progress.value
                    translationY = (1f - card1Progress.value) * 80f
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            InfoSection(
                title = "Login info",
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                iconTint = MaterialTheme.colorScheme.primary,
                iconBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = CardShapeC,
                items = listOf(
                    FieldRow(Icons.Outlined.Email, "Email", profile.email.ifBlank { null }, editable = null),
                    FieldRow(Icons.Outlined.Key, "Password", "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022", EditableField.Password),
                    FieldRow(Icons.Outlined.Phone, "Phone", if (profile.phone.isNotBlank()) "${profile.phonePrefix} ${profile.phone}" else null, EditableField.Phone),
                ),
                onFieldTap = { editingField = it },
                modifier = Modifier.graphicsLayer {
                    alpha = card3Progress.value
                    translationY = (1f - card3Progress.value) * 80f
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        }
        }
    }

    editingField?.let { field ->
        when (field) {
            EditableField.FirstName -> EditTextDialog(
                title = "First name",
                currentValue = profile.name,
                onDismiss = { editingField = null },
                onSave = { editingField = null; onUpdateProfile(profile.copy(name = it)) }
            )
            EditableField.LastName -> EditTextDialog(
                title = "Last name",
                currentValue = profile.surname,
                onDismiss = { editingField = null },
                onSave = { editingField = null; onUpdateProfile(profile.copy(surname = it)) }
            )
            EditableField.DateOfBirth -> EditDateDialog(
                currentValue = profile.birthDate,
                onDismiss = { editingField = null },
                onSave = { editingField = null; onUpdateProfile(profile.copy(birthDate = it)) }
            )
            EditableField.Password -> {
                editingField = null
                CustomTabsIntent.Builder().build()
                    .launchUrl(context, Uri.parse("https://areariservata.atm.it/resetpassword"))
            }
            EditableField.Phone -> EditPhoneDialog(
                currentPhone = profile.phone,
                currentPrefix = profile.phonePrefix.ifBlank { "+39" },
                onDismiss = { editingField = null },
                onSave = { phone, prefix ->
                    editingField = null
                    onUpdateProfile(profile.copy(phone = phone, phonePrefix = prefix))
                }
            )
        }
    }

}

private data class FieldRow(
    val icon: ImageVector,
    val label: String,
    val value: String?,
    val editable: EditableField?
)

@Composable
private fun InfoSection(
    title: String,
    containerColor: Color,
    iconTint: Color,
    iconBg: Color,
    shape: Shape,
    items: List<FieldRow>,
    onFieldTap: (EditableField) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 4.dp)
            )
            items.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (row.editable != null)
                                Modifier.clickable { onFieldTap(row.editable) }
                            else Modifier
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(iconBg, IconBgShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = row.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = iconTint
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = row.value ?: "Not set",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (row.value != null) FontWeight.Medium else FontWeight.Normal,
                            color = if (row.value != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (row.editable != null) {
                        Icon(
                            Icons.Outlined.ChevronRight, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp, end = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun EditTextDialog(
    title: String,
    currentValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember {
        mutableStateOf(TextFieldValue(currentValue, TextRange(currentValue.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                shape = MaterialTheme.shapes.medium
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onSave(value.text) },
                shape = MaterialTheme.shapes.large
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDateDialog(
    currentValue: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    val initialMillis = currentValue?.take(10)?.let {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(it)?.time
        } catch (_: Exception) { null }
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        onSave(sdf.format(Date(millis)))
                    } else {
                        onSave(null)
                    }
                },
                shape = MaterialTheme.shapes.large
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun EditPhoneDialog(
    currentPhone: String,
    currentPrefix: String,
    onDismiss: () -> Unit,
    onSave: (phone: String, prefix: String) -> Unit
) {
    var phone by remember {
        mutableStateOf(TextFieldValue(currentPhone, TextRange(currentPhone.length)))
    }
    var prefix by remember { mutableStateOf(currentPrefix) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Phone number", fontWeight = FontWeight.Bold) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = prefix, onValueChange = { prefix = it },
                    label = { Text("Prefix") }, singleLine = true,
                    modifier = Modifier.width(90.dp), shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Number") }, singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    shape = MaterialTheme.shapes.medium
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onSave(phone.text, prefix) },
                shape = MaterialTheme.shapes.large
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ShimmerCard(
    shape: Shape,
    height: Dp,
    brush: Brush
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(brush)
    )
}

@Composable
private fun ProfileAvatar(
    imageUrl: String?,
    initials: String,
    modifier: Modifier = Modifier
) {
    if (imageUrl != null) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = "Profile picture",
            modifier = modifier.clip(BlobAvatarShape),
            contentScale = ContentScale.Crop,
            loading = { InitialsAvatar(initials, Modifier.fillMaxSize()) },
            error = { InitialsAvatar(initials, Modifier.fillMaxSize()) }
        )
    } else {
        InitialsAvatar(initials, modifier)
    }
}

@Composable
private fun InitialsAvatar(initials: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(BlobAvatarShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
fun AccountSwitcherPage(
    accounts: List<AccountEntity>,
    activeAccountId: String?,
    isOffline: Boolean = false,
    onDismiss: () -> Unit,
    onLogout: (accountId: String) -> Unit,
    onSwitchAccount: (accountId: String) -> Unit,
    onAddAccount: () -> Unit
) {
    val cardAnimations = remember(accounts.size) {
        List(accounts.size) { Animatable(0f) }
    }
    val headerAlpha = remember { Animatable(0f) }
    val buttonsAlpha = remember { Animatable(0f) }

    LaunchedEffect(accounts.size) {
        launch { headerAlpha.animateTo(1f, tween(400)) }
        cardAnimations.forEachIndexed { index, anim ->
            launch {
                delay(150L + index * 80L)
                anim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f))
            }
        }
        launch {
            delay(150L + accounts.size * 80L + 100L)
            buttonsAlpha.animateTo(1f, tween(300))
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = headerAlpha.value
                    translationY = (1f - headerAlpha.value) * 20f
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.SwitchAccount,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Accounts",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Swipe an account to remove it",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val shimmerBrush = rememberShimmerBrush()
                accounts.forEachIndexed { index, account ->
                    val progress = if (index < cardAnimations.size) cardAnimations[index].value else 1f
                    val isActive = account.id == activeAccountId
                    Box(modifier = Modifier.graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * 40f
                    }) {
                        if (account.id == AccountManager.PENDING_ACCOUNT_ID) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(88.dp)
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .background(shimmerBrush)
                            )
                        } else {
                            SwipeableAccountCard(
                                account = account,
                                isActive = isActive,
                                onClick = { if (!isActive) onSwitchAccount(account.id) },
                                onSwipeDismiss = { onLogout(account.id) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = buttonsAlpha.value
                        translationY = (1f - buttonsAlpha.value) * 20f
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = onAddAccount,
                    enabled = !isOffline,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Outlined.PersonAdd, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add account")
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SwipeableAccountCard(
    account: AccountEntity,
    isActive: Boolean,
    onClick: () -> Unit,
    onSwipeDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    val dragProgress = (abs(dragOffset.value) / thresholdPx).coerceIn(0f, 1f)
    val offsetDp = with(LocalDensity.current) { dragOffset.value.toDp() }
    val isDraggingRight = dragOffset.value > 0
    val absDp = with(LocalDensity.current) { abs(dragOffset.value).toDp() }
    val gap = 8.dp

    val iconScale by animateFloatAsState(
        targetValue = if (dragProgress > 0.3f) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        if (dragOffset.value != 0f) {
            val actionWidth = (absDp - gap).coerceAtLeast(0.dp)
            if (actionWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(if (isDraggingRight) Alignment.CenterStart else Alignment.CenterEnd)
                        .width(actionWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Delete, "Remove",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp).scale(iconScale).alpha(dragProgress)
                    )
                }
            }
        }

        ElevatedCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = offsetDp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val currentOffset = dragOffset.value
                            val pastThreshold = abs(currentOffset) >= thresholdPx
                            coroutineScope.launch {
                                if (pastThreshold) {
                                    val target = if (currentOffset > 0) thresholdPx * 3f else -thresholdPx * 3f
                                    dragOffset.animateTo(target, spring(stiffness = Spring.StiffnessLow))
                                    onSwipeDismiss()
                                } else {
                                    dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                            }
                        },
                        onHorizontalDrag = { _, delta ->
                            coroutineScope.launch { dragOffset.snapTo(dragOffset.value + delta) }
                        }
                    )
                },
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            AvatarShapeSmall
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = account.initials,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.displayName.ifBlank { "ATM User" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = account.email.ifBlank { "No email" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
