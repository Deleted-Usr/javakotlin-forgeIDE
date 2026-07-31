import javax.swing.*;

public class TempProgram
{
    public static void main(String[] args)
    {
        System.out.println("Hello, World!");

		JFrame f = new JFrame("My Window!");
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		f.setSize(800, 600);
		f.setVisible(true);
    }
}
