package forgeblocks

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.Timer

/** Keyboard input, the frame timer, and Java2D rendering for a [Game]. */
class GamePanel : JPanel() {
    private var game = Game()
    private var previousNanos = System.nanoTime()
    private val timer = Timer(16) { tick() }

    init {
        preferredSize = Dimension(WIDTH, HEIGHT)
        background = BACKGROUND
        installControls()
    }

    fun start() {
        previousNanos = System.nanoTime()
        timer.start()
    }

    private fun installControls() {
        bind("LEFT", "A") { game.move(-1) }
        bind("RIGHT", "D") { game.move(1) }
        bind("DOWN", "S") { game.softDrop() }
        bind("UP", "W", "X") { game.rotate() }
        bind("SPACE") { game.hardDrop() }
        bind("P") { game.togglePause() }
        bind("R") { game = Game() }
    }

    /** Runs [action] when any of [keys] is pressed, whether or not the panel has focus. */
    private fun bind(vararg keys: String, action: () -> Unit) {
        val inputs = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        for (key in keys) {
            inputs.put(KeyStroke.getKeyStroke(key), key)
            actionMap.put(key, object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent) = action()
            })
        }
    }

    private fun tick() {
        val now = System.nanoTime()
        val elapsedMillis = ((now - previousNanos) / 1_000_000).coerceAtMost(100)
        previousNanos = now

        game.tick(elapsedMillis)
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.drawWell()
        game.board.forEachBlock { cell, color -> g.drawBlock(cell, color) }
        g.drawGhost(game.ghost)
        game.piece.cells.forEach { g.drawBlock(it, game.piece.shape.color) }
        g.drawSidebar()
        g.drawOverlay()
    }

    private fun Graphics2D.drawWell() {
        val wellWidth = game.board.width * CELL
        val wellHeight = game.board.height * CELL

        color = WELL
        fillRect(WELL_X, WELL_Y, wellWidth, wellHeight)

        color = GRID
        for (x in 0..game.board.width) drawLine(WELL_X + x * CELL, WELL_Y, WELL_X + x * CELL, WELL_Y + wellHeight)
        for (y in 0..game.board.height) drawLine(WELL_X, WELL_Y + y * CELL, WELL_X + wellWidth, WELL_Y + y * CELL)

        color = FRAME
        stroke = BasicStroke(3f)
        drawRect(WELL_X - 2, WELL_Y - 2, wellWidth + 4, wellHeight + 4)
    }

    /** A bevelled block at a board cell, or anywhere when [originX]/[originY] are given. */
    private fun Graphics2D.drawBlock(cell: Cell, fill: Color, originX: Int = WELL_X, originY: Int = WELL_Y) {
        if (cell.y < 0) return
        val x = originX + cell.x * CELL
        val y = originY + cell.y * CELL

        color = fill
        fillRect(x + 1, y + 1, CELL - 2, CELL - 2)
        color = fill.brighter()
        fillRect(x + 1, y + 1, CELL - 2, 3)
        fillRect(x + 1, y + 1, 3, CELL - 2)
        color = fill.darker()
        fillRect(x + 1, y + CELL - 4, CELL - 2, 3)
        fillRect(x + CELL - 4, y + 1, 3, CELL - 2)
    }

    private fun Graphics2D.drawGhost(ghost: Piece) {
        val previous = composite
        composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f)
        ghost.cells.forEach { drawBlock(it, ghost.shape.color) }
        composite = previous
    }

    private fun Graphics2D.drawSidebar() {
        val x = WELL_X + game.board.width * CELL + MARGIN

        font = TITLE_FONT
        color = TEXT
        drawString("FORGE", x, WELL_Y + 28)
        drawString("BLOCKS", x, WELL_Y + 56)

        font = LABEL_FONT
        color = MUTED
        drawString("NEXT", x, WELL_Y + 100)

        val previewY = WELL_Y + 110
        color = WELL
        fillRect(x, previewY, 4 * CELL, 4 * CELL)
        val next = game.next
        val previewX = x + (4 - next.gridSize) * CELL / 2
        next.rotations.first().forEach { drawBlock(it, next.color, previewX, previewY) }

        val stats = listOf("SCORE" to game.score, "LEVEL" to game.level, "LINES" to game.lines)
        stats.forEachIndexed { index, (label, value) ->
            val y = WELL_Y + 270 + index * 64
            font = LABEL_FONT
            color = MUTED
            drawString(label, x, y)
            font = VALUE_FONT
            color = TEXT
            drawString(value.toString(), x, y + 28)
        }

        font = HINT_FONT
        color = MUTED
        listOf("Arrows / WASD move", "Up rotates", "Space drops", "P pauses", "R restarts")
            .forEachIndexed { index, hint -> drawString(hint, x, HEIGHT - 110 + index * 18) }
    }

    private fun Graphics2D.drawOverlay() {
        val (title, detail) = when (val state = game.state) {
            State.Playing -> return
            State.Paused -> "PAUSED" to "Press P to resume"
            is State.GameOver -> "GAME OVER" to "Score ${state.score} - press R to restart"
        }

        color = Color(0, 0, 0, 170)
        fillRect(WELL_X, WELL_Y, game.board.width * CELL, game.board.height * CELL)

        val centreX = WELL_X + game.board.width * CELL / 2
        val centreY = WELL_Y + game.board.height * CELL / 2
        font = TITLE_FONT
        color = TEXT
        drawCentred(title, centreX, centreY)
        font = HINT_FONT
        color = MUTED
        drawCentred(detail, centreX, centreY + 28)
    }

    private fun Graphics2D.drawCentred(text: String, centreX: Int, baseline: Int) {
        drawString(text, centreX - fontMetrics.stringWidth(text) / 2, baseline)
    }

    private companion object {
        const val CELL = 30
        const val MARGIN = 24
        const val WELL_X = MARGIN
        const val WELL_Y = MARGIN
        const val WIDTH = MARGIN + 10 * CELL + MARGIN + 4 * CELL + MARGIN + 40
        const val HEIGHT = MARGIN + 20 * CELL + MARGIN

        val BACKGROUND = Color(12, 16, 35)
        val WELL = Color(20, 25, 48)
        val GRID = Color(32, 38, 66)
        val FRAME = Color(90, 100, 150)
        val TEXT = Color(235, 238, 250)
        val MUTED = Color(140, 148, 185)

        val TITLE_FONT = Font(Font.SANS_SERIF, Font.BOLD, 26)
        val VALUE_FONT = Font(Font.MONOSPACED, Font.BOLD, 24)
        val LABEL_FONT = Font(Font.SANS_SERIF, Font.BOLD, 13)
        val HINT_FONT = Font(Font.SANS_SERIF, Font.PLAIN, 13)
    }
}
