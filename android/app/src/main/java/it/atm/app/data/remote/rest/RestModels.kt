package it.atm.app.data.remote.rest

import com.google.gson.annotations.SerializedName

data class ChecksResponse(
    @SerializedName("userVixRequired") val userVixRequired: Boolean = false,
    @SerializedName("aepTicketsMigrationsCarriers") val aepTicketsMigrationsCarriers: List<String>?,
    @SerializedName("allowTicketsMigrations") val allowTicketsMigrations: Boolean = false
)

data class UserCardsResponse(
    @SerializedName("UserCards") val userCards: List<UserCard>?
)

data class UserCard(
    @SerializedName("CardsItems") val cardsItems: List<CardItem>?
)

data class CardItem(
    @SerializedName("CardCode") val cardCode: String = "",
    @SerializedName("CardNumber") val cardNumber: String = "",
    @SerializedName("SerialNumber") val serialNumber: String = "",
    @SerializedName("HolderId") val holderId: String = "",
    @SerializedName("Name") val name: String = "",
    @SerializedName("Surname") val surname: String = "",
    @SerializedName("ProfileType") val profileType: String = "",
    @SerializedName("StartValidityDate") val startValidityDate: String? = null,
    @SerializedName("ExpiredDate") val expiredDate: String? = null,
    @SerializedName("CarrierCode") val carrierCode: String = "",
    @SerializedName("Valid") val valid: Boolean = false,
    @SerializedName("LastRenewalObj") val lastRenewalObj: RenewalObj? = null
)

data class RenewalObj(
    @SerializedName("ServiceTypeDescription") val serviceTypeDescription: String? = null
)

data class Subscription(
    @SerializedName("card_code") val cardCode: String = "",
    @SerializedName("card_number") val cardNumber: String = "",
    @SerializedName("serial_number") val serialNumber: String = "",
    @SerializedName("holder_id") val holderId: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("subtitle") val subtitle: String = "",
    @SerializedName("profile") val profile: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("start_validity") val startValidity: String = "",
    @SerializedName("end_validity") val endValidity: String = "",
    @SerializedName("carrier_code") val carrierCode: String = "",
    @SerializedName("status") val status: Int = 0,
    @SerializedName("cached_data_out_bin") val cachedDataOutBin: String? = null,
    @SerializedName("vtoken_uid") val vtokenUid: String = "",
    @SerializedName("signature_count") val signatureCount: Int = 1
)

// ── Ticketing models ────────────────────────────────────────────────────

data class TicketsResponse(
    @SerializedName("ticketsTPL") val ticketsTPL: List<Ticket>? = null,
    @SerializedName("integratedTicketsTPL") val integratedTicketsTPL: List<Ticket>? = null,
    @SerializedName("subscriptions") val subscriptions: List<Ticket>? = null,
    @SerializedName("ticketsTI") val ticketsTI: List<Ticket>? = null,
    @SerializedName("ticketsItabus") val ticketsItabus: List<Ticket>? = null,
    @SerializedName("ticketsGT") val ticketsGT: List<Ticket>? = null,
    @SerializedName("ticketsItalo") val ticketsItalo: List<Ticket>? = null,
)

data class Ticket(
    @SerializedName("ticketId") val ticketId: String = "",
    @SerializedName("ticketNumber") val ticketNumber: String = "",
    @SerializedName("carrierCode") val carrierCode: String = "",
    @SerializedName("purchaseDateTime") val purchaseDateTime: String? = null,
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("showAmount") val showAmount: Boolean = true,
    @SerializedName("description") val description: String = "",
    @SerializedName("route") val route: String? = null,
    @SerializedName("validationMode") val validationMode: String? = null,
    @SerializedName("validationType") val validationType: String? = null,
    @SerializedName("tripsNumber") val tripsNumber: Int = 0,
    @SerializedName("isSingleTrip") val isSingleTrip: Boolean = false,
    @SerializedName("hasNFCValidationAllowed") val hasNFCValidationAllowed: Boolean = false,
    @SerializedName("status") val status: String = "",
    @SerializedName("waterMark") val waterMark: String? = null,
    @SerializedName("validity") val validity: String? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("noteText") val noteText: String? = null,
    @SerializedName("additionalInfo") val additionalInfo: String? = null,
    @SerializedName("qrCodeSettings") val qrCodeSettings: TicketQrCodeSettings? = null,
    @SerializedName("validations") val validations: List<TicketValidation>? = null,
    @SerializedName("startDatetime") val startDatetime: String? = null,
    @SerializedName("expiredDatetime") val expiredDatetime: String? = null,
    @SerializedName("minimumAllowedObliterationDate") val minimumAllowedObliterationDate: String? = null,
    @SerializedName("maximumAllowedObliterationDate") val maximumAllowedObliterationDate: String? = null,
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

enum class TicketStatus { PURCHASED, VALIDATED, EXPIRED, UNKNOWN }

data class TicketQrCodeSettings(
    @SerializedName("generationMode") val generationMode: String? = null,
    @SerializedName("hasAutoRefresh") val hasAutoRefresh: Boolean = false,
    @SerializedName("refreshInterval") val refreshInterval: Long? = null,
)

data class TicketValidation(
    @SerializedName("validationId") val validationId: String? = null,
    @SerializedName("validityStart") val validityStart: String? = null,
    @SerializedName("validityEnd") val validityEnd: String? = null,
    @SerializedName("isActive") val isActive: Boolean? = null,
    @SerializedName("line") val line: String? = null,
    @SerializedName("direction") val direction: String? = null,
)

data class TicketQrCodeResponse(
    @SerializedName("ticketId") val ticketId: String = "",
    @SerializedName("qrCodeType") val qrCodeType: String = "",
    @SerializedName("qrCodeValue") val qrCodeValue: String = "",
    @SerializedName("qrCodeDescription") val qrCodeDescription: String? = null,
    @SerializedName("qrCodeExpirationDateTime") val qrCodeExpirationDateTime: String? = null,
    @SerializedName("qrCodeGenerationDateTime") val qrCodeGenerationDateTime: String? = null,
    @SerializedName("qrCodeZoomAllowed") val qrCodeZoomAllowed: Boolean = false,
    @SerializedName("qrCodeInfoValidationMessage") val qrCodeInfoValidationMessage: String? = null,
)
