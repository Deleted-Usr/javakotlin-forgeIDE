package forgeblocks

import java.awt.Color

/**
 * The well that locked pieces settle into.
 *
 * A board is immutable: locking a piece or clearing rows returns a new board,
 * which keeps the rules in [Game] free of bookkeeping.
 */
class Board private constructor(
    val width: Int,
    val height: Int,
    private val rows: List<List<Color?>>
) {
    constructor(width: Int = 10, height: Int = 20) : this(width, height, List(height) { emptyRow(width) })

    /** The colour locked at [cell], or null when empty or outside the board. */
    operator fun get(cell: Cell): Color? = rows.getOrNull(cell.y)?.getOrNull(cell.x)

    /** Whether every block of [piece] is inside the walls and on an empty cell. */
    fun fits(piece: Piece): Boolean = piece.cells.all { cell ->
        cell.x in 0 until width && cell.y < height && this[cell] == null
    }

    /** A board with [piece] stamped into it. Blocks above the top row are dropped. */
    fun locked(piece: Piece): Board {
        val next = rows.map { it.toMutableList() }
        piece.cells.filter { it.y >= 0 }.forEach { next[it.y][it.x] = piece.shape.color }
        return Board(width, height, next)
    }

    /** Removes every full row and drops the rest down to fill the gap. */
    fun cleared(): Clear {
        val remaining = rows.filterNot { row -> row.all { it != null } }
        val lines = height - remaining.size
        val refilled = List(lines) { emptyRow(width) } + remaining
        return Clear(Board(width, height, refilled), lines)
    }

    /** Visits every locked block, for rendering. */
    inline fun forEachBlock(action: (Cell, Color) -> Unit) {
        for (y in 0 until height) for (x in 0 until width) {
            val cell = Cell(x, y)
            this[cell]?.let { action(cell, it) }
        }
    }

    /** The result of [cleared]: the new board and how many rows went. */
    data class Clear(val board: Board, val lines: Int)

    private companion object {
        fun emptyRow(width: Int): List<Color?> = List(width) { null }
    }
}
