package com.isaalutions.life_cam.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaalutions.life_cam.remote.FirebaseClient
import com.isaalutions.life_cam.utils.MatchState
import com.isaalutions.life_cam.webrtc.RTCAudioManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val firebaseClient: FirebaseClient,
    private val application: Application
) : ViewModel() {
    var matchState: MutableStateFlow<MatchState> = MutableStateFlow(MatchState.NewState)
    private val rtcAudioManager by lazy {
        RTCAudioManager.create(application)
    }

    init {
        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
    }

    fun permissionsGranted() {
        firebaseClient.observerUserStatus { status ->
            matchState.value = status
            when(status) {
                is MatchState.LookingForMatchState -> handleLookingForMatch()
                is MatchState.OfferedMatchState -> handleSentOffer()
                is MatchState.ReceivedMatchState -> handleIncomingMatchCase()
                else -> Unit
            }
        }
        firebaseClient.observeIncomingSignals { signalDataModel ->

        }
    }

    private fun handleLookingForMatch() {
        viewModelScope.launch {
            firebaseClient.findNextMatch()
        }
    }

    private fun handleSentOffer() {

    }

    private fun handleIncomingMatchCase() {

    }
}