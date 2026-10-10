package com.willclay.forgeide.splash;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice.WindowTranslucency;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/// The window shown while Forge starts: the anvil, sparks flying off the
/// ingot, and a status line naming the startup phase that is actually running.
///
/// **Why it draws on its own thread.** The obvious Swing approach (a `JWindow`
/// repainted by a Swing `Timer`) animates on the EDT. But while the splash is
/// up, the EDT spends about a second building the main window, so the sparks
/// would be frozen for nearly the whole time the splash is visible.
///
/// So this uses **active rendering**, the way games draw. The splash is a
/// plain AWT [Window] with a [BufferStrategy]: a back buffer that a dedicated
/// thread draws each frame into and then flips onto the screen. It is a small
/// game loop that never needs the EDT, so it keeps running while the EDT is
/// busy.
///
/// **Threading.** Construct and call [#show()], [#setStatus(String)] and
/// [#close()] from any thread. Creating and disposing the window happens on
/// the EDT. Everything the animation touches (the sparks, the strike timer) is
/// used only by the render thread, so it needs no locking. The status is a
/// `volatile` field that the next frame reads.
///
/// The anvil image (`forge-splash.png`) comes from
/// `packaging/icon/ForgeIconGenerator.java`, the same drawing as the app icon
/// without its tile and baked-in sparks.
public final class ForgeSplash
{
    private static final int SPLASH_WIDTH  = 560;
    private static final int SPLASH_HEIGHT = 300;
    private static final int CORNER        = 24;
    private static final long FRAME_NANOS  = 16_000_000;
    private static final int FADE_MS       = 160;

    // The anvil image is drawn on the generator's 256 grid, so the ingot's
    // position in the window follows from where and how big the image is drawn.
    private static final int ANVIL_X    = 20;
    private static final int ANVIL_Y    = 38;
    private static final int ANVIL_SIZE = 232;
    private static final double GRID    = ANVIL_SIZE / 256.0;
    private static final double INGOT_X = ANVIL_X + 116 * GRID;
    private static final double INGOT_Y = ANVIL_Y + 94 * GRID;
    private static final double INGOT_W = 80 * GRID;

    // Physics, in pixels and seconds.
    private static final double GRAVITY           = 520;
    private static final double SPARKS_PER_SECOND = 7;
    private static final double STRIKE_INTERVAL   = 1.3;
    private static final int STRIKE_SPARKS        = 22;
    /// The longest step one frame may take. If the render thread is starved
    /// for a moment (a garbage collection, a busy machine), moving every spark
    /// by the whole gap in one jump would fling them off-screen.
    private static final double MAX_FRAME_TIME = 0.05;

    private static final int TEXT_X = 270;
    private static final String ANVIL_RESOURCE = "/com/willclay/forgeide/icons/app/forge-splash.png";

    private static final Color BACKGROUND_TOP    = new Color(0x252A3A);
    private static final Color BACKGROUND_BOTTOM = new Color(0x12141B);
    private static final Color TEXT              = new Color(0xF0F3F8);
    private static final Color TEXT_DIM          = new Color(0x9AA3B5);
    private static final Color WHITE_HOT         = new Color(0xFFF6DC);
    private static final Color HOT               = new Color(0xFFC14D);
    private static final Color EMBER             = new Color(0xFF7A1A);
    private static final Color EMBER_DARK        = new Color(0xB3260A);

    private static final Font TITLE_FONT   = new Font(Font.SANS_SERIF, Font.BOLD, 36);
    private static final Font TAGLINE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 15);
    private static final Font SMALL_FONT   = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private final String version;
    private volatile String status = "Starting";
    private volatile boolean running;

    // Render thread only.
    private final Random random      = new Random();
    private final List<Spark> sparks = new ArrayList<>();
    private Image anvil;
    private double spawnDebt;
    private double untilStrike = 0.35;
    private double strikeFlash;

    // EDT only.
    private Window window;
    private Thread renderThread;
    private boolean closing;

    public ForgeSplash()
    {
        // From the jar manifest's Implementation-Version. Running from an IDE's
        // class folders there is no manifest, so this is null and not shown.
        version = ForgeSplash.class.getPackage().getImplementationVersion();
    }

    /// Opens the splash centred on screen. Returns at once; the window
    /// appears as soon as the EDT gets to it.
    public void show()
    {
        if (GraphicsEnvironment.isHeadless()) return;

        // Read here, on the caller's thread, so the EDT isn't kept waiting on
        // file access, and so the anvil is there in the very first frame.
        Image image = loadAnvil();

        SwingUtilities.invokeLater(() ->
        {
            // A plain AWT Window, not a Swing JWindow: nothing Swing paints
            // here, every pixel comes from the render thread.
            window = new Window((Frame) null);
            window.setSize(SPLASH_WIDTH, SPLASH_HEIGHT);
            window.setLocationRelativeTo(null);
            // Stays above the main window as it appears, so the fade-out is seen.
            window.setAlwaysOnTop(true);
            // AWT shouldn't paint over our frames when the window is uncovered;
            // the next frame redraws everything anyway.
            window.setIgnoreRepaint(true);

            // Rounded corners. The edges are hard, not anti-aliased, but the
            // soft rim drawn each frame hides most of that.
            if (supports(WindowTranslucency.PERPIXEL_TRANSPARENT))
            {
                window.setShape(new RoundRectangle2D.Double(0, 0, SPLASH_WIDTH, SPLASH_HEIGHT, CORNER, CORNER));
            }

            window.setVisible(true);
            // Two buffers: draw into the hidden one, then show() flips it on screen.
            window.createBufferStrategy(2);
            BufferStrategy buffers = window.getBufferStrategy();

            anvil   = image;
            running = true;

            renderThread = new Thread(() -> renderLoop(buffers), "forge-splash");
            renderThread.setDaemon(true);
            renderThread.start();
        });
    }

    /// Shows what Forge is doing now, such as "Discovering language plugins".
    public void setStatus(String status)
    {
        this.status = status;
    }

    /// Fades the splash out and disposes it. Safe to call more than once, and
    /// before or without [#show()].
    public void close()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (window == null || closing) return;
            closing = true;

            if (!supports(WindowTranslucency.TRANSLUCENT))
            {
                dispose();
                return;
            }

            // The render thread keeps animating underneath; only the window's
            // overall opacity changes, which is safe to do from the EDT.
            long start = System.nanoTime();
            Timer fade = new Timer(16, null);
            fade.addActionListener(_ ->
            {
                float progress = (System.nanoTime() - start) / 1_000_000f / FADE_MS;
                if (progress >= 1)
                {
                    fade.stop();
                    dispose();
                }
                else
                {
                    window.setOpacity(1 - progress);
                }
            });

            fade.start();
        });
    }

    private void dispose()
    {
        // Stop drawing before the window goes, or a frame could be shown into
        // a buffer that no longer exists. A frame takes a few milliseconds, so
        // this wait on the EDT is short.
        running = false;
        try
        {
            renderThread.join(250);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        window.dispose();
        window = null;
    }

    /// The game loop: update, draw, show, wait for the next frame.
    private void renderLoop(BufferStrategy buffers)
    {
        long lastFrame = System.nanoTime();
        try
        {
            while (running)
            {
                long frameStart = System.nanoTime();
                // Moving by the time that really passed keeps the speed steady
                // even when a frame runs late.
                double dt = Math.clamp((frameStart - lastFrame) / 1e9, 0.0, MAX_FRAME_TIME);
                lastFrame = frameStart;

                update(dt);
                drawFrame(buffers);

                long sleepNanos = FRAME_NANOS - (System.nanoTime() - frameStart);
                if (sleepNanos > 0) Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
            }
        }
        catch (InterruptedException e)
        {
            // Asked to stop; nothing to clean up.
        }
        catch (RuntimeException e)
        {
            // The splash is decoration. Whatever went wrong, it must not take
            // startup down with it, so stop animating and carry on.
            System.err.println("Splash animation stopped: " + e);
        }
    }

    private void update(double dt)
    {
        // A steady trickle. Seven a second is rarely a whole spark per frame,
        // so the fractions are carried over until they add up to one.
        spawnDebt += SPARKS_PER_SECOND * dt;
        while (spawnDebt >= 1)
        {
            sparks.add(new Spark(random));
            spawnDebt--;
        }

        // Every so often, a hammer strike: a burst of sparks and a flash of glow.
        untilStrike -= dt;
        if (untilStrike <= 0)
        {
            untilStrike = STRIKE_INTERVAL;
            strikeFlash = 1;

            for (int i = 0; i < STRIKE_SPARKS; i++)
            {
                sparks.add(new Spark(random));
            }
        }

        strikeFlash = Math.max(0, strikeFlash - dt * 3);

        sparks.removeIf(spark -> !spark.update(dt));
    }

    /// Draws one frame into the back buffer and flips it onto the screen. The
    /// two loops are the standard BufferStrategy recipe: on Windows the video
    /// memory behind a buffer can be lost (for example when the screen
    /// locks), and then the frame simply has to be drawn again.
    private void drawFrame(BufferStrategy buffers)
    {
        do
        {
            do
            {
                Graphics2D g = (Graphics2D) buffers.getDrawGraphics();
                try
                {
                    paint(g);
                }
                finally
                {
                    g.dispose();
                }
            }
            while (buffers.contentsRestored());

            buffers.show();
        }
        while (buffers.contentsLost());

        // Pushes the frame out now rather than whenever the system gets to it.
        Toolkit.getDefaultToolkit().sync();
    }

    private void paint(Graphics2D g)
    {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        // The window's shape cuts the corners off, so the whole buffer is filled.
        g.setPaint(new GradientPaint(0, 0, BACKGROUND_TOP, 0, SPLASH_HEIGHT, BACKGROUND_BOTTOM));
        g.fillRect(0, 0, SPLASH_WIDTH, SPLASH_HEIGHT);

        // The forge's glow, swelling with each hammer strike.
        float glowRadius = (float) (170 + 40 * strikeFlash);
        int glowAlpha = (int) (60 + 90 * strikeFlash);

        g.setPaint(
                new RadialGradientPaint(
                    (float) (INGOT_X + INGOT_W / 2), (float) INGOT_Y, glowRadius,
                    new float[] {0f, 1f},
                    new Color[] {withAlpha(EMBER, glowAlpha), withAlpha(EMBER, 0)}
                )
        );

        g.fillRect(0, 0, SPLASH_WIDTH, SPLASH_HEIGHT);

        if (anvil != null) g.drawImage(anvil, ANVIL_X, ANVIL_Y, ANVIL_SIZE, ANVIL_SIZE, null);

        paintSparks(g);
        paintText(g);

        // A faint anti-aliased rim: gives the dark card an edge on a dark
        // desktop and softens the hard corners left by the window shape.
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(255, 255, 255, 26));
        g.draw(new RoundRectangle2D.Double(0.75, 0.75, SPLASH_WIDTH - 1.5, SPLASH_HEIGHT - 1.5, CORNER, CORNER));
    }

    private void paintSparks(Graphics2D g)
    {
        for (Spark spark : sparks)
        {
            double heat = spark.heat();

            g.setColor(sparkColour(heat));
            g.setStroke(new BasicStroke((float) (1 + 1.6 * heat), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // A short streak back along the direction of travel reads as
            // movement, like a spark in a long-exposure photo.
            double tail = 0.025;

            g.draw(new Line2D.Double(spark.x, spark.y, spark.x - spark.vx * tail, spark.y - spark.vy * tail));
        }
    }

    private void paintText(Graphics2D g)
    {
        g.setColor(TEXT);
        g.setFont(TITLE_FONT);
        g.drawString("Forge IDE", TEXT_X, 124);

        g.setColor(TEXT_DIM);
        g.setFont(TAGLINE_FONT);
        g.drawString("An IDE that knows your workflow", TEXT_X, 152);

        g.setFont(SMALL_FONT);

        if (version != null) g.drawString("Version " + version, TEXT_X, 176);

        g.drawString(status + "…", TEXT_X, SPLASH_HEIGHT - 34);
    }

    private static boolean supports(WindowTranslucency kind)
    {
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .isWindowTranslucencySupported(kind);
    }

    private static Image loadAnvil()
    {
        try (InputStream input = ForgeSplash.class.getResourceAsStream(ANVIL_RESOURCE))
        {
            if (input == null) return null;
            return ImageIO.read(input);
        }
        catch (IOException e)
        {
            // Not worth stopping startup over; the splash just shows without it.
            System.err.println("Could not load " + ANVIL_RESOURCE + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /// White-hot when fresh, cooling through orange to a dark red as `heat`
    /// falls from 1 to 0, and fading out over the last stretch.
    private static Color sparkColour(double heat)
    {
        Color colour;

        if (heat > 0.6)      colour = blend(HOT, WHITE_HOT, (heat - 0.6) / 0.4);
        else if (heat > 0.3) colour = blend(EMBER, HOT, (heat - 0.3) / 0.3);
        else                 colour = blend(EMBER_DARK, EMBER, heat / 0.3);

        int alpha = (int) (255 * Math.min(1, heat * 2.5));

        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }

    /// `amount` 0 gives `from`, 1 gives `to`.
    private static Color blend(Color from, Color to, double amount)
    {
        return new Color(
                (int) (from.getRed()   + (to.getRed()   - from.getRed())   * amount),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                (int) (from.getBlue()  + (to.getBlue()  - from.getBlue())  * amount)
        );
    }

    private static Color withAlpha(Color c, int alpha)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /// One spark: thrown up off the ingot, pulled down by gravity, cooling as it ages
    private static final class Spark
    {
        double x, y, vx, vy, age;
        final double life;

        Spark(Random r)
        {
            x = INGOT_X + r.nextDouble() * INGOT_W;
            y = INGOT_Y;
            // Mostly up and to the right, the way sparks leave a hammer blow.
            vx   = -40 + r.nextDouble() * 200;
            vy   = -(140 + r.nextDouble() * 180);
            life = 0.5 + r.nextDouble() * 0.9;
        }

        /// Moves the spark on by `dt` seconds. Returns false once it has burnt out.
        boolean update(double dt)
        {
            vy  += GRAVITY * dt;
            x   += vx * dt;
            y   += vy * dt;
            age += dt;

            return age < life;
        }

        /// 1 when fresh and white-hot, falling to 0 as it burns out.
        double heat()
        {
            return 1 - age / life;
        }
    }
}
