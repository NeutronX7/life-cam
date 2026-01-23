package com.isaalutions.life_cam.utils

data class SignalDataModel(
    val type: SignalDataModelTypes?=null,
    val data:String?=null
)

enum class SignalDataModelTypes {
    OFFER,
    ANSWER,
    ICE,
    CHAT
}
