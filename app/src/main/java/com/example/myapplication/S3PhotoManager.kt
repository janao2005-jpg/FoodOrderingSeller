
package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

class S3PhotoManager {

    companion object {
        private const val API_URL =
            "https://k1wwk5lbc5.execute-api.us-east-1.amazonaws.com/profile-photo"

        const val MAX_PHOTO_BYTES = 350000
        private const val MAX_REQUEST_BYTES = 500000
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private fun onMain(action: () -> Unit) {
        handler.post(action)
    }

    private fun withToken(
        callback: (String?, String?) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            callback(null, "Please log in first")
            return
        }

        user.getIdToken(false)
            .addOnSuccessListener {
                val token = it.token
                if (token.isNullOrEmpty()) {
                    callback(null, "Firebase token missing")
                } else {
                    callback(token, null)
                }
            }
            .addOnFailureListener {
                callback(null, it.message ?: "Authentication failed")
            }
    }

    private fun requestLambda(
        token: String,
        action: String,
        photoKey: String? = null
    ): JSONObject {

        val connection =
            URL(API_URL).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.doOutput = true

            connection.setRequestProperty(
                "Authorization", "Bearer $token"
            )
            connection.setRequestProperty(
                "Content-Type", "application/json"
            )

            val requestBody = JSONObject().put("action", action)
            if (!photoKey.isNullOrBlank()) {
                requestBody.put("photoKey", photoKey)
            }
            val body = requestBody.toString().toByteArray(Charsets.UTF_8)

            connection.outputStream.use {
                it.write(body)
            }

            val status = connection.responseCode

            val response = (
                    if (status in 200..299)
                        connection.inputStream
                    else
                        connection.errorStream
                    )?.bufferedReader()?.use {
                    it.readText()
                } ?: ""

            if (status !in 200..299) {
                val error = try {
                    JSONObject(response).optString(
                        "error", response
                    )
                } catch (e: Exception) {
                    response
                }

                throw Exception(
                    "API error $status: $error"
                )
            }

            return JSONObject(response)

        } finally {
            connection.disconnect()
        }
    }

    private fun sendFile(
        file: File,
        data: JSONObject
    ) {
        val uploadUrl = data.getString("uploadUrl")
        val fields = data.getJSONObject("fields")

        val boundary = "Photo-${UUID.randomUUID()}"
        val crlf = "\r\n"

        val output = ByteArrayOutputStream()

        val iterator = fields.keys()

        while (iterator.hasNext()) {
            val name = iterator.next()
            val value = fields.getString(name)

            output.write(
                "--$boundary$crlf".toByteArray()
            )
            output.write(
                ("Content-Disposition: form-data; " +
                        "name=\"$name\"$crlf").toByteArray()
            )
            output.write(crlf.toByteArray())
            output.write(value.toByteArray(Charsets.UTF_8))
            output.write(crlf.toByteArray())
        }

        output.write(
            "--$boundary$crlf".toByteArray()
        )
        output.write(
            ("Content-Disposition: form-data; " +
                    "name=\"file\"; filename=\"profile.jpg\"$crlf")
                .toByteArray()
        )
        output.write(
            "Content-Type: image/jpeg$crlf".toByteArray()
        )
        output.write(crlf.toByteArray())

        file.inputStream().use { input ->
            input.copyTo(output)
        }

        output.write(crlf.toByteArray())
        output.write(
            "--$boundary--$crlf".toByteArray()
        )

        val bytes = output.toByteArray()

        if (bytes.size > MAX_REQUEST_BYTES) {
            throw Exception(
                "Image request is larger than 0.5 MB"
            )
        }

        val connection =
            URL(uploadUrl).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )
            connection.setFixedLengthStreamingMode(bytes.size)

            connection.outputStream.use {
                it.write(bytes)
            }

            val status = connection.responseCode

            if (status !in 200..299) {
                val error = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() } ?: ""

                throw Exception(
                    "S3 upload error $status: $error"
                )
            }

        } finally {
            connection.disconnect()
        }
    }

    fun uploadPhoto(
        file: File,
        callback: (String?, String?) -> Unit
    ) = uploadPhotoWithAction(file, "upload", null, callback)

    fun uploadProductPhoto(
        file: File,
        existingPhotoKey: String? = null,
        callback: (String?, String?) -> Unit
    ) = uploadPhotoWithAction(file, "product_upload", existingPhotoKey, callback)

    private fun uploadPhotoWithAction(
        file: File,
        action: String,
        existingPhotoKey: String?,
        callback: (String?, String?) -> Unit
    ) {
        if (!file.exists() ||
            file.length() == 0L ||
            file.length() > MAX_PHOTO_BYTES
        ) {
            callback(null, "Invalid or oversized photo")
            return
        }

        withToken { token, error ->

            if (token == null) {
                callback(null, error)
                return@withToken
            }

            executor.execute {
                try {
                    val result = requestLambda(token, action, existingPhotoKey)

                    sendFile(file, result)

                    val photoKey = result.getString("photoKey")

                    onMain {
                        callback(photoKey, null)
                    }

                } catch (e: Exception) {
                    onMain {
                        callback(
                            null,
                            e.message ?: "Upload failed"
                        )
                    }
                }
            }
        }
    }

    fun downloadProductPhoto(
        photoKey: String,
        callback: (Bitmap?, String?) -> Unit
    ) {
        if (photoKey.isBlank()) {
            onMain { callback(null, "Product photo key is missing") }
            return
        }
        withToken { token, error ->
            if (token == null) {
                onMain { callback(null, error) }
                return@withToken
            }
            executor.execute {
                try {
                    val result = requestLambda(token, "product_download", photoKey)
                    if (result.optString("photoKey") != photoKey) {
                        throw Exception("The photo service returned a different product image")
                    }
                    val url = result.getString("downloadUrl")
                    val connection = URL(url).openConnection() as HttpURLConnection
                    val bitmap = try {
                        connection.connectTimeout = 15000
                        connection.readTimeout = 20000
                        connection.useCaches = false
                        connection.setRequestProperty("Cache-Control", "no-cache")
                        if (connection.responseCode !in 200..299) {
                            throw Exception("S3 download error ${connection.responseCode}")
                        }
                        connection.inputStream.use { BitmapFactory.decodeStream(it) }
                            ?: throw Exception("Could not decode product photo")
                    } finally {
                        connection.disconnect()
                    }
                    onMain { callback(bitmap, null) }
                } catch (e: Exception) {
                    onMain { callback(null, e.message ?: "Product photo download failed") }
                }
            }
        }
    }

    fun downloadPhoto(
        callback: (Bitmap?, String?) -> Unit
    ) {
        withToken { token, error ->

            if (token == null) {
                callback(null, error)
                return@withToken
            }

            executor.execute {
                try {
                    val result = requestLambda(token, "download")
                    val downloadUrl =
                        result.getString("downloadUrl")

                    val connection =
                        URL(downloadUrl).openConnection()
                                as HttpURLConnection

                    try {
                        connection.connectTimeout = 15000
                        connection.readTimeout = 20000
                        connection.useCaches = false
                        connection.setRequestProperty(
                            "Cache-Control", "no-cache"
                        )

                        if (connection.responseCode !in 200..299) {
                            throw Exception(
                                "S3 download error " +
                                        connection.responseCode
                            )
                        }

                        val bitmap =
                            connection.inputStream.use {
                                BitmapFactory.decodeStream(it)
                            } ?: throw Exception(
                                "Could not decode photo"
                            )

                        onMain {
                            callback(bitmap, null)
                        }

                    } finally {
                        connection.disconnect()
                    }

                } catch (e: Exception) {
                    onMain {
                        callback(
                            null,
                            e.message ?: "Download failed"
                        )
                    }
                }
            }
        }
    }
}
