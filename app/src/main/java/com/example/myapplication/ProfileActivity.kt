
package com.example.myapplication

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var profileImageView: ImageView
    private lateinit var saveButton: Button

    private val s3Manager = S3PhotoManager()
    private var compressedFile: File? = null

    private fun message(text: String) {
        Toast.makeText(
            this, text, Toast.LENGTH_LONG
        ).show()
    }

    private val imagePicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val file = compressImage(uri)

                if (file != null) {
                    compressedFile = file
                    profileImageView.setImageURI(uri)
                    message(
                        "Picture selected. Press Save Changes to upload."
                    )
                } else {
                    message("Could not compress image")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()

        profileImageView =
            findViewById(R.id.profileImageView)

        val selectPictureButton =
            findViewById<Button>(
                R.id.selectProfilePictureButton
            )

        val nameEditText =
            findViewById<EditText>(
                R.id.profileNameEditText
            )

        val emailEditText =
            findViewById<EditText>(
                R.id.profileEmailEditText
            )

        val phoneEditText =
            findViewById<EditText>(
                R.id.profilePhoneEditText
            )

        val addressEditText =
            findViewById<EditText>(
                R.id.profileAddressEditText
            )

        val roleTextView =
            findViewById<TextView>(
                R.id.profileRoleTextView
            )

        saveButton =
            findViewById(R.id.saveProfileButton)

        val logoutButton =
            findViewById<Button>(R.id.logoutButton)

        findViewById<Button>(R.id.manageProductsButton).setOnClickListener {
            startActivity(Intent(this, SellerInventoryActivity::class.java))
        }

        val currentUser = auth.currentUser

        if (currentUser == null) {
            startActivity(
                Intent(this, WelcomeActivity::class.java)
            )
            finish()
            return
        }

        val userRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(currentUser.uid)

        // SELECT PROFILE PICTURE
        selectPictureButton.setOnClickListener {
            imagePicker.launch("image/*")
        }

        // LOAD EXISTING SELLER PROFILE
        userRef.get()
            .addOnSuccessListener { snapshot ->

                val role = snapshot.child("role")
                    .getValue(String::class.java)

                if (role != "seller") {
                    message("This account is not a Seller account")
                    auth.signOut()
                    startActivity(
                        Intent(this, WelcomeActivity::class.java)
                    )
                    finish()
                    return@addOnSuccessListener
                }

                val name = snapshot.child("name")
                    .getValue(String::class.java)

                val email = snapshot.child("email")
                    .getValue(String::class.java)

                val phone = snapshot.child("phone")
                    .getValue(String::class.java)

                val address = snapshot.child("address")
                    .getValue(String::class.java)

                val photoKey = snapshot.child("photoKey")
                    .getValue(String::class.java)
                val photoUrl = snapshot.child("photoUrl")
                    .getValue(String::class.java)
                val verified = snapshot.child("verified")
                    .getValue(Boolean::class.java) ?: false

                nameEditText.setText(name)
                emailEditText.setText(email ?: currentUser.email)
                phoneEditText.setText(phone)
                addressEditText.setText(address)
                roleTextView.text = "Role: seller"

                // Publish only the shop fields buyers need; keep seller contact data private.
                FirebaseDatabase.getInstance().reference
                    .child("shops").child(currentUser.uid)
                    .updateChildren(
                        mapOf(
                            "sellerId" to currentUser.uid,
                            "shopName" to (name ?: "Seller shop"),
                            "name" to (name ?: "Seller shop"),
                            "address" to address.orEmpty(),
                            "verified" to verified,
                            "photoKey" to photoKey.orEmpty(),
                            "photoUrl" to photoUrl.orEmpty()
                        )
                    )

                // Load picture from private AWS S3 bucket
                if (!photoKey.isNullOrEmpty()) {
                    s3Manager.downloadPhoto { bitmap, error ->

                        if (!isFinishing && !isDestroyed) {
                            if (bitmap != null) {
                                if (compressedFile == null) {
                                    profileImageView.setImageBitmap(bitmap)
                                }
                            } else {
                                message(
                                    error ?: "Could not load photo"
                                )
                            }
                        }
                    }
                }
            }
            .addOnFailureListener {
                message(
                    "Could not load profile: ${it.message}"
                )
            }

        // SAVE PROFILE AND PHOTO
        saveButton.setOnClickListener {

            val name = nameEditText.text.toString().trim()
            val phone = phoneEditText.text.toString().trim()
            val address = addressEditText.text.toString().trim()

            if (name.isEmpty() ||
                phone.isEmpty() ||
                address.isEmpty()
            ) {
                message("Please fill all fields")
                return@setOnClickListener
            }

            saveButton.isEnabled = false
            saveButton.text = "Saving..."

            val updates = mutableMapOf<String, Any>(
                "name" to name,
                "phone" to phone,
                "address" to address
            )

            val file = compressedFile

            fun saveToFirebase() {
                val databaseUpdates = mutableMapOf<String, Any>()
                updates.forEach { (field, value) ->
                    databaseUpdates["users/${currentUser.uid}/$field"] = value
                }
                databaseUpdates["shops/${currentUser.uid}/sellerId"] = currentUser.uid
                databaseUpdates["shops/${currentUser.uid}/shopName"] = name
                databaseUpdates["shops/${currentUser.uid}/name"] = name
                databaseUpdates["shops/${currentUser.uid}/address"] = address
                databaseUpdates["shops/${currentUser.uid}/verified"] = true
                updates["photoKey"]?.let { databaseUpdates["shops/${currentUser.uid}/photoKey"] = it }
                updates["photoUrl"]?.let { databaseUpdates["shops/${currentUser.uid}/photoUrl"] = it }
                FirebaseDatabase.getInstance().reference.updateChildren(databaseUpdates)
                    .addOnSuccessListener {
                        saveButton.isEnabled = true
                        saveButton.text = "Save Changes"
                        compressedFile = null
                        message("Profile saved successfully")
                    }
                    .addOnFailureListener { exception ->
                        saveButton.isEnabled = true
                        saveButton.text = "Save Changes"
                        message(
                            "Firebase error: ${exception.message}"
                        )
                    }
            }

            if (file == null) {
                // Only text fields changed
                saveToFirebase()
            } else {
                // Upload picture first, then save the
                // photo reference and profile in Firebase.
                s3Manager.uploadPhoto(file) { photoKey, error ->

                    if (!isFinishing && !isDestroyed) {
                        if (photoKey != null) {

                            updates["photoKey"] = photoKey
                            updates["photoUrl"] =
                                "https://seifmarya12.s3.eu-north-1.amazonaws.com/$photoKey"
                            saveToFirebase()

                        } else {
                            saveButton.isEnabled = true
                            saveButton.text = "Save Changes"
                            message(
                                "AWS upload failed: " +
                                        (error ?: "Unknown error")
                            )
                        }
                    }
                }
            }
        }

        // LOGOUT
        logoutButton.setOnClickListener {

            auth.signOut()

            val intent =
                Intent(this, WelcomeActivity::class.java)

            intent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(intent)
            finish()
        }
    }

    // COMPRESS PICTURE UNDER 0.5 MB
    private fun compressImage(uri: Uri): File? {

        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            var sample = 1

            while (
                bounds.outWidth / sample > 1500 ||
                bounds.outHeight / sample > 1500
            ) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
            }

            var bitmap: Bitmap =
                contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(
                        it, null, options
                    )
                } ?: return null

            val output = ByteArrayOutputStream()
            var quality = 85

            fun encode() {
                output.reset()
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    quality,
                    output
                )
            }

            encode()

            while (
                output.size() >
                S3PhotoManager.MAX_PHOTO_BYTES &&
                quality > 35
            ) {
                quality -= 10
                encode()
            }

            while (
                output.size() >
                S3PhotoManager.MAX_PHOTO_BYTES &&
                bitmap.width > 100 &&
                bitmap.height > 100
            ) {
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * 0.75).toInt(),
                    (bitmap.height * 0.75).toInt(),
                    true
                )

                quality = 75
                encode()
            }

            if (
                output.size() >
                S3PhotoManager.MAX_PHOTO_BYTES
            ) {
                return null
            }

            val file = File(
                cacheDir,
                "seller_${System.currentTimeMillis()}.jpg"
            )

            FileOutputStream(file).use {
                it.write(output.toByteArray())
            }

            output.close()
            file

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

