package com.razal.expenses

import android.content.Context
import android.provider.Telephony

object SmsParser {
    private val amountRx = Regex(
        """(?:AED|DHS?)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:AED|DHS?)\b""",
        RegexOption.IGNORE_CASE
    )
    private const val STOP = """\s+(?:on|using|via|with|avl|available|bal|balance|ending|card|from|ref)\b|[.,;]|$"""
    private val atRx = Regex("""(?:\bat\b|@)\s+(.+?)(?=$STOP)""", RegexOption.IGNORE_CASE)
    private val toRx = Regex("""\bto\s+(.+?)(?=$STOP)""", RegexOption.IGNORE_CASE)

    private val skip = listOf(
        "otp", "one time", "one-time", "verification", "credited", "salary",
        "refund", "received", "deposit", "declined", "failed", "reversed"
    )
    private val debit = listOf(
        "purchase", "spent", "debited", "withdraw", "paid", "payment",
        "used for", "transaction", "transfer"
    )

    private val rules = listOf(
        "Food" to listOf("talabat", "deliveroo", "noon food", "restaurant", "cafe", "coffee", "starbucks", "mcdonald", "kfc", "burger", "pizza", "shawarma", "bakery", "kitchen"),
        "Groceries" to listOf("carrefour", "lulu", "spinneys", "union coop", "choithrams", "waitrose", "grocer", "supermarket", "hypermarket", "al maya"),
        "Fuel" to listOf("adnoc", "enoc", "eppco", "emarat", "petrol", "fuel"),
        "Transport" to listOf("careem", "uber", "salik", "darb", "rta dubai", "taxi", "parking", "mawaqif", "nol card"),
        "Bills" to listOf("dewa", "addc", "aadc", "etisalat", "telecom", "utility", "insurance", "ejari", "tawtheeq"),
        "Shopping" to listOf("amazon", "noon", "shein", "namshi", "ikea", "zara", "mall", "centrepoint"),
        "Health" to listOf("pharmacy", "clinic", "hospital", "medical", "aster", "dental"),
        "Entertainment" to listOf("netflix", "spotify", "cinema", "vox", "anghami", "google play", "youtube")
    )

    private fun guess(merchant: String): String {
        val m = merchant.lowercase()
        return rules.firstOrNull { (_, words) -> words.any { m.contains(it) } }?.first ?: "Uncategorized"
    }

    fun toExpense(body: String, sender: String, ts: Long): Expense? {
        val low = body.lowercase()
        if (skip.any { low.contains(it) }) return null
        if (debit.none { low.contains(it) }) return null
        val m = amountRx.find(body) ?: return null
        val amt = (m.groups[1]?.value ?: m.groups[2]?.value)
            ?.replace(",", "")?.toDoubleOrNull() ?: return null
        if (amt <= 0) return null
        val merchant = (atRx.find(body)?.groupValues?.get(1)
            ?: toRx.find(body)?.groupValues?.get(1)
            ?: "Unknown").trim().take(40)
        return Expense(Store.newId(), ts, amt, merchant, guess(merchant), "", "sms", body.hashCode().toString())
    }

    fun importInbox(ctx: Context, days: Int): Int {
        Store.init(ctx)
        val since = System.currentTimeMillis() - days * 86_400_000L
        var n = 0
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf("address", "body", "date"),
            "date > ?", arrayOf(since.toString()), "date ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val e = toExpense(c.getString(1) ?: "", c.getString(0) ?: "", c.getLong(2))
                if (e != null && Store.add(e)) n++
            }
        }
        return n
    }
}
