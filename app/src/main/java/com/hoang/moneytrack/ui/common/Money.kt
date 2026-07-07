package com.hoang.moneytrack.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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

/**
 * Live thousand-separator formatting for money input fields: types "2000000", shows "2.000.000".
 * Backing state stays digits-only (Long-parseable) — only the rendered text gets dots inserted.
 */
object ThousandsVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val transformed = StringBuilder()
        val original2Transformed = IntArray(original.length + 1)
        for (i in original.indices) {
            if (i != 0 && (original.length - i) % 3 == 0) transformed.append('.')
            original2Transformed[i] = transformed.length
            transformed.append(original[i])
        }
        original2Transformed[original.length] = transformed.length

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                original2Transformed[offset.coerceIn(0, original.length)]

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, transformed.length)
                val dots = transformed.substring(0, clamped).count { it == '.' }
                return (clamped - dots).coerceIn(0, original.length)
            }
        }
        return TransformedText(AnnotatedString(transformed.toString()), offsetMapping)
    }
}
