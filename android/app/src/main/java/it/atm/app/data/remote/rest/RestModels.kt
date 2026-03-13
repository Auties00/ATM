package it.atm.app.data.remote.rest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChecksResponse(
    @SerialName("userVixRequired") val userVixRequired: Boolean = false,
    @SerialName("aepTicketsMigrationsCarriers") val aepTicketsMigrationsCarriers: List<String>? = null,
    @SerialName("allowTicketsMigrations") val allowTicketsMigrations: Boolean = false
)

@Serializable
data class UserCardsResponse(
    @SerialName("UserCards") val userCards: List<UserCard>? = null
)

@Serializable
data class UserCard(
    @SerialName("CardsItems") val cardsItems: List<CardItem>? = null
)

@Serializable
data class CardItem(
    @SerialName("CardCode") val cardCode: String = "",
    @SerialName("CardNumber") val cardNumber: String = "",
    @SerialName("SerialNumber") val serialNumber: String = "",
    @SerialName("HolderId") val holderId: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Surname") val surname: String = "",
    @SerialName("ProfileType") val profileType: String = "",
    @SerialName("StartValidityDate") val startValidityDate: String? = null,
    @SerialName("ExpiredDate") val expiredDate: String? = null,
    @SerialName("CarrierCode") val carrierCode: String = "",
    @SerialName("Valid") val valid: Boolean = false,
    @SerialName("LastRenewalObj") val lastRenewalObj: RenewalObj? = null
)

@Serializable
data class RenewalObj(
    @SerialName("ServiceTypeDescription") val serviceTypeDescription: String? = null
)

@Serializable
data class TicketsResponse(
    @SerialName("ticketsTPL") val ticketsTPL: List<Ticket>? = null,
    @SerialName("integratedTicketsTPL") val integratedTicketsTPL: List<Ticket>? = null,
    @SerialName("subscriptions") val subscriptions: List<Ticket>? = null,
    @SerialName("ticketsTI") val ticketsTI: List<Ticket>? = null,
    @SerialName("ticketsItabus") val ticketsItabus: List<Ticket>? = null,
    @SerialName("ticketsGT") val ticketsGT: List<Ticket>? = null,
    @SerialName("ticketsItalo") val ticketsItalo: List<Ticket>? = null,
)

@Serializable
data class Ticket(
    @SerialName("ticketId") val ticketId: String = "",
    @SerialName("ticketNumber") val ticketNumber: String = "",
    @SerialName("carrierCode") val carrierCode: String = "",
    @SerialName("purchaseDateTime") val purchaseDateTime: String? = null,
    @SerialName("amount") val amount: Double = 0.0,
    @SerialName("showAmount") val showAmount: Boolean = true,
    @SerialName("description") val description: String = "",
    @SerialName("route") val route: String? = null,
    @SerialName("validationMode") val validationMode: String? = null,
    @SerialName("validationType") val validationType: String? = null,
    @SerialName("tripsNumber") val tripsNumber: Int = 0,
    @SerialName("isSingleTrip") val isSingleTrip: Boolean = false,
    @SerialName("hasNFCValidationAllowed") val hasNFCValidationAllowed: Boolean = false,
    @SerialName("status") val status: String = "",
    @SerialName("waterMark") val waterMark: String? = null,
    @SerialName("validity") val validity: String? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("noteText") val noteText: String? = null,
    @SerialName("additionalInfo") val additionalInfo: String? = null,
    @SerialName("qrCodeSettings") val qrCodeSettings: TicketQrCodeSettings? = null,
    @SerialName("validations") val validations: List<TicketValidation>? = null,
    @SerialName("startDatetime") val startDatetime: String? = null,
    @SerialName("expiredDatetime") val expiredDatetime: String? = null,
    @SerialName("minimumAllowedObliterationDate") val minimumAllowedObliterationDate: String? = null,
    @SerialName("maximumAllowedObliterationDate") val maximumAllowedObliterationDate: String? = null,
) {
    val displayStatus: TicketStatus
        get() = when (status.uppercase()) {
            "VALIDATED" -> TicketStatus.VALIDATED
            "EXPIRED", "EXPIRED_NUMBER" -> TicketStatus.EXPIRED
            "PURCHASED" -> TicketStatus.PURCHASED
            else -> TicketStatus.UNKNOWN
        }

    val hasQrCode: Boolean
        get() = qrCodeSettings?.generationMode?.equals("QRCODE_SERVER", ignoreCase = true) == true

    val activeValidationId: String?
        get() = validations?.firstOrNull { it.isActive == true }?.validationId
}

@Serializable
enum class TicketStatus { PURCHASED, VALIDATED, EXPIRED, UNKNOWN }

@Serializable
data class TicketQrCodeSettings(
    @SerialName("generationMode") val generationMode: String? = null,
    @SerialName("hasAutoRefresh") val hasAutoRefresh: Boolean = false,
    @SerialName("refreshInterval") val refreshInterval: Long? = null,
)

@Serializable
data class TicketValidation(
    @SerialName("validationId") val validationId: String? = null,
    @SerialName("validityStart") val validityStart: String? = null,
    @SerialName("validityEnd") val validityEnd: String? = null,
    @SerialName("isActive") val isActive: Boolean? = null,
    @SerialName("line") val line: String? = null,
    @SerialName("direction") val direction: String? = null,
)

@Serializable
data class TicketQrCodeResponse(
    @SerialName("ticketId") val ticketId: String = "",
    @SerialName("qrCodeType") val qrCodeType: String = "",
    @SerialName("qrCodeValue") val qrCodeValue: String = "",
    @SerialName("qrCodeDescription") val qrCodeDescription: String? = null,
    @SerialName("qrCodeExpirationDateTime") val qrCodeExpirationDateTime: String? = null,
    @SerialName("qrCodeGenerationDateTime") val qrCodeGenerationDateTime: String? = null,
    @SerialName("qrCodeZoomAllowed") val qrCodeZoomAllowed: Boolean = false,
    @SerialName("qrCodeInfoValidationMessage") val qrCodeInfoValidationMessage: String? = null,
)

@Serializable
data class AccountProfileResponse(
    @SerialName("name") val name: String = "",
    @SerialName("surname") val surname: String = "",
    @SerialName("email") val email: String = "",
    @SerialName("confirmedEmail") val confirmedEmail: Boolean = false,
    @SerialName("phone") val phone: String = "",
    @SerialName("phonePrefix") val phonePrefix: String = "",
    @SerialName("birthDate") val birthDate: String? = null,
    @SerialName("imagePath") val imagePath: String? = null,
)

@Serializable
data class MigrationResponse(
    @SerialName("success") val success: Boolean? = null,
    @SerialName("errorCode") val errorCode: String? = null,
    @SerialName("errorMessage") val errorMessage: String? = null,
    @SerialName("message") val message: String? = null,
)
