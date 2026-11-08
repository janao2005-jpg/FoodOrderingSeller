
package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private val webAppUrl =
        "https://script.google.com/macros/s/AKfycbx_Cmj5rU5Ps_yADO-kNtnWr8UB75L3-vPOtPClWKhIEMxeCfxQl5FR-9WEp54I1Hgxgw/exec"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val nameEditText =
            findViewById<EditText>(R.id.nameEditText)
        val emailEditText =
            findViewById<EditText>(R.id.emailEditText)
        val phoneEditText =
            findViewById<EditText>(R.id.phoneEditText)
        val addressEditText =
            findViewById<EditText>(R.id.addressEditText)
        val passwordEditText =
            findViewById<EditText>(R.id.passwordEditText)
        val confirmPasswordEditText =
            findViewById<EditText>(R.id.confirmPasswordEditText)

        val registerButton =
            findViewById<Button>(R.id.registerButton)
        val backToLoginButton =
            findViewById<Button>(R.id.backToLoginButton)

        registerButton.setOnClickListener {

            val name = nameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val phone = phoneEditText.text.toString().trim()
            val address = addressEditText.text.toString().trim()
            val password = passwordEditText.text.toString()
            val confirmPassword =
                confirmPasswordEditText.text.toString()

            if (
                name.isEmpty() ||
                email.isEmpty() ||
                phone.isEmpty() ||
                address.isEmpty() ||
                password.isEmpty() ||
                confirmPassword.isEmpty()
            ) {
                showMessage("Please fill all fields")
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                showMessage("Passwords do not match")
                return@setOnClickListener
            }

            if (password.length < 6) {
                showMessage("Password must be at least 6 characters")
                return@setOnClickListener
            }

            registerButton.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->

                    if (!task.isSuccessful) {
                        registerButton.isEnabled = true
                        showMessage(
                            task.exception?.message
                                ?: "Registration failed"
                        )
                        return@addOnCompleteListener
                    }

                    val user = auth.currentUser

                    if (user == null) {
                        registerButton.isEnabled = true
                        showMessage("Could not create account")
                        return@addOnCompleteListener
                    }

                    val uid = user.uid

                    val userProfile = hashMapOf<String, Any>(
                        "name" to name,
                        "email" to email,
                        "phone" to phone,
                        "address" to address,
                        "role" to "seller",
                        "verified" to false
                    )

                    val shopProfile = hashMapOf<String, Any>(
                        "sellerId" to uid,
                        "shopName" to name,
                        "name" to name,
                        "address" to address,
                        "verified" to false,
                        "photoKey" to "",
                        "photoUrl" to ""
                    )

                    FirebaseDatabase.getInstance().reference
                        .child("users").child(uid).setValue(userProfile)
                        .addOnSuccessListener {
                            FirebaseDatabase.getInstance().reference
                                .child("shops").child(uid).setValue(shopProfile)
                                .addOnSuccessListener { sendVerificationCode(email, uid) }
                                .addOnFailureListener { exception ->
                                    registerButton.isEnabled = true
                                    showMessage("Could not publish shop: ${exception.message}")
                                }
                        }
                        .addOnFailureListener { exception ->
                            registerButton.isEnabled = true
                            showMessage(
                                "Could not save profile: " +
                                        exception.message
                            )
                        }
                }
        }

        backToLoginButton.setOnClickListener {
            finish()
        }
    }

    private fun sendVerificationCode(
        email: String,
        uid: String
    ) {
        val requestQueue = Volley.newRequestQueue(this)

        val body = JSONObject().apply {
            put("action", "send")
            put("email", email)
        }

        val request = JsonObjectRequest(
            Request.Method.POST,
            webAppUrl,
            body,
            { response ->
                val success = response.optBoolean("success")
                val message = response.optString("message")

                if (success) {
                    showMessage("Verification code sent to $email")

                    val intent = Intent(
                        this,
                        VerificationActivity::class.java
                    )
                    intent.putExtra("email", email)
                    intent.putExtra("uid", uid)
                    startActivity(intent)
                    finish()
                } else {
                    showMessage(
                        message.ifEmpty {
                            "Could not send verification code"
                        }
                    )
                }
            },
            { error ->
                showMessage(
                    "Verification request failed: " +
                            (error.message ?: "Network error")
                )
            }
        )

        requestQueue.add(request)
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
