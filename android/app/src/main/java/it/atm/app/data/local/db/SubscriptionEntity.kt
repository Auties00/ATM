package it.atm.app.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscriptions",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("accountId")]
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val cardCode: String = "",
    val cardNumber: String = "",
    val serialNumber: String = "",
    val holderId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val profile: String = "",
    val name: String = "",
    val startValidity: String = "",
    val endValidity: String = "",
    val carrierCode: String = "",
    val status: Int = 0,
    val cachedDataOutBin: String? = null,
    val vtokenUid: String = "",
    val signatureCount: Int = 1
)
