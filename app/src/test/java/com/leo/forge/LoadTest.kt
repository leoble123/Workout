package com.leo.forge

import com.leo.forge.domain.model.Equipment
import com.leo.forge.domain.model.Load
import com.leo.forge.domain.model.Units
import org.junit.Assert.*
import org.junit.Test

class LoadTest {

    @Test
    fun `conversion round-trips`() {
        val kg = 102.5
        assertEquals(kg, Load.toKg(Load.toDisplay(kg, Units.LB), Units.LB), 0.0001)
        assertEquals(kg, Load.toKg(Load.toDisplay(kg, Units.KG), Units.KG), 0.0001)
    }

    @Test
    fun `increments are native to the unit, not converted`() {
        // 2.5 kg converts to 5.5 lb, which is not a plate anyone owns.
        assertEquals(2.5, Load.increment(Equipment.BARBELL, Units.KG), 0.001)
        assertEquals(5.0, Load.increment(Equipment.BARBELL, Units.LB), 0.001)
        assertEquals(5.0, Load.increment(Equipment.MACHINE_SELECTORIZED, Units.KG), 0.001)
        assertEquals(10.0, Load.increment(Equipment.MACHINE_SELECTORIZED, Units.LB), 0.001)
    }

    @Test
    fun `a pin stack and a plate-loaded machine step differently`() {
        assertNotEquals(
            Load.increment(Equipment.MACHINE_SELECTORIZED, Units.KG),
            Load.increment(Equipment.MACHINE_PLATE_LOADED, Units.KG),
        )
    }

    @Test
    fun `bands have no loadable step in either unit`() {
        assertEquals(0.0, Load.increment(Equipment.BANDS, Units.KG), 0.001)
        assertEquals(0.0, Load.increment(Equipment.BANDS, Units.LB), 0.001)
    }

    @Test
    fun `a stored zero increment survives the unit switch`() {
        assertEquals(0.0, Load.increment(Equipment.BARBELL, Units.LB, storedKgIncrement = 0.0), 0.001)
    }

    @Test
    fun `formatting drops a pointless trailing zero`() {
        assertEquals("60", Load.format(60.0))
        assertEquals("62.5", Load.format(62.5))
        assertEquals("102.1", Load.format(102.06))
    }

    @Test
    fun `tonnage is reported in the unit a person would say out loud`() {
        val (v, u) = Load.formatTonnage(12_000.0, Units.KG)
        assertEquals("12", v); assertEquals("t", u)
        val (v2, u2) = Load.formatTonnage(12_000.0, Units.LB)
        assertEquals("k lb", u2)
        assertTrue(v2.toDouble() > 12)  // 12 tonnes is a lot more than 12k lb
    }
}
