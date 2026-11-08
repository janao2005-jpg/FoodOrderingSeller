package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener

class SellerProductsFragment : Fragment() {
    private val repository = ProductRepository()
    private var listener: ValueEventListener? = null
    private lateinit var adapter: SellerProductAdapter
    private var selectedProduct: Product? = null
    private val imagePicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        val product = selectedProduct ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        val file = ProductPhotoCompressor.compress(requireContext(), uri)
        if (file == null) {
            Toast.makeText(requireContext(), "Photo must be smaller than 0.5 MB", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        S3PhotoManager().uploadProductPhoto(file, product.photoKey) { key, error ->
            if (!isAdded) return@uploadProductPhoto
            if (key == null) {
                toast(error ?: "Photo upload failed")
            } else {
                repository.updateProduct(product.copy(photoKey = key, photoUrl = Product.urlFor(key))) { ok, message ->
                    if (isAdded) toast(if (ok) "Product photo updated" else message ?: "Could not save photo")
                }
            }
            file.delete()
        }
    }
    private val camera = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val product = selectedProduct ?: return@registerForActivityResult
        if (bitmap == null || !isAdded) return@registerForActivityResult
        val file = ProductPhotoCompressor.compressBitmap(requireContext(), bitmap)
        if (file == null) toast("Photo could not be compressed below 0.5 MB")
        else uploadProductPhoto(product, file)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_seller_products, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            toast("Please sign in again")
            requireActivity().finish()
            return
        }
        val recycler = view.findViewById<RecyclerView>(R.id.sellerProductsRecyclerView)
        val empty = view.findViewById<TextView>(R.id.sellerProductsEmptyTextView)
        adapter = SellerProductAdapter(
            onEdit = { product -> showProductEditor(product) },
            onDelete = { product ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Product?")
                    .setMessage("Are you sure you want to delete this product?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        repository.deleteProduct(product.id) { ok, message ->
                            if (isAdded) toast(if (ok) "Product deleted" else message ?: "Could not delete product")
                        }
                    }
                    .show()
            }
        )
        recycler.layoutManager = GridLayoutManager(requireContext(), 2)
        recycler.adapter = adapter
        listener = repository.observeSellerProducts(uid, { products ->
            if (!isAdded) return@observeSellerProducts
            adapter.submit(products)
            empty.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
        }, { error -> if (isAdded) toast(error) })
    }

    override fun onDestroyView() {
        listener?.let { l -> FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            com.google.firebase.database.FirebaseDatabase.getInstance().reference
                .child("products").orderByChild("sellerId").equalTo(uid).removeEventListener(l)
        } }
        listener = null
        super.onDestroyView()
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    private fun showProductEditor(product: Product) {
        val context = requireContext()
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        fun field(hint: String, value: String, type: Int): EditText = EditText(context).apply {
            this.hint = hint
            setText(value)
            inputType = type
            textSize = 16f
            setSingleLine(true)
        }
        val name = field("Product name", product.name, android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val category = field("Category", product.category, android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val price = field("Price", product.price.toString(), android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val quantity = field("Stock quantity", product.quantity.toString(), android.text.InputType.TYPE_CLASS_NUMBER)
        val photo = Button(context).apply {
            text = "Change product photo"
            isAllCaps = false
            setOnClickListener {
                selectedProduct = product
                showPhotoOptions()
            }
        }
        content.addView(name)
        content.addView(category)
        content.addView(price)
        content.addView(quantity)
        content.addView(photo)
        AlertDialog.Builder(context)
            .setTitle("Edit product")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save changes") { _, _ ->
                val parsedPrice = price.text.toString().toDoubleOrNull()
                val parsedQuantity = quantity.text.toString().toIntOrNull()
                if (name.text.toString().isBlank() || parsedPrice == null || parsedPrice < 0.0 || parsedQuantity == null || parsedQuantity < 0) {
                    toast("Enter a name, non-negative price and quantity")
                } else {
                    repository.updateProduct(product.copy(name = name.text.toString().trim(), category = category.text.toString().trim(), price = parsedPrice, quantity = parsedQuantity)) { ok, message ->
                        if (isAdded) toast(if (ok) "Product updated" else message ?: "Could not update product")
                    }
                }
            }
            .show()
    }

    private fun showPhotoOptions() {
        AlertDialog.Builder(requireContext())
            .setTitle("Product photo")
            .setItems(arrayOf("Choose from Downloads or files", "Take a photo")) { _, which ->
                if (which == 0) imagePicker.launch(arrayOf("image/*")) else camera.launch(null)
            }
            .show()
    }

    private fun uploadProductPhoto(product: Product, file: java.io.File) {
        S3PhotoManager().uploadProductPhoto(file, product.photoKey) { key, error ->
            if (!isAdded) return@uploadProductPhoto
            if (key == null) toast(error ?: "Photo upload failed")
            else repository.updateProduct(product.copy(photoKey = key, photoUrl = Product.urlFor(key))) { ok, message ->
                if (isAdded) toast(if (ok) "Product photo updated" else message ?: "Could not save photo")
            }
            file.delete()
        }
    }
}

private class SellerProductAdapter(
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit,
) : RecyclerView.Adapter<SellerProductAdapter.Holder>() {
    private val items = mutableListOf<Product>()
    fun submit(products: List<Product>) {
        items.clear(); items.addAll(products); notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_seller_product, parent, false)
    )
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(items[position], onEdit, onDelete)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val image = view.findViewById<ImageView>(R.id.productImageView)
        private val name = view.findViewById<TextView>(R.id.productNameTextView)
        private val category = view.findViewById<TextView>(R.id.productCategoryTextView)
        private val price = view.findViewById<TextView>(R.id.productPriceTextView)
        private val quantity = view.findViewById<TextView>(R.id.productStockTextView)
        fun bind(
            product: Product,
            onEdit: (Product) -> Unit,
            onDelete: (Product) -> Unit
        ) {
            name.setText(product.name)
            category.text = product.category
            category.visibility = if (product.category.isBlank()) View.GONE else View.VISIBLE
            price.text = "${String.format(java.util.Locale.getDefault(), "%.2f", product.price)} EGP"
            quantity.text = "Stock: ${product.quantity}"
            image.setImageResource(R.drawable.food_image_placeholder)
            RemoteImageLoader.loadProduct(product.photoKey, image)
            itemView.findViewById<Button>(R.id.deleteProductButton).setOnClickListener { onDelete(product) }
            itemView.findViewById<Button>(R.id.saveProductButton).setOnClickListener { onEdit(product) }
        }
    }
}

