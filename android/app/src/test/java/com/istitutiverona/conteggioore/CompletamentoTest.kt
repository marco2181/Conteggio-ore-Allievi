package com.istitutiverona.conteggioore

import com.istitutiverona.conteggioore.pdf.pctDi
import com.istitutiverona.conteggioore.pdf.testoCompletamento
import org.junit.Assert.assertEquals
import org.junit.Test

// Check minimo: chi supera il monte ore → "Completato +X h extra", mai negative.
class CompletamentoTest {
    @Test fun sottoMonte() = assertEquals("50%", testoCompletamento(10.0, 20.0))
    @Test fun esatto() = assertEquals("Completato", testoCompletamento(20.0, 20.0))
    @Test fun extra() = assertEquals("Completato +2,5h extra", testoCompletamento(22.5, 20.0))
    @Test fun monteZero() = assertEquals("—", testoCompletamento(5.0, 0.0))
    @Test fun pct() {
        assertEquals(0, pctDi(1.0, 0.0))
        assertEquals(110, pctDi(22.0, 20.0))
    }
}
