package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import android.graphics.BitmapFactory
import java.io.File

class AddProductFragment : Fragment() {
    private var selectedPhoto: File? = null
    private lateinit var preview: ImageView
    private lateinit var imageStatus: TextView
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null || !isAdded) return@registerForActivityResult
        val file = ProductPhotoCompressor.compress(requireContext(), uri)
        if (file == null) {
            toast("Choose an image that can be compressed below 0.5 MB")
        } else {
            selectedPhoto?.delete()
            selectedPhoto = file
            preview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            imageStatus.text = "✓  Image ready · under 0.5 MB"
        }
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null && isAdded) {
            val file = ProductPhotoCompressor.compressBitmap(requireContext(), bitmap)
            if (file == null) toast("Photo could not be compressed below 0.5 MB")
            else {
                selectedPhoto?.delete()
                selectedPhoto = file
                preview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                imageStatus.text = "✓  Image ready · under 0.5 MB"
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_add_product, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        preview = view.findViewById(R.id.newProductImageView)
        imageStatus = view.findViewById(R.id.productImageStatusTextView)
        view.findViewById<Button>(R.id.selectProductImageButton).setOnClickListener {
            imagePicker.launch(arrayOf("image/*"))
        }
        view.findViewById<Button>(R.id.captureProductImageButton).setOnClickListener {
            camera.launch(null)
        }
        view.findViewById<Button>(R.id.saveNewProductButton).setOnClickListener {
            val name = view.findViewById<EditText>(R.id.newProductNameEditText).text.toString().trim()
            val category = view.findViewById<EditText>(R.id.newProductCategoryEditText).text.toString().trim()
            val price = view.findViewById<EditText>(R.id.newProductPriceEditText).text.toString().toDoubleOrNull()
            val quantity = view.findViewById<EditText>(R.id.newProductQuantityEditText).text.toString().toIntOrNull()
            val sellerId = FirebaseAuth.getInstance().currentUser?.uid
            val photo = selectedPhoto
            if (name.isBlank() || price == null || price < 0.0 || quantity == null || quantity < 0 || sellerId.isNullOrBlank() || photo == null) {
                toast("Enter valid product details and choose a photo")
                return@setOnClickListener
            }
            val button = view.findViewById<Button>(R.id.saveNewProductButton)
            button.isEnabled = false
            button.text = "Uploading photo..."
            S3PhotoManager().uploadProductPhoto(photo) { photoKey, error ->
                if (!isAdded) return@uploadProductPhoto
                if (photoKey == null) {
                    button.isEnabled = true
                    button.text = "Save Product"
                    toast(error ?: "Photo upload failed")
                } else {
                    button.text = "Saving product..."
                    ProductRepository().addProduct(
                        Product(sellerId = sellerId, name = name, category = category, price = price, quantity = quantity,
                            photoKey = photoKey, photoUrl = Product.urlFor(photoKey))
                    ) { ok, saveError ->
                        if (!isAdded) return@addProduct
                        button.isEnabled = true
                        button.text = "Save Product"
                        if (ok) {
                            photo.delete()
                            selectedPhoto = null
                            (activity as? SellerInventoryActivity)?.showProducts()
                            Toast.makeText(requireContext(), "Product added and available to buyers", Toast.LENGTH_LONG).show()
                        } else toast(saveError ?: "Could not save product")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        selectedPhoto?.delete()
        selectedPhoto = null
        super.onDestroyView()
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
}

