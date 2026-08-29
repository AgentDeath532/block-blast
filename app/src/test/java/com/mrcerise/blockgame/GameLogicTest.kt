package com.mrcerise.blockgame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameLogicTest {

    @Test
    fun emptyBoardAllowsSinglePlacement() {
        val g = Game(Random(7))
        val single = PieceShape(listOf(Cell(0, 0)))
        for (r in 0..7) for (c in 0..7) {
            assertTrue(g.canPlace(single, r, c))
        }
        // out of bounds rejected
        assertFalse(g.canPlace(single, 8, 0))
        assertFalse(g.canPlace(single, 0, 8))
    }

    @Test
    fun horizontalLineClearsAndScores() {
        val g = Game(Random(1))
        // fill bottom row except first two cells, then place a horizontal domino there
        for (c in 2..7) g.grid[7][c] = true
        val domino = PieceShape(listOf(Cell(0, 0), Cell(0, 1)))
        val slot = forceTray(g, domino)
        val result = g.place(slot, 7, 0)
        assertTrue(result.placed)
        assertEquals(1, result.clearedLines)
        assertEquals(8, result.clearedCells.size)
        // row fully cleared
        for (c in 0..7) assertFalse(g.grid[7][c])
        // score: 2 placed + 8*10 = 82
        assertEquals(82, result.gained)
        assertEquals(82, g.score)
        assertEquals(1, g.streak)
    }

    @Test
    fun crossClearGivesComboBonus() {
        val g = Game(Random(2))
        // fill row 7 and column 7 completely except their intersection (7,7) empty;
        // place a single at (7,7) -> clears row + column = 2 lines, 15 cells
        for (c in 0..6) g.grid[7][c] = true
        for (r in 0..6) g.grid[r][7] = true
        val single = PieceShape(listOf(Cell(0, 0)))
        val slot = forceTray(g, single)
        val result = g.place(slot, 7, 7)
        assertTrue(result.placed)
        assertEquals(2, result.clearedLines)
        assertEquals(15, result.clearedCells.size)
        // 1 placed + 15*10 + 30 combo = 181
        assertEquals(181, result.gained)
        assertEquals(2, result.clearedLines)
        for (i in 0..7) {
            assertFalse(g.grid[7][i])
            assertFalse(g.grid[i][7])
        }
    }

    @Test
    fun noClearResetsStreak() {
        val g = Game(Random(3))
        val single = PieceShape(listOf(Cell(0, 0)))
        // clear a line once
        for (c in 1..7) g.grid[7][c] = true
        val s1 = forceTray(g, single)
        g.place(s1, 7, 0)
        assertEquals(1, g.streak)
        // next move clears nothing
        val s2 = forceTray(g, single)
        val r2 = g.place(s2, 0, 0)
        assertEquals(0, r2.streak)
        assertEquals(0, g.streak)
        assertEquals(1, r2.gained)
    }

    @Test
    fun blockedPlacementIsRejected() {
        val g = Game(Random(4))
        g.grid[0][0] = true
        val single = PieceShape(listOf(Cell(0, 0)))
        assertFalse(g.canPlace(single, 0, 0))
        val slot = forceTray(g, single)
        val result = g.place(slot, 0, 0)
        assertFalse(result.placed)
        // piece stays in tray
        assertTrue(g.tray[slot] != null)
    }

    @Test
    fun fullBoardIsGameOver() {
        val g = Game(Random(5))
        for (r in 0..7) for (c in 0..7) g.grid[r][c] = true
        // carve a 1-cell hole: even the single piece can fill it, so not over yet
        g.grid[4][4] = false
        g.tray[0] = PieceShape(listOf(Cell(0, 0)))
        g.tray[1] = PieceShape(listOf(Cell(0, 0)))
        g.tray[2] = PieceShape(listOf(Cell(0, 0)))
        assertTrue(g.anyMovePossible())
        // fill the hole
        g.grid[4][4] = true
        assertFalse(g.anyMovePossible())
    }

    @Test
    fun serializationRoundTrip() {
        val g = Game(Random(11))
        g.grid[0][0] = true
        g.grid[3][5] = true
        val s = g.serializeGrid()
        val g2 = Game(Random(12))
        g2.deserializeGrid(s)
        assertTrue(g2.grid[0][0])
        assertTrue(g2.grid[3][5])
        assertFalse(g2.grid[1][1])

        val t = g.serializeTray()
        g2.deserializeTray(t)
        assertEquals(g.tray.map { it?.cells }, g2.tray.map { it?.cells })
    }

    @Test
    fun everyPoolShapeIsWellFormed() {
        for (shape in PiecePool.shapes) {
            assertTrue(shape.size in 1..9)
            assertTrue(shape.rows in 1..5)
            assertTrue(shape.cols in 1..5)
            // fits on an empty board somewhere
            val g = Game(Random(9))
            var fits = false
            for (r in 0..8 - shape.rows) for (c in 0..8 - shape.cols) {
                if (g.canPlace(shape, r, c)) fits = true
            }
            assertTrue("shape ${shape.cells} should fit empty board", fits)
        }
        // matches the 3 pieces shown in the reference screenshot:
        // T-left, 3x3 square, S/Z tetromino
        val tLeft = PieceShape(listOf(Cell(0,1), Cell(1,0), Cell(1,1), Cell(2,1)))
        assertTrue(PiecePool.shapes.any { it.cells.toSet() == tLeft.cells.toSet() })
        val square = PieceShape(listOf(
            Cell(0,0), Cell(0,1), Cell(0,2),
            Cell(1,0), Cell(1,1), Cell(1,2),
            Cell(2,0), Cell(2,1), Cell(2,2)))
        assertTrue(PiecePool.shapes.any { it.cells.toSet() == square.cells.toSet() })
    }

    /** Put a specific shape into a free tray slot and return that slot index. */
    private fun forceTray(g: Game, shape: PieceShape): Int {
        for (i in g.tray.indices) {
            g.tray[i] = shape
            return i
        }
        return -1
    }
}
