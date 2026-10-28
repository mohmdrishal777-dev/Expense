package com.razal.expenses

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

data class Expense(
    val id: Long,
    val ts: Long,
    val amount: Double,
    val merchant: String,
    val category: String,
    val purpose: String,
    val source: String,
    val key: String
)

object Store {
    val CATEGORIES = listOf(
        "Uncategorized", "Food", "Groceries", "Transport", "Fuel", "Bills",
        "Shopping", "Health", "Entertainment", "Travel", "Other"
    )

    private lateinit var prefs: SharedPreferences
    private val items = mutableListOf<Expense>()
    private var lastId = 0L

    var version by mutableIntStateOf(0)
        private set
    var budget by mutableDoubleStateOf(0.0)
        private set

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("expenses", Context.MODE_PRIVATE)
        budget = prefs.getString("budget", "0")?.toDoubleOrNull() ?: 0.0
        val arr = JSONArray(prefs.getString("items", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            items.add(
                Expense(
                    o.getLong("id"), o.getLong("ts"), o.getDouble("amount"),
                    o.getString("merchant"), o.getString("category"), o.getString("purpose"),
                    o.getString("source"), o.getString("key")
                )
            )
            lastId = maxOf(lastId, o.getLong("id"))
        }
    }

    @Synchronized
    fun newId(): Long {
        lastId = maxOf(lastId + 1, System.currentTimeMillis())
        return lastId
    }

    @Synchronized
    fun add(e: Expense): Boolean {
        if (e.key.isNotEmpty() && items.any { it.key == e.key && abs(it.ts - e.ts) < 10 * 60_000L }) return false
        items.add(e)
        save()
        return true
    }

    @Synchronized
    fun update(e: Expense) {
        val i = items.indexOfFirst { it.id == e.id }
        if (i >= 0) {
            items[i] = e
            save()
        }
    }

    @Synchronized
    fun delete(id: Long) {
        items.removeAll { it.id == id }
        save()
    }

    @Synchronized
    fun setBudget(b: Double) {
        budget = b
        prefs.edit().putString("budget", b.toString()).apply()
    }

    @Synchronized
    fun forMonth(ym: YearMonth): List<Expense> =
        items.filter {
            YearMonth.from(Instant.ofEpochMilli(it.ts).atZone(ZoneId.systemDefault())) == ym
        }.sortedByDescending { it.ts }

    private fun save() {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("ts", it.ts).put("amount", it.amount)
                    .put("merchant", it.merchant).put("category", it.category)
                    .put("purpose", it.purpose).put("source", it.source).put("key", it.key)
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
        version++
    }
}
