package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class WelcomeActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.aklny_plum)
        window.navigationBarColor = getColor(R.color.aklny_plum)
        val user = auth.currentUser
        if (user == null) {
            showWelcome()
        } else {
            showSessionCheck()
            FirebaseDatabase.getInstance().getReference("users").child(user.uid).get()
                .addOnSuccessListener { snapshot ->
                    if (isFinishing || isDestroyed) return@addOnSuccessListener
                    val role = snapshot.child("role").getValue(String::class.java)?.lowercase()
                    if (role != "seller") {
                        auth.signOut()
                        showWelcome()
                        Toast.makeText(this, "Use the Buyer app for this account.", Toast.LENGTH_LONG).show()
                    } else if (snapshot.child("verified").getValue(Boolean::class.java) == true) {
                        open(SellerDashboardActivity::class.java)
                    } else {
                        startActivity(Intent(this, VerificationActivity::class.java)
                            .putExtra("email", user.email.orEmpty()).putExtra("uid", user.uid))
                        finish()
                    }
                }
                .addOnFailureListener {
                    if (!isFinishing && !isDestroyed) {
                        showWelcome()
                        Toast.makeText(this, "Couldn't check your saved session. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun showWelcome() {
        setContentView(R.layout.activity_welcome)
        findViewById<Button>(R.id.welcomeSignInButton).setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        findViewById<Button>(R.id.welcomeSignUpButton).setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
    }

    private fun showSessionCheck() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(255, 246, 231))
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "aklny"
            textSize = 40f
            setTextColor(getColor(R.color.aklny_mint))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.aklny_plum))
        }, LinearLayout.LayoutParams(48, 48).apply { topMargin = 22 })
        setContentView(root)
    }

    private fun open(destination: Class<*>) {
        startActivity(Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
