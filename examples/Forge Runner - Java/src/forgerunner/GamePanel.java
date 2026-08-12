package forgerunner;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Fixed-step game loop, keyboard input, collision events, and Java2D rendering. */
@SuppressWarnings("serial")
final class GamePanel extends JPanel
{
    private static final int VIEW_WIDTH = 960;
    private static final int VIEW_HEIGHT = 540;
    private static final double STEP = 1.0 / 120.0;
    private static final double SPAWN_X = 110;
    private static final double SPAWN_Y = Level.FLOOR_Y - Player.HEIGHT;
    private static final double FINISH_X = 6_500;

    private enum State { PLAYING, PAUSED, WON }

    private final Set<Integer> heldKeys = new HashSet<>();
    private final Set<Integer> pressedKeys = new HashSet<>();
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random(7);
    private final Timer timer = new Timer(8, this::tick);

    private Level level = Level.create();
    private Player player = new Player(SPAWN_X, SPAWN_Y);
    private State state = State.PLAYING;
    private long previousNanos;
    private double accumulator;
    private double elapsed;
    private double cameraX;
    private int score;
    private int deaths;

    GamePanel()
    {
        setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
        setBackground(new Color(12, 16, 35));
        setFocusable(true);
        installControls();
    }

    void start()
    {
        previousNanos = System.nanoTime();
        timer.start();
        requestFocusInWindow();
    }

    private void installControls()
    {
        int[] keys = {
                KeyEvent.VK_A, KeyEvent.VK_D, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                KeyEvent.VK_W, KeyEvent.VK_UP, KeyEvent.VK_SPACE,
                KeyEvent.VK_SHIFT, KeyEvent.VK_P, KeyEvent.VK_R
        };

        InputMap inputs = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actions = getActionMap();
        for (int key : keys)
        {
            String down = "key-down-" + key;
            String up = "key-up-" + key;

            // Modifier keys include their own modifier bit in Swing's KeyStroke.
            // Register both forms so Shift itself triggers the dash, and so the
            // other controls continue working while Shift is held.
            bind(inputs, key, 0, false, down);
            bind(inputs, key, 0, true, up);
            bind(inputs, key, InputEvent.SHIFT_DOWN_MASK, false, down);
            bind(inputs, key, InputEvent.SHIFT_DOWN_MASK, true, up);
            actions.put(down, new KeyAction(key, true));
            actions.put(up, new KeyAction(key, false));
        }
    }

    private static void bind(InputMap inputs, int key, int modifiers, boolean released, String action)
    {
        inputs.put(KeyStroke.getKeyStroke(key, modifiers, released), action);
    }

    private void tick(ActionEvent ignored)
    {
        long now = System.nanoTime();
        double frameTime = Math.min(0.05, (now - previousNanos) / 1_000_000_000.0);
        previousNanos = now;
        accumulator += frameTime;

        while (accumulator >= STEP)
        {
            updateGame(STEP);
            accumulator -= STEP;
        }
        repaint();
    }

    private void updateGame(double seconds)
    {
        elapsed += seconds;

        if (consume(KeyEvent.VK_R))
        {
            restart();
            return;
        }
        if (consume(KeyEvent.VK_P) && state != State.WON)
        {
            state = state == State.PAUSED ? State.PLAYING : State.PAUSED;
        }
        if (state != State.PLAYING) return;

        boolean left = held(KeyEvent.VK_A, KeyEvent.VK_LEFT);
        boolean right = held(KeyEvent.VK_D, KeyEvent.VK_RIGHT);
        boolean jumpHeld = held(KeyEvent.VK_W, KeyEvent.VK_UP, KeyEvent.VK_SPACE);
        boolean jumpPressed = consume(KeyEvent.VK_W, KeyEvent.VK_UP, KeyEvent.VK_SPACE);
        boolean dashPressed = consume(KeyEvent.VK_SHIFT);

        player.update(seconds, left, right, jumpPressed, jumpHeld, dashPressed, level);
        updateCoins();
        updateEnemies(seconds);
        updateParticles(seconds);

        if (player.y() > VIEW_HEIGHT + 100) respawn();
        if (player.x() + Player.WIDTH >= FINISH_X)
        {
            state = State.WON;
            burst(FINISH_X + 20, 270, new Color(255, 180, 70), 60);
            System.out.println("Course complete: " + score + " sparks, " + deaths + " falls.");
        }

        double targetCamera = player.x() - VIEW_WIDTH * 0.38;
        targetCamera = Math.max(0, Math.min(Level.WORLD_WIDTH - VIEW_WIDTH, targetCamera));
        cameraX += (targetCamera - cameraX) * Math.min(1, seconds * 6.5);
    }

    private void updateCoins()
    {
        Rectangle2D.Double playerBounds = player.bounds();
        for (Level.Coin coin : level.coins())
        {
            if (!coin.collected && playerBounds.intersects(coin.bounds()))
            {
                coin.collected = true;
                score++;
                burst(coin.x, coin.y, new Color(255, 205, 64), 10);
            }
        }
    }

    private void updateEnemies(double seconds)
    {
        for (Level.Enemy enemy : level.enemies())
        {
            enemy.update(seconds);
            if (!enemy.alive || !player.bounds().intersects(enemy.bounds())) continue;

            boolean stomped = player.velocityY() > 0
                    && player.y() + Player.HEIGHT < enemy.y + Level.Enemy.HEIGHT * 0.65;
            if (stomped)
            {
                enemy.alive = false;
                player.bounce();
                score += 3;
                burst(enemy.x + Level.Enemy.WIDTH / 2, enemy.y + 8, new Color(111, 245, 170), 16);
            }
            else
            {
                respawn();
                return;
            }
        }
    }

    private void updateParticles(double seconds)
    {
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext())
        {
            Particle particle = iterator.next();
            particle.life -= seconds;
            if (particle.life <= 0)
            {
                iterator.remove();
                continue;
            }
            particle.x += particle.velocityX * seconds;
            particle.y += particle.velocityY * seconds;
            particle.velocityY += 360 * seconds;
        }
    }

    private void burst(double x, double y, Color color, int amount)
    {
        for (int index = 0; index < amount; index++)
        {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 55 + random.nextDouble() * 180;
            particles.add(new Particle(
                    x, y, Math.cos(angle) * speed, Math.sin(angle) * speed,
                    0.35 + random.nextDouble() * 0.45, 3 + random.nextDouble() * 4, color
            ));
        }
    }

    private void respawn()
    {
        deaths++;
        burst(player.x() + Player.WIDTH / 2, Math.min(player.y(), 500), new Color(255, 92, 116), 20);
        player.respawn(SPAWN_X, SPAWN_Y);
        cameraX = 0;
    }

    private void restart()
    {
        level = Level.create();
        player = new Player(SPAWN_X, SPAWN_Y);
        particles.clear();
        state = State.PLAYING;
        score = 0;
        deaths = 0;
        elapsed = 0;
        cameraX = 0;
        pressedKeys.clear();
    }

    private boolean held(int... keys)
    {
        for (int key : keys) if (heldKeys.contains(key)) return true;
        return false;
    }

    private boolean consume(int... keys)
    {
        boolean found = false;
        for (int key : keys) found |= pressedKeys.remove(key);
        return found;
    }

    @Override
    protected void paintComponent(Graphics graphics)
    {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double scale = Math.min(getWidth() / (double) VIEW_WIDTH, getHeight() / (double) VIEW_HEIGHT);
        double offsetX = (getWidth() - VIEW_WIDTH * scale) / 2;
        double offsetY = (getHeight() - VIEW_HEIGHT * scale) / 2;
        g.translate(offsetX, offsetY);
        g.scale(scale, scale);
        g.clipRect(0, 0, VIEW_WIDTH, VIEW_HEIGHT);

        drawBackground(g);
        AffineTransform beforeWorld = g.getTransform();
        g.translate(-cameraX, 0);
        drawWorld(g);
        g.setTransform(beforeWorld);
        drawHud(g);

        if (state == State.PAUSED) drawOverlay(g, "PAUSED", "Press P to keep forging");
        else if (state == State.WON) drawOverlay(g, "FORGE LIT!", "R to run the course again");

        g.dispose();
    }

    private void drawBackground(Graphics2D g)
    {
        g.setPaint(new GradientPaint(0, 0, new Color(32, 32, 76), 0, VIEW_HEIGHT, new Color(123, 61, 106)));
        g.fillRect(0, 0, VIEW_WIDTH, VIEW_HEIGHT);

        g.setColor(new Color(255, 192, 111, 190));
        g.fill(new Ellipse2D.Double(735 - cameraX * 0.03, 65, 115, 115));

        drawMountainLayer(g, 0.08, 330, new Color(65, 52, 99), 250, 170);
        drawMountainLayer(g, 0.16, 395, new Color(48, 43, 82), 190, 125);

        g.setColor(new Color(231, 224, 255, 75));
        for (int index = 0; index < 10; index++)
        {
            double wrapped = Math.floorMod((long) (index * 173 - cameraX * 0.1), 1_180) - 110;
            double y = 65 + (index % 4) * 54;
            g.fill(new Ellipse2D.Double(wrapped, y, 65, 18));
            g.fill(new Ellipse2D.Double(wrapped + 27, y - 10, 55, 25));
        }
    }

    private void drawMountainLayer(Graphics2D g, double parallax, int baseY, Color color, int spacing, int height)
    {
        g.setColor(color);
        double shift = -(cameraX * parallax) % spacing;
        for (double x = shift - spacing; x < VIEW_WIDTH + spacing; x += spacing)
        {
            Polygon mountain = new Polygon();
            mountain.addPoint((int) x, baseY);
            mountain.addPoint((int) (x + spacing * 0.52), baseY - height);
            mountain.addPoint((int) (x + spacing), baseY);
            g.fillPolygon(mountain);
        }
        g.fillRect(0, baseY, VIEW_WIDTH, VIEW_HEIGHT - baseY);
    }

    private void drawWorld(Graphics2D g)
    {
        for (Level.Platform platform : level.platforms())
        {
            if (platform.x() + platform.width() < cameraX - 40 || platform.x() > cameraX + VIEW_WIDTH + 40) continue;
            Color body = platform.ground() ? new Color(37, 38, 63) : new Color(48, 50, 79);
            g.setColor(body);
            g.fill(new Rectangle2D.Double(platform.x(), platform.y(), platform.width(), platform.height()));
            g.setColor(new Color(239, 119, 83));
            g.fill(new Rectangle2D.Double(platform.x(), platform.y(), platform.width(), 7));
            g.setColor(new Color(255, 181, 92));
            g.fill(new Rectangle2D.Double(platform.x(), platform.y(), platform.width(), 2));
        }

        for (Level.Coin coin : level.coins())
        {
            if (coin.collected) continue;
            double pulse = 0.75 + Math.abs(Math.sin(elapsed * 5 + coin.x * 0.02)) * 0.25;
            g.setColor(new Color(255, 199, 62, 45));
            g.fill(new Ellipse2D.Double(coin.x - 20, coin.y - 20, 40, 40));
            g.setColor(new Color(255, 218, 91));
            g.fill(new Ellipse2D.Double(coin.x - 10 * pulse, coin.y - 12, 20 * pulse, 24));
            g.setColor(new Color(255, 247, 190));
            g.fill(new Ellipse2D.Double(coin.x - 3, coin.y - 7, 4, 7));
        }

        for (Level.Enemy enemy : level.enemies()) if (enemy.alive) drawEnemy(g, enemy);
        drawForge(g);
        drawParticles(g);
        drawPlayer(g);
    }

    private void drawEnemy(Graphics2D g, Level.Enemy enemy)
    {
        double bob = Math.sin(elapsed * 7 + enemy.x * 0.03) * 2;
        g.setColor(new Color(36, 27, 52, 90));
        g.fill(new Ellipse2D.Double(enemy.x + 3, enemy.y + 24, 34, 10));
        g.setColor(new Color(83, 218, 153));
        g.fillRoundRect((int) enemy.x, (int) (enemy.y + bob), (int) Level.Enemy.WIDTH, (int) Level.Enemy.HEIGHT, 16, 16);
        g.setColor(new Color(22, 43, 48));
        int eyeX = (int) (enemy.direction > 0 ? enemy.x + 25 : enemy.x + 10);
        g.fillOval(eyeX, (int) enemy.y + 10, 5, 7);
    }

    private void drawForge(Graphics2D g)
    {
        double glow = 8 + Math.sin(elapsed * 4) * 3;
        g.setColor(new Color(255, 104, 55, 40));
        g.fill(new Ellipse2D.Double(FINISH_X - glow, 240 - glow, 70 + glow * 2, 235 + glow * 2));
        g.setColor(new Color(31, 28, 50));
        g.fillRoundRect((int) FINISH_X, 250, 54, 218, 18, 18);
        g.setStroke(new BasicStroke(6));
        g.setColor(new Color(255, 126, 61));
        g.drawRoundRect((int) FINISH_X + 4, 254, 46, 210, 14, 14);
        g.setPaint(new GradientPaint(0, 310, new Color(255, 232, 112), 0, 452, new Color(255, 68, 58)));
        g.fillOval((int) FINISH_X + 15, 330, 25, 100);
        g.setStroke(new BasicStroke(1));
    }

    private void drawParticles(Graphics2D g)
    {
        for (Particle particle : particles)
        {
            double alpha = Math.min(1, particle.life * 2.3);
            g.setColor(new Color(
                    particle.color.getRed(), particle.color.getGreen(), particle.color.getBlue(), (int) (alpha * 255)
            ));
            g.fill(new Ellipse2D.Double(particle.x, particle.y, particle.size, particle.size));
        }
    }

    private void drawPlayer(Graphics2D g)
    {
        double x = player.x();
        double y = player.y();
        if (player.isDashing())
        {
            g.setColor(new Color(255, 172, 70, 70));
            for (int index = 1; index <= 4; index++)
            {
                g.fillRoundRect((int) (x - player.facing() * index * 13), (int) y + 6, 30, 32, 12, 12);
            }
        }

        g.setColor(new Color(24, 22, 42, 100));
        g.fillOval((int) x - 3, (int) y + 37, 40, 12);
        g.setColor(new Color(241, 91, 82));
        g.fillRoundRect((int) x, (int) y, (int) Player.WIDTH, (int) Player.HEIGHT, 11, 11);
        g.setColor(new Color(255, 169, 72));
        g.fillRoundRect((int) x + 5, (int) y + 5, 24, 16, 7, 7);
        g.setColor(new Color(35, 35, 57));
        int eyeX = (int) (player.facing() > 0 ? x + 20 : x + 10);
        g.fillOval(eyeX, (int) y + 10, 5, 6);
        g.setColor(new Color(255, 225, 133));
        g.fillRect((int) x + 7, (int) y + 33, 20, 5);
    }

    private void drawHud(Graphics2D g)
    {
        g.setColor(new Color(18, 18, 37, 190));
        g.fillRoundRect(22, 20, 250, 76, 18, 18);
        g.setColor(new Color(255, 217, 91));
        g.fillOval(40, 38, 20, 20);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.drawString(score + " / " + level.coinCount() + " sparks", 72, 55);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(new Color(210, 207, 231));
        g.drawString("Falls " + deaths, 40, 79);
        g.drawString("Dash", 126, 79);
        g.setColor(new Color(55, 51, 78));
        g.fillRoundRect(167, 69, 82, 10, 8, 8);
        g.setColor(new Color(255, 126, 68));
        g.fillRoundRect(167, 69, (int) (82 * player.dashReadiness()), 10, 8, 8);

        if (elapsed < 8 && state == State.PLAYING)
        {
            g.setColor(new Color(18, 18, 37, 185));
            g.fillRoundRect(300, 20, 638, 42, 16, 16);
            g.setColor(new Color(236, 231, 247));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.drawString("A/D move   •   Space jumps   •   Shift dashes   •   P pauses   •   R restarts", 324, 47);
        }
    }

    private void drawOverlay(Graphics2D g, String title, String subtitle)
    {
        g.setColor(new Color(12, 12, 28, 175));
        g.fillRect(0, 0, VIEW_WIDTH, VIEW_HEIGHT);
        g.setColor(new Color(255, 173, 74));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 46));
        int titleWidth = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (VIEW_WIDTH - titleWidth) / 2, 245);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        int subtitleWidth = g.getFontMetrics().stringWidth(subtitle);
        g.drawString(subtitle, (VIEW_WIDTH - subtitleWidth) / 2, 282);
    }

    private final class KeyAction extends AbstractAction
    {
        private final int key;
        private final boolean pressed;

        private KeyAction(int key, boolean pressed)
        {
            this.key = key;
            this.pressed = pressed;
        }

        @Override
        public void actionPerformed(ActionEvent event)
        {
            if (pressed)
            {
                if (heldKeys.add(key)) pressedKeys.add(key);
            }
            else heldKeys.remove(key);
        }
    }

    private static final class Particle
    {
        double x;
        double y;
        final double velocityX;
        double velocityY;
        double life;
        final double size;
        final Color color;

        private Particle(double x, double y, double velocityX, double velocityY, double life, double size, Color color)
        {
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.life = life;
            this.size = size;
            this.color = color;
        }
    }
}
