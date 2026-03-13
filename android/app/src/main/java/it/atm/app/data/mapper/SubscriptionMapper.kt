package it.atm.app.data.mapper

import it.atm.app.data.local.db.SubscriptionEntity
import it.atm.app.data.remote.rest.CardItem

object SubscriptionMapper {
    fun cardsToSubscriptions(cards: List<CardItem>, accountId: String): List<SubscriptionEntity> {
        return cards.filter { it.valid }.map { card ->
            val name = listOf(card.name, card.surname)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            val title = card.lastRenewalObj?.serviceTypeDescription
                ?: card.profileType.ifBlank { "Subscription" }
            val startValidity = (card.startValidityDate ?: "").take(10)
            val endValidity = (card.expiredDate ?: "").take(10)

            SubscriptionEntity(
                accountId = accountId,
                cardCode = card.cardCode,
                cardNumber = card.cardNumber,
                serialNumber = card.serialNumber,
                holderId = card.holderId,
                title = title,
                subtitle = card.cardNumber,
                profile = card.profileType,
                name = name,
                startValidity = startValidity,
                endValidity = endValidity,
                carrierCode = card.carrierCode,
                status = if (card.valid) 0 else 1,
                cachedDataOutBin = null,
                vtokenUid = "",
                signatureCount = 1
            )
        }
    }
}
