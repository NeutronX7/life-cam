package com.isaalutions.life_cam.remote

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.gson.Gson
import com.isaalutions.life_cam.utils.FirebaseFieldNames
import com.isaalutions.life_cam.utils.MatchState
import com.isaalutions.life_cam.utils.MyValueEventListener
import com.isaalutions.life_cam.utils.SharedPrefHelper
import com.isaalutions.life_cam.utils.SignalDataModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseClient @Inject constructor(
    private val database: DatabaseReference,
    private val prefHelper: SharedPrefHelper,
    private val gson: Gson
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun observerUserStatus(callBack : (MatchState) -> Unit) {
        coroutineScope.launch {
            removeSelfData()
            updateSelfStatus(StatusDataModel(type = StatusDataModelTypes.LOOKING_FOR_MATCH))
            val statusRef = database.child(FirebaseFieldNames.USERS).child(prefHelper.getUserId().toString())
                .child(FirebaseFieldNames.STATUS)
            statusRef.addValueEventListener(object : MyValueEventListener() {
                override fun onDataChange(snapshot: DataSnapshot) {
                    super.onDataChange(snapshot)
                    snapshot.getValue(StatusDataModel::class.java)?.let { status ->
                        val newState = when(status.type) {
                            StatusDataModelTypes.IDLE -> MatchState.IDLE
                            StatusDataModelTypes.LOOKING_FOR_MATCH -> MatchState.LookingForMatchState
                            StatusDataModelTypes.OFFERED_MATCH -> MatchState.OfferedMatchState(participant = status.participant!!)
                            StatusDataModelTypes.RECEIVED_MATCH -> MatchState.ReceivedMatchState(participant = status.participant!!)
                            StatusDataModelTypes.CONNECTED -> MatchState.Connected
                            else -> null
                        }

                        newState?.let {
                            callBack(it)
                        }?: coroutineScope.launch {
                            updateSelfStatus(StatusDataModel(type = StatusDataModelTypes.LOOKING_FOR_MATCH))
                            callBack(MatchState.LookingForMatchState)
                        }
                    }?: coroutineScope.launch {
                        updateSelfStatus(StatusDataModel(type = StatusDataModelTypes.LOOKING_FOR_MATCH))
                        callBack(MatchState.LookingForMatchState)
                    }
                }
            })
        }
    }

    suspend fun findNextMatch(){
        removeSelfData()
        findAvailableParticipant { foundTarget ->
            Log.d("FirebaseClient", "Found target: $foundTarget")
            foundTarget?.let { target ->
                coroutineScope.launch {
                    updateSelfStatus(StatusDataModel(type = StatusDataModelTypes.OFFERED_MATCH, participant = target))
                    database.child(FirebaseFieldNames.USERS).child(target)
                        .child(FirebaseFieldNames.STATUS).setValue(
                            StatusDataModel(type = StatusDataModelTypes.RECEIVED_MATCH, participant = prefHelper.getUserId())
                        ).await()
                }
            }
        }
    }

    private fun findAvailableParticipant(callback : (String?) -> Unit) {
        database.child(FirebaseFieldNames.USERS).orderByChild("status/type")
            .equalTo(StatusDataModelTypes.LOOKING_FOR_MATCH.name)
            .addListenerForSingleValueEvent(object : MyValueEventListener() {
                override fun onDataChange(snapshot: DataSnapshot) {
                    super.onDataChange(snapshot)
                    var foundTarget: String? = null
                    snapshot.children.forEach { childSnapShot ->
                        if(childSnapShot.key != prefHelper.getUserId()) {
                            foundTarget = childSnapShot.key
                            return@forEach
                        }
                    }
                    callback(foundTarget)
                }

                override fun onCancelled(error: DatabaseError) {
                    super.onCancelled(error)
                    callback(null)
                }
            })
    }

    fun observeIncomingSignals(callBack:(SignalDataModel)-> Unit) {
        database.child(FirebaseFieldNames.USERS).child(prefHelper.getUserId()!!)
            .child(FirebaseFieldNames.DATA).addValueEventListener(object : MyValueEventListener() {
                override fun onDataChange(snapshot: DataSnapshot) {
                    super.onDataChange(snapshot)
                    runCatching {
                        gson.fromJson(snapshot.value.toString(), SignalDataModel::class.java)
                    }
                    .onSuccess {
                        if(it!=null) callBack(it)
                    }
                    .onFailure {
                        Log.d("FirebaseClient", "Error parsing signal data: ${it.localizedMessage}")
                    }
                }
            })
    }

    suspend fun updateParticipantDataModel(participant:String, data: SignalDataModel) {
        database.child(FirebaseFieldNames.USERS).child(FirebaseFieldNames.DATA)
            .setValue(gson.toJson(data)).await()
    }

    suspend fun updateSelfStatus(status: StatusDataModel) {
        database.child(FirebaseFieldNames.USERS).child(prefHelper.getUserId()!!)
            .child(FirebaseFieldNames.STATUS).setValue(status)
            .await()
    }

    suspend fun removeSelfData() {
        database.child(FirebaseFieldNames.USERS).child(prefHelper.getUserId()!!)
            .child(FirebaseFieldNames.DATA).removeValue().await()
    }

    fun clear() {
        coroutineScope.cancel()
    }
}