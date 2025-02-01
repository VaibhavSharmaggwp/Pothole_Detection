package com.example.driveease

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.os.Binder
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.driveease.databinding.ActivitySignBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.GoogleAuthProvider
import android.view.animation.AnimationUtils
import android.widget.ImageView



class SignActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
    private lateinit var binding: ActivitySignBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        // Initialize the correct binding for SignActivity
        binding = ActivitySignBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load and start the pulse animation
        val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
        findViewById<ImageView>(R.id.safetyIcon).startAnimation(pulseAnimation)

        auth = FirebaseAuth.getInstance()

        // Google sign in code goes here

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail().build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        binding.googleSignInBtn.setOnClickListener {
            signInGoogle()
        }

        val signInEmail: EditText = findViewById(R.id.signInEmail)
        val signInPassword: EditText = findViewById(R.id.signInPassword)
        val signInPasswordLayout: TextInputLayout = findViewById(R.id.signInPasswordLayout)
        val signInBtn: Button = findViewById(R.id.signInBtn)
        val signInProgressBar: ProgressBar = findViewById(R.id.signInProgressBar)

        val signUpText = binding.signUpText

        val adminLoginText = binding.adminLogin

        signUpText.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        adminLoginText.setOnClickListener{
            val intent = Intent(this, Admin_Signin::class.java)
            startActivity(intent)
        }


        signInBtn.setOnClickListener {
            signInProgressBar.visibility = View.VISIBLE

            signInPasswordLayout.isPasswordVisibilityToggleEnabled = true
            val email = signInEmail.text.toString()
            val password = signInPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                if (email.isEmpty()) {
                    signInEmail.error = "Enter your  email address"
                }
                if (password.isEmpty()) {
                    signInPassword.error = "Enter your password"
                    signInPasswordLayout.isPasswordVisibilityToggleEnabled = false
                }
                signInProgressBar.visibility = View.GONE
                Toast.makeText(this, "Enter valid details", Toast.LENGTH_LONG).show()

            } else if (!email.matches(emailPattern.toRegex())) {
                signInProgressBar.visibility = View.GONE
                signInEmail.error = "Enter valid email address"
                Toast.makeText(this, "Enter valid email address", Toast.LENGTH_LONG).show()
            } else if (password.length < 6) {
                signInPasswordLayout.isPasswordVisibilityToggleEnabled = false
                signInProgressBar.visibility = View.GONE
                signInPassword.error = "Enter password more than 6 characters"
                Toast.makeText(this, "Enter password more than 6 characters", Toast.LENGTH_LONG)
                    .show()
            } else {
                auth.signInWithEmailAndPassword(email, password).addOnCompleteListener {
                    if (it.isSuccessful) {
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                    } else {
                        Toast.makeText(
                            this,
                            "Something went wrong, Try again later",
                            Toast.LENGTH_LONG
                        ).show()
                        signInProgressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun signInGoogle() {
        // Create the intent for Google Sign-In
        val signInIntent = googleSignInClient.signInIntent
        // Launch the intent using the Activity Result API
        launcher.launch(signInIntent)
    }

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Check if the result is OK
            if (result.resultCode == Activity.RESULT_OK) {
                // Retrieve the task for Google Sign-In
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleResults(task)

            }



        }

    private fun handleResults(task: Task<GoogleSignInAccount>) {
        if(task.isSuccessful){
            val account: GoogleSignInAccount?= task.result
            if(account != null){
                updateUI(account)
            }

        }else{
            Toast.makeText(this, task.exception.toString(), Toast.LENGTH_LONG).show()
        }
    }

    private fun updateUI(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener{
            if(it.isSuccessful){

                val intent = Intent(this, Pothole_main::class.java)
                startActivity(intent)
            }
            else{
                Toast.makeText(this, it.exception.toString(), Toast.LENGTH_LONG).show()
            }
        }
    }
}