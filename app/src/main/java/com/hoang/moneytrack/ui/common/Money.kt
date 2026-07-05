package com.hoang.moneytrack.ui.common

import java.text.NumberFormat
import java.util.Locale

/** Single money formatter for the whole app. VI: "85.000đ", EN: "₫85,000". Money is Long (đồng). */
fun Long.toVnd(locale: Locale = Locale.getDefault()): String {
    val grouped = NumberFormat.getIntegerInstance(locale).format(this)
    return if (locale.language == "vi") "${grouped}đ" else "₫$grouped"
}

/** Signed variant for txn rows: +85.000đ / -85.000đ. */
fun Long.toVndSigned(positive: Boolean, locale: Locale = Locale.getDefault()): String =
    (if (positive) "+" else "-") + toVnd(locale)
