package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback

class SellerInventoryActivity : AppCompatActivity() {
    private var showingAddProduct = false

    fun showProducts() {
        showingAddProduct = false
        findViewById<TextView>(R.id.inventoryTitleTextView).text = "My Products"
        findViewById<Button>(R.id.inventoryBackButton).visibility = android.view.View.VISIBLE
        findViewById<Button>(R.id.inventoryBackButton).contentDescription = "Back to profile"
        findViewById<Button>(R.id.addProductButton).visibility = android.view.View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.inventoryFragmentContainer, SellerProductsFragment())
            .commit()
    }

    fun showAddProduct() {
        showingAddProduct = true
        findViewById<TextView>(R.id.inventoryTitleTextView).text = "Add Product"
        findViewById<Button>(R.id.inventoryBackButton).visibility = android.view.View.VISIBLE
        findViewById<Button>(R.id.inventoryBackButton).contentDescription = "Back to my products"
        findViewById<Button>(R.id.addProductButton).visibility = android.view.View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.inventoryFragmentContainer, AddProductFragment())
            .commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seller_inventory)
        findViewById<Button>(R.id.inventoryBackButton).setOnClickListener {
            if (showingAddProduct) showProducts() else finish()
        }
        findViewById<Button>(R.id.addProductButton).setOnClickListener {
            showAddProduct()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showingAddProduct) showProducts() else finish()
            }
        })
        if (savedInstanceState == null) showProducts()
    }
}
