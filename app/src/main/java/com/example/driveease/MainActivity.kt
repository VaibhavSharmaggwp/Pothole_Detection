package com.example.driveease

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()



        // Check if the user is logged in
        if (auth.currentUser == null) {
            // Redirect to SignActivity if user is not logged in
            val intent = Intent(this, SignActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            // Redirect to Pothole_main directly if user is logged in
            val intent = Intent(this, Pothole_main::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }
}