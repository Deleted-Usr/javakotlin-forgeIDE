package forgeblocks

import java.awt.Color

/** A square on the board. Immutable, so moving a piece builds a new one. */
data class Cell(val x: Int, val y: Int) {
    operator fun plus(other: Cell) = Cell(x + other.x, y + other.y)
}

/**
 * The seven tetrominoes.
 *
 * Each shape is drawn as a small text grid and rotated mathematically, so
 * adding a new shape is one entry here rather than four hand-written
 * rotation tables. Try adding a pentomino and see what happens.
 */
enum class Shape(val color: Color, pattern: String) {
    I(Color(0x4FD1E8), """
        ....
        XXXX
        ....
        ....
    """),
    O(Color(0xF7D354), """
        XX
        XX
    """),
    T(Color(0xB48CF2), """
        .X.
        XXX
        ...
    """),
    S(Color(0x7BE07B), """
        .XX
        XX.
        ...
    """),
    Z(Color(0xF06A6A), """
        XX.
        .XX
        ...
    """),
    J(Color(0x5F8DF7), """
        X..
        XXX
        ...
    """),
    L(Color(0xF7A54B), """
        ..X
        XXX
        ...
    """);

    private val grid = pattern.trimIndent().lines()

    /** Width and height of the square the pattern is drawn in. */
    val gridSize = grid.size

    /** The four orientations, clockwise, starting from the pattern as drawn. */
    val rotations: List<List<Cell>> = run {
        val base = grid.flatMapIndexed { y, row ->
            row.mapIndexedNotNull { x, char -> if (char == 'X') Cell(x, y) else null }
        }
        // Rotating a square grid clockwise maps (x, y) to (size - 1 - y, x).
        generateSequence(base) { cells -> cells.map { Cell(gridSize - 1 - it.y, it.x) } }
            .take(4)
            .toList()
    }
}

/** A shape at a position on the board. Every move or turn is a [copy]. */
data class Piece(val shape: Shape, val origin: Cell, val rotation: Int = 0) {
    val cells: List<Cell>
        get() = shape.rotations[rotation].map { it + origin }

    fun moved(dx: Int, dy: Int) = copy(origin = origin + Cell(dx, dy))

    fun rotated() = copy(rotation = (rotation + 1) % shape.rotations.size)

    companion object {
        /** Centres the shape horizontally with its topmost block on row 0. */
        fun spawn(shape: Shape, boardWidth: Int): Piece {
            val x = (boardWidth - shape.gridSize) / 2
            val y = -shape.rotations.first().minOf { it.y }
            return Piece(shape, Cell(x, y))
        }
    }
}
