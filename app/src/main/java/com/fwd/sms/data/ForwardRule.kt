package com.fwd.sms.data

import java.util.UUID

enum class DestinationType {
    SMS, EMAIL, TELEGRAM, WEBHOOK
}

data class Destination(
    val type: DestinationType,
    val value: String,
    val label: String = ""
)

data class MessageFilter(
    val senderContains: String = "",
    val bodyContains: String = "",
    val senderExclude: String = "",
    val bodyExclude: String = ""
)

data class ForwardRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val source: SourceType = SourceType.ALL,
    val destinations: List<Destination> = emptyList(),
    val filter: MessageFilter = MessageFilter(),
    val simSlot: Int = -1
)

enum class SourceType {
    SMS, NOTIFICATION, ALL
}
