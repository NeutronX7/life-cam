package com.isaalutions.life_cam.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.isaalutions.life_cam.remote.FirebaseClient
import com.isaalutions.life_cam.remote.StatusDataModel
import com.isaalutions.life_cam.remote.StatusDataModelTypes
import com.isaalutions.life_cam.utils.ChatItem
import com.isaalutions.life_cam.utils.MatchState
import com.isaalutions.life_cam.utils.SignalDataModel
import com.isaalutions.life_cam.utils.SignalDataModelTypes
import com.isaalutions.life_cam.webrtc.MyPeerObserver
import com.isaalutions.life_cam.webrtc.RTCAudioManager
import com.isaalutions.life_cam.webrtc.RTCClient
import com.isaalutions.life_cam.webrtc.RTCClientImpl
import com.isaalutions.life_cam.webrtc.WebRTCFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject
import kotlin.reflect.typeOf

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class MainViewModel @Inject constructor(
    private val firebaseClient: FirebaseClient,
    private val application: Application,
    private val webRTCFactory: WebRTCFactory,
    private val gson: Gson
) : ViewModel() {
    var matchState: MutableStateFlow<MatchState> = MutableStateFlow(MatchState.NewState)
    var chatList: MutableStateFlow<List<ChatItem>> = MutableStateFlow(mutableListOf())
    var participantId: String = "testParticipant"
    private var remoteSurface: SurfaceViewRenderer ?= null
    private var rtcClient: RTCClient?= null
    private fun addChatItem(item: ChatItem) {
        val currentList = chatList.value.toMutableList()
        currentList.add(item)
        chatList.value = currentList
    }
    private fun resetChatList() {
        chatList.value = mutableListOf()
    }
    fun sendChatItem(newChatItem: ChatItem) {
        addChatItem(newChatItem)
        viewModelScope.launch {
            firebaseClient.updateParticipantDataModel(
                participant = participantId,
                data = SignalDataModel(type = SignalDataModelTypes.CHAT, data = newChatItem.text)
            )
        }
    }
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
                is MatchState.OfferedMatchState -> handleSentOffer(status)
                is MatchState.ReceivedMatchState -> handleIncomingMatchCase(status)
                else -> Unit
            }
        }
        firebaseClient.observeIncomingSignals { signalDataModel ->
            when(signalDataModel.type) {
                SignalDataModelTypes.OFFER -> handleReceivedOfferSdp(signalDataModel)
                SignalDataModelTypes.ANSWER -> handleReceivedOfferSdp(signalDataModel)
                SignalDataModelTypes.ICE -> handleReceivedIceCandidate(signalDataModel)
                SignalDataModelTypes.CHAT -> handleReceivedChat(signalDataModel)
                else -> Unit
            }
        }

        findNextMatch()
    }

    private fun handleReceivedChat(signalDataModel: SignalDataModel) {
        val chatItem = ChatItem(
            text = signalDataModel.data.toString(),
            isMine = false
        )
        addChatItem(chatItem)
    }

    private fun handleReceivedIceCandidate(signalDataModel: SignalDataModel) {
        runCatching {
            gson.fromJson(signalDataModel.data.toString(), IceCandidate::class.java)
        }.onSuccess {
            rtcClient?.onIceCandidateReceived(it)
        }
    }

    private fun handleReceivedAnswerSdp(signalDataModel: SignalDataModel) {
        val sessionDescription = SessionDescription(
            SessionDescription.Type.ANSWER,
            signalDataModel.data!!
        )
        rtcClient?.onRemoteSessionReceived(sessionDescription)
    }

    private fun handleReceivedOfferSdp(signalDataModel: SignalDataModel) {
        setupRTTConnection(participantId)?.also {
            it.onRemoteSessionReceived(
                SessionDescription(
                    SessionDescription.Type.OFFER,
                    signalDataModel.data.toString()
                )
            )
            it.answer()
        }
    }

    private fun handleLookingForMatch() {
        viewModelScope.launch {
            firebaseClient.findNextMatch()
        }

        resetChatList()
        rtcClient?.onDestroy()
    }

    private fun handleSentOffer(status: MatchState.OfferedMatchState) {
        this.participantId = status.participant
    }

    private fun handleIncomingMatchCase(status: MatchState.ReceivedMatchState) {
        this.participantId = status.participant
        setupRTTConnection(participantId)?.also {
            it.offer()
        }
    }

    fun initRemoteSurfaceView(renderer: SurfaceViewRenderer) {
        webRTCFactory.initSurfaceView(renderer)
        this.remoteSurface = renderer
    }

    fun startLocalStream(renderer: SurfaceViewRenderer) {
        webRTCFactory.prepareLocalStream(renderer)
    }

    fun switchCamera() {
        webRTCFactory.switchCamera()
    }

    fun stopLookingForMatch() {
        viewModelScope.launch {
            resetChatList()
            if(matchState.value == MatchState.Connected) {
                firebaseClient.updateParticipantStatus(
                    participantId, StatusDataModel(
                        type = StatusDataModelTypes.LOOKING_FOR_MATCH
                    )
                )
                firebaseClient.updateSelfStatus(StatusDataModel(type = StatusDataModelTypes.IDLE))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        remoteSurface?.release()
        remoteSurface = null
        rtcClient?.onDestroy()
        webRTCFactory.onDestroy()
    }

    private fun setupRTTConnection(participantId: String): RTCClient? {
        runCatching { rtcClient?.onDestroy() }
        rtcClient = null
        rtcClient = webRTCFactory.createRTCClient(observer = object : MyPeerObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                p0?.let {
                    rtcClient?.onLocalIceCandidateGenerated(it)
                }
            }

            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                p0?.let {
                    runCatching {
                        remoteSurface?.let { surface ->
                            it.videoTracks[0].addSink(surface)
                        }
                    }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                super.onConnectionChange(newState)
                if(newState == PeerConnection.PeerConnectionState.CONNECTED) {
                    viewModelScope.launch {
                        firebaseClient.updateSelfStatus(StatusDataModel(type = StatusDataModelTypes.CONNECTED))
                        firebaseClient.removeSelfData()
                    }
                }
            }
        }, listener = object : RTCClientImpl.TransferDataToServerCallback {
            override fun onIceGenerated(iceCandidate: IceCandidate) {
                viewModelScope.launch {
                    firebaseClient.updateParticipantDataModel(
                            participant = participantId,
                            data = SignalDataModel(type = SignalDataModelTypes.ICE, data = gson.toJson(iceCandidate)
                        )
                    )
                }
            }

            override fun onAnswerGenerated(sessionDescription: SessionDescription) {
                viewModelScope.launch {
                    firebaseClient.updateParticipantDataModel(
                        participant = participantId,
                        data = SignalDataModel(
                            type = SignalDataModelTypes.ANSWER,
                            data = sessionDescription.description
                        )
                    )
                }
            }

            override fun onOfferGenerated(sessionDescription: SessionDescription) {
                viewModelScope.launch {
                    firebaseClient.updateParticipantDataModel(
                        participant = participantId,
                        data = SignalDataModel(
                            type = SignalDataModelTypes.OFFER,
                            data = sessionDescription.description
                        )
                    )
                }
            }
        })

        return rtcClient
    }

    fun findNextMatch() {
        rtcClient?.onDestroy()
        viewModelScope.launch {
            if(matchState.value == MatchState.Connected) {
                firebaseClient.updateParticipantStatus(
                    participantId,
                    StatusDataModel(
                        type = StatusDataModelTypes.LOOKING_FOR_MATCH
                    )
                )
            }
            firebaseClient.updateSelfStatus(StatusDataModel(type = StatusDataModelTypes.LOOKING_FOR_MATCH))
        }
    }
}