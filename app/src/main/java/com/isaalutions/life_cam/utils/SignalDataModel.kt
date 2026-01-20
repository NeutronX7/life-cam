package com.isaalutions.life_cam.utils

data class SignalDataModel(
    val type: SingalDataModelTypes?=null,
    val data:String?=null
)

enum class SingalDataModelTypes {
    OFFER,
    ANSWER,
    ICE,
    CHAT
}
