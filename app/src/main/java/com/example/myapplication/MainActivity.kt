
package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val emailEditText =
            findViewById<EditText>(R.id.emailEditText)
        val passwordEditText =
            findViewById<EditText>(R.id.passwordEditText)
        val loginButton =
            findViewById<Button>(R.id.loginButton)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                showMessage("Please enter email and password")
                return@setOnClickListener
            }

            loginButton.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        loginButton.isEnabled = true
                        showMessage(
                            task.exception?.message ?: "Login failed"
                        )
                        return@addOnCompleteListener
                    }

                    val user = auth.currentUser
                    if (user == null) {
                        loginButton.isEnabled = true
                        showMessage("Login failed")
                        return@addOnCompleteListener
                    }

                    val uid = user.uid

                    FirebaseDatabase.getInstance()
                        .getReference("users")
                        .child(uid)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            loginButton.isEnabled = true

                            val role = snapshot.child("role")
                                .getValue(String::class.java)

                            val verified = snapshot.child("verified")
                                .getValue(Boolean::class.java) ?: false

                            if (role != "seller") {
                                showMessage(
                                    "This is not a Seller account. " +
                                            "Please use the Buyer App."
                                )
                                auth.signOut()
                                return@addOnSuccessListener
                            }

                            if (verified) {
                                showMessage("Seller login successful")

                                startActivity(
                                    Intent(
                                        this,
                                        SellerDashboardActivity::class.java
                                    )
                                )
                                finish()
                            } else {
                                showMessage(
                                    "Please verify your account first"
                                )

                                val intent = Intent(
                                    this,
                                    VerificationActivity::class.java
                                )
                                intent.putExtra("email", email)
                                intent.putExtra("uid", uid)
                                startActivity(intent)
                            }
                        }
                        .addOnFailureListener { exception ->
                            loginButton.isEnabled = true
                            auth.signOut()
                            showMessage(
                                "Database error: ${exception.message}"
                            )
                        }
                }
        }

    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}


