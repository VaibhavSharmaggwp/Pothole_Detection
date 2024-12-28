// MainActivity
package com.example.driveease

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.driveease.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    //private val viewModel by viewModels<MainViewModel>()

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // here is splash screen
//        installSplashScreen().apply {
//            setKeepOnScreenCondition{
//                !viewModel.isReady.value
//            }
//        }
        setContentView(R.layout.activity_main)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Check if the user is logged in
        if (auth.currentUser == null) {
            val intent = Intent(this, SignActivity::class.java)
            startActivity(intent)
            finish()  // This line will close MainActivity, preventing "Hello World" from being displayed.
        }else{
            val intent = Intent(this, Pothole_main::class.java)
            startActivity(intent)
            finish()
        }
    }
}