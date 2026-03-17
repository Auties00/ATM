package it.atm.app.data.local

import it.atm.app.auth.AccountManager
import it.atm.app.domain.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileDataStore @Inject constructor(
    private val accountManager: AccountManager
) {
    suspend fun saveProfile(profile: UserProfile) {
        accountManager.updateActiveAccount {
            it.copy(
                name = profile.name,
                surname = profile.surname,
                email = profile.email,
                confirmedEmail = profile.confirmedEmail,
                phone = profile.phone,
                phonePrefix = profile.phonePrefix,
                birthDate = profile.birthDate,
                imagePath = profile.imagePath
            )
        }
    }

    fun getCachedProfile(): UserProfile {
        val account = accountManager.getActiveAccount() ?: return UserProfile()
        return UserProfile(
            name = account.name,
            surname = account.surname,
            email = account.email,
            confirmedEmail = account.confirmedEmail,
            phone = account.phone,
            phonePrefix = account.phonePrefix,
            birthDate = account.birthDate,
            imagePath = account.imagePath
        )
    }
}
