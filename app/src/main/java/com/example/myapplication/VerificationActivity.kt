
package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONObject

class VerificationActivity : AppCompatActivity() {

    private val webAppUrl =
        "https://script.google.com/macros/s/AKfycbx_Cmj5rU5Ps_yADO-kNtnWr8UB75L3-vPOtPClWKhIEMxeCfxQl5FR-9WEp54I1Hgxgw/exec"

    private lateinit var email: String
    private lateinit var uid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        email = intent.getStringExtra("email") ?: ""
        uid = intent.getStringExtra("uid") ?: ""

        val messageTextView =
            findViewById<TextView>(
                R.id.verificationMessageTextView
            )

        val codeEditText =
            findViewById<EditText>(R.id.codeEditText)

        val verifyButton =
            findViewById<Button>(R.id.verifyButton)

        val resendButton =
            findViewById<Button>(R.id.resendCodeButton)

        if (email.isBlank() || uid.isBlank()) {
            showMessage("Missing verification information")
            finish()
            return
        }

        messageTextView.text =
            "We sent a 6-digit code to:\n$email"

        verifyButton.setOnClickListener {
            val code = codeEditText.text.toString().trim()

            if (code.length != 6 || !code.all { it.isDigit() }) {
                showMessage("Please enter the 6-digit code")
                return@setOnClickListener
            }

            verifyCode(code)
        }

        resendButton.setOnClickListener {
            resendCode()
        }
    }

    private fun verifyCode(code: String) {

        val body = JSONObject().apply {
            put("action", "verify")
            put("email", email)
            put("code", code)
        }

        val request = JsonObjectRequest(
            Request.Method.POST,
            webAppUrl,
            body,
            { response ->
                if (response.optBoolean("success")) {

                    val currentUser =
                        FirebaseAuth.getInstance().currentUser

                    if (currentUser == null ||
                        currentUser.uid != uid
                    ) {
                        showMessage(
                            "Session expired. Please log in again."
                        )
                        return@JsonObjectRequest
                    }

                    val database = FirebaseDatabase.getInstance().reference
                    database.child("users").child(uid).get()
                        .addOnSuccessListener { profile ->
                            val name = profile.child("name").getValue(String::class.java).orEmpty()
                            val shop = mapOf(
                                "sellerId" to uid,
                                "shopName" to name,
                                "name" to name,
                                "address" to profile.child("address").getValue(String::class.java).orEmpty(),
                                "verified" to true,
                                "photoKey" to profile.child("photoKey").getValue(String::class.java).orEmpty(),
                                "photoUrl" to profile.child("photoUrl").getValue(String::class.java).orEmpty()
                            )
                            database.updateChildren(
                                mapOf("users/$uid/verified" to true, "shops/$uid" to shop)
                            )
                        .addOnSuccessListener {
                            showMessage(
                                "Account verified successfully"
                            )

                            val intent = Intent(
                                this,
                                SellerDashboardActivity::class.java
                            )
                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK

                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { error ->
                            showMessage(
                                "Could not save verification: " +
                                        error.message
                            )
                        }
                        }
                        .addOnFailureListener { error ->
                            showMessage("Could not load profile: ${error.message}")
                        }

                } else {
                    showMessage(
                        response.optString(
                            "message",
                            "Invalid verification code"
                        )
                    )
                }
            },
            { error ->
                showMessage(
                    "Verification failed: " +
                            (error.message ?: "Network error")
                )
            }
        )

        Volley.newRequestQueue(this).add(request)
    }

    private fun resendCode() {

        val body = JSONObject().apply {
            put("action", "send")
            put("email", email)
        }

        val request = JsonObjectRequest(
            Request.Method.POST,
            webAppUrl,
            body,
            { response ->
                if (response.optBoolean("success")) {
                    showMessage("New verification code sent")
                } else {
                    showMessage(
                        response.optString(
                            "message",
                            "Could not resend code"
                        )
                    )
                }
            },
            { error ->
                showMessage(
                    "Could not resend code: " +
                            (error.message ?: "Network error")
                )
            }
        )

        Volley.newRequestQueue(this).add(request)
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}




