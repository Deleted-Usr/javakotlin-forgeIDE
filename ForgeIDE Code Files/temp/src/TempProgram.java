import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

// Small top down shooter made within the ForgeIDE

public class TempProgram extends JPanel implements ActionListener, KeyListener, MouseListener, MouseMotionListener {
    
    // Window settings
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private Timer timer;

    // Player variables
    private double playerX = 400;
    private double playerY = 300;
    private double playerAngle = 0;
    private int playerSpeed = 4;
    private boolean moveUp, moveDown, moveLeft, moveRight;

    // Game states
    private ArrayList<Bullet> bullets = new ArrayList<>();
    private ArrayList<Enemy> enemies = new ArrayList<>();
    private int score = 0;
    private int spawnCounter = 0;
    private Point mousePoint = new Point(0, 0);

    public TempProgram() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.DARK_GRAY);
        setFocusable(true);
        
        // Listeners
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        // Game Loop (~60 FPS)
        timer = new Timer(16, this);
        timer.start();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("IDE Test Game - Top Down Shooter");
        TempProgram game = new TempProgram();
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw Bullets
        g2d.setColor(Color.YELLOW);
        for (Bullet b : bullets) {
            g2d.fillOval((int) b.x - 4, (int) b.y - 4, 8, 8);
        }

        // Draw Enemies
        g2d.setColor(Color.RED);
        for (Enemy e : enemies) {
            g2d.fillOval((int) e.x - 15, (int) e.y - 15, 30, 30);
        }

        // Draw Player (with rotation mapping)
        g2d.setColor(Color.CYAN);
        g2d.translate(playerX, playerY);
        g2d.rotate(playerAngle);
        
        // Draw body and a small turret facing forward
        g2d.fillOval(-20, -20, 40, 40);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, -6, 25, 12); 
        
        // Reset transformations for HUD
        g2d.rotate(-playerAngle);
        g2d.translate(-playerX, -playerY);

        // Draw Score HUD
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.drawString("SCORE: " + score, 20, 40);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // 1. Handle Movement Mechanics
        if (moveUp) playerY -= playerSpeed;
        if (moveDown) playerY += playerSpeed;
        if (moveLeft) playerX -= playerSpeed;
        if (moveRight) playerX += playerSpeed;

        // Keep player on screen bounds
        playerX = Math.max(20, Math.min(WIDTH - 20, playerX));
        playerY = Math.max(20, Math.min(HEIGHT - 20, playerY));

        // 2. Calculate Look Rotation Angle
        playerAngle = Math.atan2(mousePoint.y - playerY, mousePoint.x - playerX);

        // 3. Update Bullets
        Iterator<Bullet> bulletIt = bullets.iterator();
        while (bulletIt.hasNext()) {
            Bullet b = bulletIt.next();
            b.update();
            if (b.x < 0 || b.x > WIDTH || b.y < 0 || b.y > HEIGHT) {
                bulletIt.remove();
            }
        }

        // 4. Spawn Enemies
        spawnCounter++;
        if (spawnCounter % 50 == 0) {
            enemies.add(new Enemy(WIDTH, HEIGHT, playerX, playerY));
        }

        // 5. Update Enemies & Check Bullet Collisions
        Iterator<Enemy> enemyIt = enemies.iterator();
        while (enemyIt.hasNext()) {
            Enemy enemy = enemyIt.next();
            enemy.update(playerX, playerY);

            // Bullet hit registration
            Iterator<Bullet> bIt = bullets.iterator();
            boolean hit = false;
            while (bIt.hasNext()) {
                Bullet b = bIt.next();
                double dist = Math.hypot(enemy.x - b.x, enemy.y - b.y);
                if (dist < 19) { // Enemy radius (15) + bullet radius (4)
                    bIt.remove();
                    hit = true;
                    break;
                }
            }

            if (hit) {
                enemyIt.remove();
                score += 10;
            }
        }

        repaint();
    }

    // Input Handling
    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W: moveUp = true; break;
            case KeyEvent.VK_S: moveDown = true; break;
            case KeyEvent.VK_A: moveLeft = true; break;
            case KeyEvent.VK_D: moveRight = true; break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W: moveUp = false; break;
            case KeyEvent.VK_S: moveDown = false; break;
            case KeyEvent.VK_A: moveLeft = false; break;
            case KeyEvent.VK_D: moveRight = false; break;
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            // Shoot a bullet moving towards the active cursor angle
            bullets.add(new Bullet(playerX, playerY, playerAngle));
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mousePoint = e.getPoint();
    }

    @Override public void mouseDragged(MouseEvent e) { mousePoint = e.getPoint(); }
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    // Inner Helper Classes for Entity Management
    private static class Bullet {
        double x, y, dx, dy;
        Bullet(double startX, double startY, double angle) {
            this.x = startX;
            this.y = startY;
            int speed = 8;
            this.dx = Math.cos(angle) * speed;
            this.dy = Math.sin(angle) * speed;
        }
        void update() {
            x += dx;
            y += dy;
        }
    }

    private static class Enemy {
        double x, y;
        double speed = 1.5;
        Enemy(int scrWidth, int scrHeight, double pX, double pY) {
            Random r = new Random();
            // Spawn randomly along the screen edges to surprise the player
            if (r.nextBoolean()) {
                this.x = r.nextBoolean() ? -20 : scrWidth + 20;
                this.y = r.nextInt(scrHeight);
            } else {
                this.x = r.nextInt(scrWidth);
                this.y = r.nextBoolean() ? -20 : scrHeight + 20;
            }
        }
        void update(double pX, double pY) {
            double angle = Math.atan2(pY - y, pX - x);
            x += Math.cos(angle) * speed;
            y += Math.sin(angle) * speed;
        }
    }
}
