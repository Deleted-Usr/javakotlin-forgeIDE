import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class KeyboardLogic implements KeyListener
{
	public static JLabel label = new JLabel("Press any key...", JLabel.CENTER);

	@Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        
        // Handle action keys (arrows, function keys, etc.)
        if (keyCode == KeyEvent.VK_UP) {
            label.setText("Pressed: UP ARROW");
        } else if (keyCode == KeyEvent.VK_DOWN) {
            label.setText("Pressed: DOWN ARROW");
        } else {
            label.setText("Pressed key code: " + keyCode);
        }
    }

    // 2. Invoked when a key is released
    @Override
    public void keyReleased(KeyEvent e) {
        label.setText("Released: " + KeyEvent.getKeyText(e.getKeyCode()));
    }

    // 3. Invoked when a character is typed (press + release)
    @Override
    public void keyTyped(KeyEvent e) {
        char keyChar = e.getKeyChar();
        
        // Prints to console for text input tracking
        System.out.println("Character typed: " + keyChar);
    }
}