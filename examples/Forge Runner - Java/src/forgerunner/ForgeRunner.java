package forgerunner;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;

/** Application entry point. Open this file and press Run in ForgeIDE. */
public final class ForgeRunner
{
    private ForgeRunner() { }

    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(ForgeRunner::showGame);
    }

    private static void showGame()
    {
        JFrame frame = new JFrame("Forge Runner");
        GamePanel game = new GamePanel();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(game);
        frame.pack();
        frame.setMinimumSize(new Dimension(720, 440));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        game.start();
        System.out.println("Forge Runner started. Use A/D, Space, and Shift to play.");
    }
}
