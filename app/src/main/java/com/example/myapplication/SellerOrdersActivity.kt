package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.DateFormat
import java.util.Date

private data class SellerOrderRow(
    val id: String,
    val buyer: String,
    val status: String,
    val createdAt: Long,
    val items: String,
    val total: Double
)

class SellerOrdersActivity : AppCompatActivity() {
    private val ordersRef = FirebaseDatabase.getInstance().reference.child("orders")
    private var listener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seller_orders)
        window.statusBarColor = getColor(R.color.aklny_cream)
        window.navigationBarColor = getColor(R.color.aklny_cream)
        findViewById<Button>(R.id.sellerOrdersBackButton).setOnClickListener { finish() }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        val list = findViewById<RecyclerView>(R.id.sellerOrdersRecyclerView)
        val empty = findViewById<TextView>(R.id.sellerOrdersEmptyTextView)
        val adapter = SellerOrderAdapter { row, nextStatus ->
            ordersRef.child(row.id).child("status").setValue(nextStatus)
                .addOnSuccessListener { Toast.makeText(this, "Order marked $nextStatus", Toast.LENGTH_SHORT).show() }
                .addOnFailureListener { Toast.makeText(this, it.message ?: "Could not update order", Toast.LENGTH_LONG).show() }
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rows = snapshot.children.mapNotNull { order ->
                    val items = order.child("items").children.filter {
                        it.child("sellerId").getValue(String::class.java) == uid
                    }
                    if (!order.child("sellerIds").hasChild(uid) && items.isEmpty()) return@mapNotNull null
                    val summary = items.joinToString("\n") { item ->
                        val name = item.child("name").getValue(String::class.java).orEmpty()
                        val quantity = item.child("quantity").getValue(Int::class.java) ?: 0
                        "$quantity × $name"
                    }
                    val total = items.sumOf { item ->
                        (item.child("unitPrice").getValue(Double::class.java) ?: 0.0) *
                            (item.child("quantity").getValue(Int::class.java) ?: 0)
                    }
                    SellerOrderRow(
                        id = order.key.orEmpty(),
                        buyer = order.child("buyerId").getValue(String::class.java).orEmpty(),
                        status = order.child("status").getValue(String::class.java) ?: "placed",
                        createdAt = order.child("createdAt").getValue(Long::class.java) ?: 0L,
                        items = summary.ifBlank { "Order received" },
                        total = total
                    )
                }.sortedByDescending { it.createdAt }
                adapter.submit(rows)
                empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@SellerOrdersActivity, error.message, Toast.LENGTH_LONG).show()
            }
        }.also { ordersRef.addValueEventListener(it) }
    }

    override fun onDestroy() {
        listener?.let { ordersRef.removeEventListener(it) }
        super.onDestroy()
    }
}

private class SellerOrderAdapter(private val update: (SellerOrderRow, String) -> Unit) : RecyclerView.Adapter<SellerOrderAdapter.Holder>() {
    private val rows = mutableListOf<SellerOrderRow>()
    fun submit(items: List<SellerOrderRow>) { rows.clear(); rows.addAll(items); notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_seller_order, parent, false)
    )
    override fun getItemCount() = rows.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(rows[position], update)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(row: SellerOrderRow, update: (SellerOrderRow, String) -> Unit) {
            itemView.findViewById<TextView>(R.id.sellerOrderIdTextView).text = "Order #${row.id.takeLast(7)}"
            itemView.findViewById<TextView>(R.id.sellerOrderStatusTextView).text = row.status.replaceFirstChar { it.uppercase() }
            itemView.findViewById<TextView>(R.id.sellerOrderItemsTextView).text = row.items
            itemView.findViewById<TextView>(R.id.sellerOrderTotalTextView).text = "Your items · ${String.format(java.util.Locale.getDefault(), "%.2f", row.total)}"
            itemView.findViewById<TextView>(R.id.sellerOrderDateTextView).text = if (row.createdAt > 0L) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(row.createdAt)) else "New order"
            val button = itemView.findViewById<Button>(R.id.sellerOrderActionButton)
            when (row.status.lowercase()) {
                "placed", "pending" -> { button.text = "Accept order"; button.isEnabled = true; button.setOnClickListener { update(row, "accepted") } }
                "accepted", "preparing" -> { button.text = "Mark ready"; button.isEnabled = true; button.setOnClickListener { update(row, "ready") } }
                "ready" -> { button.text = "Mark delivered"; button.isEnabled = true; button.setOnClickListener { update(row, "delivered") } }
                else -> { button.text = "Completed"; button.isEnabled = false }
            }
        }
    }
}
