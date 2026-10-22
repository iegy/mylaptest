package net.iegy.hisn.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabsModelTest {
    private fun tab(id: String, url: String = "https://$id.com", priv: Boolean = false, parent: String? = null) =
        TabState(id = id, url = url, isPrivate = priv, parentId = parent)

    @Test fun addSelectsTheNewestTab() {
        val m = TabsModel().add(tab("a")).add(tab("b")).add(tab("c"))
        assertEquals("c", m.selectedId)
        assertEquals(3, m.tabs.size)
    }
    @Test fun addWithoutSelectKeepsSelection() {
        val m = TabsModel().add(tab("a")).add(tab("b"), select = false)
        assertEquals("a", m.selectedId)
    }
    @Test fun closingTheSelectedTabSelectsItsRightNeighbour() {
        val m = TabsModel().add(tab("a")).add(tab("b")).add(tab("c")).select("b").close("b")
        assertEquals("c", m.selectedId)
        assertEquals(listOf("a", "c"), m.tabs.map { it.id })
        assertEquals(listOf("b"), m.closed.map { it.id })
    }
    @Test fun closingAChildReturnsToItsOpener() {
        val m = TabsModel().add(tab("a")).add(tab("b")).select("a").add(tab("d", parent = "a")).close("d")
        assertEquals("a", m.selectedId)
    }
    @Test fun closingAnUnselectedTabKeepsSelection() {
        val m = TabsModel().add(tab("a")).add(tab("b")).close("a")
        assertEquals("b", m.selectedId)
    }
    @Test fun closingUnknownTabIsANoOp() {
        val m = TabsModel().add(tab("a"))
        assertEquals(m, m.close("zzz"))
    }
    @Test fun reopenReturnsNewestClosedFirst() {
        val m = TabsModel().add(tab("a")).add(tab("b")).close("a").close("b")
        val (next, closed) = m.popClosed()
        assertEquals("b", closed?.id)
        assertEquals(1, next.closed.size)
    }
    @Test fun closeOthersKeepsOnlyTheChosenTab() {
        val m = TabsModel().add(tab("a")).add(tab("b")).add(tab("c")).closeOthers("b")
        assertEquals(listOf("b"), m.tabs.map { it.id })
        assertEquals("b", m.selectedId)
        assertEquals(2, m.closed.size)
    }
    @Test fun closeAllEmptiesTheModel() {
        val m = TabsModel().add(tab("a")).add(tab("b")).closeAll()
        assertTrue(m.tabs.isEmpty())
        assertNull(m.selectedId)
    }
    @Test fun privateTabsAreNeverRemembered() {
        val m = TabsModel().add(tab("p", priv = true)).close("p")
        assertTrue(m.closed.isEmpty())
        assertTrue(TabsModel().add(tab("q", priv = true)).closeAll().closed.isEmpty())
    }
    @Test fun startPageTabsAreNotRemembered() {
        assertTrue(TabsModel().add(TabState("h")).close("h").closed.isEmpty())
    }
    @Test fun closedHistoryIsCapped() {
        var m = TabsModel()
        for (i in 1..15) m = m.add(tab("t$i")).close("t$i")
        assertEquals(TabsModel.MAX_CLOSED, m.closed.size)
    }
}
