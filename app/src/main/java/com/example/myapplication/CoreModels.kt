package com.example.myapplication

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Seller(
    var uid: String = "",
    var name: String = "",
    var shopName: String = "",
    var email: String = "",
    var phone: String = "",
    var address: String = "",
    var role: String = "seller",
    var verified: Boolean = false,
    var photoKey: String = "",
    var photoUrl: String = ""
)

@IgnoreExtraProperties
data class Buyer(
    var uid: String = "",
    var name: String = "",
    var email: String = "",
    var phone: String = "",
    var address: String = "",
    var role: String = "buyer",
    var verified: Boolean = false,
    var photoKey: String = "",
    var photoUrl: String = ""
)

@IgnoreExtraProperties
data class OrderItem(
    var productId: String = "",
    var sellerId: String = "",
    var name: String = "",
    var unitPrice: Double = 0.0,
    var quantity: Int = 0,
    var lineTotal: Double = 0.0,
    var photoKey: String = "",
    var photoUrl: String = ""
)

@IgnoreExtraProperties
data class Order(
    var id: String = "",
    var buyerId: String = "",
    var sellerIds: MutableMap<String, Boolean> = mutableMapOf(),
    var items: MutableMap<String, OrderItem> = mutableMapOf(),
    var total: Double = 0.0,
    var status: String = "placed",
    var createdAt: Long = 0L
)
