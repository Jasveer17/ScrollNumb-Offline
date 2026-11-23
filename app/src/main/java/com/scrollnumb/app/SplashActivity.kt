package com.scrollnumb.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences("scroll_numb_prefs", Context.MODE_PRIVATE)
        val isNewUser = sharedPrefs.getBoolean("IS_NEW_USER", true)

        if (isNewUser) {
            // New users land on the Welcome screen (Get Started / Login)
            val intent = Intent(this, com.scrollnumb.app.feature.welcome.WelcomeActivity::class.java)
            startActivity(intent)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
