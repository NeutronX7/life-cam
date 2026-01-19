package com.isaalutions.life_cam.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaalutions.life_cam.remote.FirebaseClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val firebaseClient: FirebaseClient
) : ViewModel() {

    fun permissionsGranted() {
        firebaseClient.observerUserStatus { status ->
            Log.d("MainViewModel", "User status updated: $status")
        }
        viewModelScope.launch {
            firebaseClient.findNextMatch()
        }
    }

}