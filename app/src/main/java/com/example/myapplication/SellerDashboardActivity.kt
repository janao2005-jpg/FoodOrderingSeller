package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SellerDashboardActivity : AppCompatActivity() {
    private val root = FirebaseDatabase.getInstance().reference
    private var productsListener: ValueEventListener? = null
    private var ordersListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seller_dashboard)
        window.statusBarColor = getColor(R.color.aklny_cream)
        window.navigationBarColor = getColor(R.color.aklny_cream)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            finish()
            return
        }
        root.child("users").child(uid).child("name").get().addOnSuccessListener {
            findViewById<TextView>(R.id.sellerDashboardGreetingTextView).text =
                "Welcome back, ${it.getValue(String::class.java).orEmpty()}"
        }
        productsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                findViewById<TextView>(R.id.sellerProductCountTextView).text = snapshot.children.count().toString()
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }.also { root.child("products").orderByChild("sellerId").equalTo(uid).addValueEventListener(it) }
        ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.children.count { order ->
                    order.child("sellerIds").hasChild(uid) ||
                        order.child("items").children.any { it.child("sellerId").getValue(String::class.java) == uid }
                }
                findViewById<TextView>(R.id.sellerOrderCountTextView).text = count.toString()
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }.also { root.child("orders").addValueEventListener(it) }

        findViewById<Button>(R.id.sellerDashboardProductsButton).setOnClickListener {
            startActivity(Intent(this, SellerInventoryActivity::class.java))
        }
        findViewById<Button>(R.id.sellerDashboardOrdersButton).setOnClickListener {
            startActivity(Intent(this, SellerOrdersActivity::class.java))
        }
        findViewById<Button>(R.id.sellerDashboardProfileButton).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<Button>(R.id.sellerDashboardBottomHomeButton).setOnClickListener { }
        findViewById<Button>(R.id.sellerDashboardBottomProductsButton).setOnClickListener {
            startActivity(Intent(this, SellerInventoryActivity::class.java))
        }
        findViewById<Button>(R.id.sellerDashboardBottomOrdersButton).setOnClickListener {
            startActivity(Intent(this, SellerOrdersActivity::class.java))
        }
        findViewById<Button>(R.id.sellerDashboardBottomProfileButton).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onDestroy() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        productsListener?.let { if (uid != null) root.child("products").orderByChild("sellerId").equalTo(uid).removeEventListener(it) }
        ordersListener?.let { root.child("orders").removeEventListener(it) }
        super.onDestroy()
    }
}

