package com.hoang.moneytrack

import com.hoang.moneytrack.data.FinanceRepository
import com.hoang.moneytrack.data.db.RecurUnit
import com.hoang.moneytrack.ui.common.toVnd
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class MoneyAndRecurrenceTest {

    @Test
    fun vndFormat_vi() {
        assertEquals("85.000đ", 85_000L.toVnd(Locale.forLanguageTag("vi")))
        assertEquals("2.500.000đ", 2_500_000L.toVnd(Locale.forLanguageTag("vi")))
    }

    @Test
    fun vndFormat_en() {
        assertEquals("₫85,000", 85_000L.toVnd(Locale.US))
    }

    @Test
    fun monthlyAdvance_clampsTo28() {
        val next = FinanceRepository.advance(LocalDate.of(2026, 1, 31), RecurUnit.MONTHLY, 31)
        assertEquals(LocalDate.of(2026, 2, 28), next)
    }

    @Test
    fun monthlyAdvance_keepsDay() {
        val next = FinanceRepository.advance(LocalDate.of(2026, 7, 5), RecurUnit.MONTHLY, 5)
        assertEquals(LocalDate.of(2026, 8, 5), next)
    }

    @Test
    fun weeklyAdvance() {
        val next = FinanceRepository.advance(LocalDate.of(2026, 7, 6), RecurUnit.WEEKLY, 1)
        assertEquals(LocalDate.of(2026, 7, 13), next)
    }
}
