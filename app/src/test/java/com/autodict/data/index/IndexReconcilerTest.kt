package com.autodict.data.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexReconcilerTest {

    @Test
    fun newFileIsReparsed() {
        val diff = IndexReconciler.computeDiff(cached = emptyMap(), current = mapOf("a" to 100L))
        assertEquals(listOf("a"), diff.reparseIds)
        assertTrue(diff.removeIds.isEmpty())
    }

    @Test
    fun changedMtimeIsReparsed() {
        val diff = IndexReconciler.computeDiff(cached = mapOf("a" to 100L), current = mapOf("a" to 200L))
        assertEquals(listOf("a"), diff.reparseIds)
        assertTrue(diff.removeIds.isEmpty())
    }

    @Test
    fun unchangedFileIsSkipped() {
        val diff = IndexReconciler.computeDiff(cached = mapOf("a" to 100L), current = mapOf("a" to 100L))
        assertTrue(diff.isEmpty)
    }

    @Test
    fun deletedFileIsRemoved() {
        val diff = IndexReconciler.computeDiff(
            cached = mapOf("a" to 100L, "b" to 50L),
            current = mapOf("a" to 100L),
        )
        assertEquals(listOf("b"), diff.removeIds)
        assertTrue(diff.reparseIds.isEmpty())
    }

    @Test
    fun mixedChangeIsBothReparsedAndRemoved() {
        val diff = IndexReconciler.computeDiff(
            cached = mapOf("keep" to 1L, "change" to 1L, "gone" to 1L),
            current = mapOf("keep" to 1L, "change" to 2L, "new" to 9L),
        )
        assertEquals(setOf("change", "new"), diff.reparseIds.toSet())
        assertEquals(listOf("gone"), diff.removeIds)
    }
}
