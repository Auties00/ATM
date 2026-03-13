package it.atm.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val surname: String = "",
    val email: String = "",
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresAt: Long = 0L,
    val deviceUid: String = "",
    val onboardingComplete: Boolean = false,
    val lastSync: String? = null,
    val qrSigType: Int = 0,
    val qrInitialKeyId: Int = 0,
    val qrCodeFormat: Int = 1,
    val qrSignatureKeysVTID: Int = 0,
    val activeNfcSubscriptionIndex: Int = -1
) {
    val displayName: String get() = "$name $surname".trim()
    val initials: String get() = buildString {
        if (name.isNotBlank()) append(name.first().uppercase())
        if (surname.isNotBlank()) append(surname.first().uppercase())
    }.ifEmpty { email.firstOrNull()?.uppercase() ?: "?" }
}
