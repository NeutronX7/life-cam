package com.isaalutions.life_cam.remote

data class StatusDataModel(
    val participant: String? = null,
    val type: StatusDataModelTypes? = null
)

enum class StatusDataModelTypes {
    IDLE,
    LOOKING_FOR_MATCH,
    OFFERED_MATCH,
    RECEIVED_MATCH,
    CONNECTED
}