package it.atm.app.domain.model

data class UserProfile(
    val name: String = "",
    val surname: String = "",
    val email: String = "",
    val confirmedEmail: Boolean = false,
    val phone: String = "",
    val phonePrefix: String = "",
    val birthDate: String? = null,
    val imagePath: String? = null,
) {
    val displayName: String get() = "$name $surname".trim()
    val initials: String get() = buildString {
        if (name.isNotBlank()) append(name.first().uppercase())
        if (surname.isNotBlank()) append(surname.first().uppercase())
    }.ifEmpty { "?" }
    val profileImageUrl: String? get() = imagePath?.let { path ->
        when {
            path.startsWith("http") -> path
            path.startsWith("/") -> "https://be.atm.it$path"
            else -> "https://be.atm.it/$path"
        }
    }
}
