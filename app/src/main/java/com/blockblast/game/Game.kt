package com.blockblast.game

import kotlin.math.max
import kotlin.random.Random

/** A board cell coordinate. */
data class Cell(val row: Int, val col: Int)

/** A piece shape defined by its cells (rows go down, cols go right). */
class PieceShape(val cells: List<Cell>) {
    val rows: Int = cells.maxOf { it.row } + 1
    val cols: Int = cells.maxOf { it.col } + 1
    val size: Int = cells.size
}

/** Pool of every piece shape the game can offer (Block Blast style set). */
object PiecePool {

    private fun shape(vararg rows: String): PieceShape {
        val cells = ArrayList<Cell>()
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch ->
                if (ch == '#') cells.add(Cell(r, c))
            }
        }
        return PieceShape(cells)
    }

    val shapes: List<PieceShape> = listOf(
        // single
        shape("#"),
        // dominoes
        shape("##"),
        shape("#", "#"),
        // lines of 3 / 4 / 5
        shape("###"),
        shape("#", "#", "#"),
        shape("####"),
        shape("#", "#", "#", "#"),
        shape("#####"),
        shape("#", "#", "#", "#", "#"),
        // squares
        shape("##", "##"),
        shape("###", "###", "###"),
        // small corners (3 cells)
        shape("#-", "##"),
        shape("-#", "##"),
        shape("##", "#-"),
        shape("##", "-#"),
        // L / J tetrominoes (4 cells)
        shape("#-", "#-", "##"),
        shape("-#", "-#", "##"),
        shape("##", "#-", "#-"),
        shape("##", "-#", "-#"),
        shape("###", "#--"),
        shape("###", "--#"),
        shape("#--", "###"),
        shape("--#", "###"),
        // T tetrominoes (4 orientations)
        shape("###", "-#-"),
        shape("-#-", "###"),
        shape("-#", "##", "-#"),
        shape("#-", "##", "#-"),
        // S / Z tetrominoes
        shape("-##", "##-"),
        shape("##-", "-##"),
        shape("#-", "##", "-#"),
        shape("-#", "##", "#-"),
        // big corners (5 cells)
        shape("#-", "#-", "##"),
        shape("-#", "-#", "##"),
        shape("##", "-#", "-#"),
        shape("##", "#-", "#-"),
        shape("###", "#--", "#--"),
        shape("###", "--#", "--#"),
        shape("#--", "#--", "###"),
        shape("--#", "--#", "###"),
        // P / U-ish 5-cell pieces
        shape("##", "##", "#-"),
        shape("##", "##", "-#"),
        shape("#-", "##", "##"),
        shape("-#", "##", "##"),
        shape("#-#", "###")
    )

    fun random(rng: Random): PieceShape = shapes[rng.nextInt(shapes.size)]

    fun indexOf(shape: PieceShape): Int = shapes.indexOf(shape)
    fun byIndex(i: Int): PieceShape = shapes[((i % shapes.size) + shapes.size) % shapes.size]
}

/** Result of attempting to place a piece. */
class PlaceResult(
    val placed: Boolean,
    val clearedCells: List<Cell> = emptyList(),
    val clearedLines: Int = 0,
    val gained: Int = 0,
    val streak: Int = 0,
    val comboBonus: Int = 0
)

/**
 * Pure game state: 8x8 board, the 3 offered pieces, score and streak.
 * No Android dependencies so it can be unit tested on the JVM.
 */
class Game(val rng: Random = Random.Default) {

    val boardSize = 8
    val grid = Array(boardSize) { BooleanArray(boardSize) }

    var score = 0
        internal set
    var best = 0
    var streak = 0
        private set
    var over = false
        internal set

    /** The three offered pieces; null = empty slot (waiting for a refill). */
    val tray: Array<PieceShape?> = arrayOfNulls(3)

    init {
        refillTray()
    }

    fun reset() {
        for (r in 0 until boardSize) grid[r].fill(false)
        score = 0
        streak = 0
        over = false
        refillTray()
    }

    fun refillTray() {
        for (i in tray.indices) {
            if (tray[i] == null) tray[i] = PiecePool.random(rng)
        }
        over = !anyMovePossible()
    }

    fun canPlace(shape: PieceShape, topRow: Int, leftCol: Int): Boolean {
        if (topRow < 0 || leftCol < 0 || topRow + shape.rows > boardSize || leftCol + shape.cols > boardSize)
            return false
        for (cell in shape.cells) {
            if (grid[topRow + cell.row][leftCol + cell.col]) return false
        }
        return true
    }

    fun canPlaceAnywhere(shape: PieceShape?): Boolean {
        if (shape == null) return false
        for (r in 0..boardSize - shape.rows) {
            for (c in 0..boardSize - shape.cols) {
                if (canPlace(shape, r, c)) return true
            }
        }
        return false
    }

    fun anyMovePossible(): Boolean = tray.any { canPlaceAnywhere(it) }

    /** Place a tray piece; clears full rows/columns and computes the score. */
    fun place(slot: Int, topRow: Int, leftCol: Int): PlaceResult {
        val shape = tray[slot]
        if (over || shape == null || !canPlace(shape, topRow, leftCol)) return PlaceResult(false)

        for (cell in shape.cells) grid[topRow + cell.row][leftCol + cell.col] = true
        tray[slot] = null

        // full rows / columns
        val fullRows = (0 until boardSize).filter { r -> (0 until boardSize).all { c -> grid[r][c] } }
        val fullCols = (0 until boardSize).filter { c -> (0 until boardSize).all { r -> grid[r][c] } }

        val cleared = LinkedHashSet<Cell>()
        for (r in fullRows) for (c in 0 until boardSize) cleared.add(Cell(r, c))
        for (c in fullCols) for (r in 0 until boardSize) cleared.add(Cell(r, c))

        for (cell in cleared) grid[cell.row][cell.col] = false

        val lines = fullRows.size + fullCols.size
        var gained = shape.size
        if (lines > 0) {
            streak += 1
            gained += cleared.size * 10                       // 10 pts per cleared block
            val comboBonus = when (lines) {                   // multi-line combo
                1 -> 0
                2 -> 30
                3 -> 60
                4 -> 100
                5 -> 150
                else -> 200
            }
            val streakBonus = if (streak >= 2) (streak - 1) * 20 else 0
            gained += comboBonus + streakBonus
            score += gained
            best = max(best, score)
            return PlaceResult(true, cleared.toList(), lines, gained, streak, comboBonus + streakBonus)
        } else {
            streak = 0
            score += gained
            best = max(best, score)
            if (tray.all { it == null }) refillTray()
            over = !anyMovePossible()
            return PlaceResult(true, emptyList(), 0, gained, 0, 0)
        }
    }

    /** Called by the UI once the clear animation has finished. */
    fun afterClearAnim() {
        // like the real game, a fresh set of pieces only appears once all three are used
        if (tray.all { it == null }) refillTray()
        else over = !anyMovePossible()
    }

    // ---- persistence -----------------------------------------------------

    fun serializeGrid(): String = buildString {
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) append(if (grid[r][c]) '1' else '0')
        }
    }

    fun deserializeGrid(s: String) {
        for (r in 0 until boardSize) grid[r].fill(false)
        s.forEachIndexed { i, ch ->
            if (ch == '1') {
                val r = i / boardSize
                val c = i % boardSize
                if (r < boardSize && c < boardSize) grid[r][c] = true
            }
        }
    }

    fun serializeTray(): String = tray.joinToString(",") { it?.let { PiecePool.indexOf(it).toString() } ?: "-1" }

    fun deserializeTray(s: String) {
        val parts = s.split(",")
        for (i in 0 until 3) {
            tray[i] = parts.getOrNull(i)?.toIntOrNull()?.let { if (it >= 0) PiecePool.byIndex(it) else null }
        }
    }
}
