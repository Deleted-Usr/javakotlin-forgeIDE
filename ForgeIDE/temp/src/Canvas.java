import java.awt.*;
import javax.swing.*;

public class Canvas extends JPanel
{
	public Canvas()
	{
		addKeyListener(new KeyboardLogic());
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		setBackground(Color.WHITE);

		g.setColor(Color.RED);
		g.fillRect(100, 100, 10, 10);
	}
}