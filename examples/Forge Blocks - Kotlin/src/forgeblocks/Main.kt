package forgeblocks

import javax.swing.JFrame
import javax.swing.SwingUtilities

/** Application entry point. Open this file and press Run in ForgeIDE. */
fun main() {
    SwingUtilities.invokeLater {
        val game = GamePanel()

        JFrame("Forge Blocks").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            contentPane = game
            isResizable = false
            pack()
            setLocationRelativeTo(null)
            isVisible = true
        }

        game.start()
        println("Forge Blocks started. Use the arrow keys, Space, and P to play.")
    }
}
