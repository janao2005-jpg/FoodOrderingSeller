package com.example.myapplication

import android.net.Uri
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Product(
    var id: String = "",
    var sellerId: String = "",
    var name: String = "",
    var price: Double = 0.0,
    var quantity: Int = 0,
    var category: String = "",
    var photoKey: String = "",
    var photoUrl: String = ""
) {
    companion object {
        private const val S3_BASE_URL = "https://seifmarya12.s3.eu-north-1.amazonaws.com/"
        fun urlFor(photoKey: String) = if (photoKey.isBlank()) "" else
            S3_BASE_URL + Uri.encode(photoKey, "/")
    }
}



