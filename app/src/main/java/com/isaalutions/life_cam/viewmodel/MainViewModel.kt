package com.isaalutions.life_cam.viewmodel

import androidx.lifecycle.ViewModel
import com.isaalutions.life_cam.remote.FirebaseClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val firebaseClient: FirebaseClient
) : ViewModel() {

}