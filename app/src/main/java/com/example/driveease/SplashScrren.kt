package com.example.driveease

import android.content.Intent
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView

class SplashScrren : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_scrren)

        val logoImageView: ImageView = findViewById(R.id.splash_logo)
        val animation = AnimationUtils.loadAnimation(this, R.anim.logo_animation)
        logoImageView.startAnimation(animation)

        startHeavyTask()
    }

    private fun startHeavyTask() {
        LongOperation().execute()
    }

    // AsyncTask for performing a heavy task in the background
    private inner class LongOperation : AsyncTask<String?, Void?, String?>() {
        override fun doInBackground(vararg params: String?): String? {
            for (i in 0..3) {
                try {
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    Thread.interrupted()
                }
            }
            return "result"
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            // Handle UI updates or navigate to the next activity here
            val intent = Intent(this@SplashScrren, SignActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
