package forgeblocks

import kotlin.math.pow
import kotlin.random.Random

/** Everything the game can be doing. Exhaustive `when`s over this never miss a case. */
sealed interface State {
    data object Playing : State
    data object Paused : State
    data class GameOver(val score: Int) : State
}

/** The rules of Forge Blocks, with no knowledge of Swing or drawing. */
class Game(private val random: Random = Random(System.nanoTime())) {
    var board = Board()
        private set
    var state: State = State.Playing
        private set
    var score = 0
        private set
    var lines = 0
        private set

    /** The level rises every ten lines and speeds gravity up. */
    val level: Int
        get() = lines / 10 + 1

    /** Milliseconds between gravity steps at the current level. */
    val fallInterval: Long
        get() = (800.0 * 0.85.pow(level - 1)).toLong().coerceAtLeast(80)

    private val bag = ArrayDeque<Shape>()
    private var fallTimer = 0L

    var next: Shape = draw()
        private set
    var piece: Piece = takeNext()
        private set

    /** Where the piece would land if dropped now. */
    val ghost: Piece
        get() = generateSequence(piece) { it.moved(0, 1) }.takeWhile(board::fits).last()

    fun move(dx: Int) = whilePlaying { tryApply(piece.moved(dx, 0)) }

    /** Turns clockwise, nudging sideways if a wall or block is in the way. */
    fun rotate() = whilePlaying {
        val turned = piece.rotated()
        KICKS.any { tryApply(turned.moved(it, 0)) }
    }

    fun softDrop() = whilePlaying {
        if (tryApply(piece.moved(0, 1))) score += 1 else lock()
    }

    fun hardDrop() = whilePlaying {
        val landing = ghost
        score += 2 * (landing.origin.y - piece.origin.y)
        piece = landing
        lock()
    }

    fun togglePause() {
        state = when (state) {
            State.Playing -> State.Paused
            State.Paused -> State.Playing
            is State.GameOver -> state
        }
    }

    /** Advances gravity by [elapsedMillis]. */
    fun tick(elapsedMillis: Long) = whilePlaying {
        fallTimer += elapsedMillis
        while (fallTimer >= fallInterval && state is State.Playing) {
            fallTimer -= fallInterval
            if (!tryApply(piece.moved(0, 1))) lock()
        }
    }

    private fun tryApply(candidate: Piece): Boolean {
        if (!board.fits(candidate)) return false
        piece = candidate
        return true
    }

    private fun lock() {
        val (cleared, count) = board.locked(piece).cleared()
        board = cleared
        lines += count
        score += pointsFor(count) * level
        fallTimer = 0

        piece = takeNext()
        if (!board.fits(piece)) state = State.GameOver(score)
    }

    private fun takeNext(): Piece = Piece.spawn(next, board.width).also { next = draw() }

    /** A "seven bag": every shape once, shuffled, so droughts cannot happen. */
    private fun draw(): Shape {
        if (bag.isEmpty()) bag.addAll(Shape.entries.shuffled(random))
        return bag.removeFirst()
    }

    private inline fun whilePlaying(action: () -> Unit) {
        if (state is State.Playing) action()
    }

    private companion object {
        val KICKS = listOf(0, -1, 1, -2, 2)

        fun pointsFor(lines: Int) = when (lines) {
            0 -> 0
            1 -> 100
            2 -> 300
            3 -> 500
            else -> 800
        }
    }
}
