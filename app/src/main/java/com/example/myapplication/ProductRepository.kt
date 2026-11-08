package com.example.myapplication

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProductRepository {
    private val products = FirebaseDatabase.getInstance().reference.child("products")

    fun observeSellerProducts(
        sellerId: String,
        onResult: (List<Product>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onResult(snapshot.children.mapNotNull { item ->
                    item.getValue(Product::class.java)?.copy(id = item.key.orEmpty())
                })
            }

            override fun onCancelled(error: DatabaseError) = onError(error.message)
        }
        products.orderByChild("sellerId").equalTo(sellerId).addValueEventListener(listener)
        return listener
    }

    fun addProduct(product: Product, onComplete: (Boolean, String?) -> Unit) {
        val id = products.push().key
        if (id == null) {
            onComplete(false, "Could not create product")
            return
        }
        products.child(id).setValue(product.copy(id = id))
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful, task.exception?.message)
            }
    }

    fun updateProduct(product: Product, onComplete: (Boolean, String?) -> Unit) {
        if (product.id.isBlank()) {
            onComplete(false, "Product id is missing")
            return
        }
        products.child(product.id).updateChildren(
            mapOf(
                "name" to product.name,
                "price" to product.price,
                "quantity" to product.quantity,
                "category" to product.category,
                "photoKey" to product.photoKey,
                "photoUrl" to (product.photoUrl.ifBlank { Product.urlFor(product.photoKey) })
            )
        ).addOnCompleteListener { task ->
            onComplete(task.isSuccessful, task.exception?.message)
        }
    }

    fun deleteProduct(productId: String, onComplete: (Boolean, String?) -> Unit) {
        if (productId.isBlank()) {
            onComplete(false, "Product id is missing")
            return
        }
        products.child(productId).removeValue().addOnCompleteListener { task ->
            onComplete(task.isSuccessful, task.exception?.message)
        }
    }
}



