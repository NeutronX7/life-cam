package com.isaalutions.life_cam.remote

import com.google.firebase.database.DatabaseReference
import com.google.gson.Gson
import com.isaalutions.life_cam.utils.SharedPrefHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseClient @Inject constructor(
    private val database: DatabaseReference,
    private val prefHelper: SharedPrefHelper,
    private val gson: Gson
) {

    init {
        database.child(prefHelper.getUserId().toString()).setValue("Hello world, here, ISAAC")
    }
}