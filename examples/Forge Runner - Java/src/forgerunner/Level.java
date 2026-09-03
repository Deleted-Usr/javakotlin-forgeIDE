package forgerunner;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The hand-authored course and its lightweight game objects.
final class Level
{
    static final double WORLD_WIDTH = 6_650;
    static final double FLOOR_Y = 468;

    record Platform(double x, double y, double width, double height, boolean ground)
    {
        Rectangle2D.Double bounds()
        {
            return new Rectangle2D.Double(x, y, width, height);
        }
    }

    static final class Coin
    {
        final double x;
        final double y;
        boolean collected;

        Coin(double x, double y)
        {
            this.x = x;
            this.y = y;
        }

        Rectangle2D.Double bounds()
        {
            return new Rectangle2D.Double(x - 12, y - 12, 24, 24);
        }
    }

    static final class Enemy
    {
        static final double WIDTH = 38;
        static final double HEIGHT = 30;

        double x;
        final double y;
        final double left;
        final double right;
        double direction = 1;
        boolean alive = true;

        Enemy(double x, double y, double left, double right)
        {
            this.x = x;
            this.y = y;
            this.left = left;
            this.right = right;
        }

        void update(double seconds)
        {
            if (!alive) return;
            x += direction * 72 * seconds;
            if (x < left)
            {
                x = left;
                direction = 1;
            }
            else if (x + WIDTH > right)
            {
                x = right - WIDTH;
                direction = -1;
            }
        }

        Rectangle2D.Double bounds()
        {
            return new Rectangle2D.Double(x, y, WIDTH, HEIGHT);
        }
    }

    private final List<Platform> platforms = new ArrayList<>();
    private final List<Coin> coins = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();

    private Level() { }

    static Level create()
    {
        Level level = new Level();

        level.ground(0, 930);
        level.ground(1_090, 780);
        level.ground(2_030, 1_030);
        level.ground(3_250, 620);
        level.ground(4_060, 1_080);
        level.ground(5_340, 1_310);

        level.platform(300, 382, 170, 24);
        level.platform(590, 318, 150, 24);
        level.platform(835, 374, 95, 24);
        level.platform(1_135, 370, 180, 24);
        level.platform(1_430, 305, 170, 24);
        level.platform(1_700, 250, 150, 24);
        level.platform(2_105, 365, 150, 24);
        level.platform(2_360, 292, 185, 24);
        level.platform(2_650, 225, 160, 24);
        level.platform(2_880, 350, 180, 24);
        level.platform(3_285, 350, 150, 24);
        level.platform(3_560, 280, 165, 24);
        level.platform(3_830, 365, 100, 24);
        level.platform(4_170, 375, 180, 24);
        level.platform(4_490, 310, 160, 24);
        level.platform(4_770, 245, 190, 24);
        level.platform(5_090, 350, 130, 24);
        level.platform(5_390, 360, 160, 24);
        level.platform(5_690, 300, 180, 24);
        level.platform(6_000, 240, 170, 24);
        level.platform(6_300, 350, 160, 24);

        level.coinLine(350, 350, 3, 48);
        level.coinLine(620, 285, 3, 44);
        level.coinLine(1_175, 337, 3, 50);
        level.coinLine(1_465, 272, 3, 48);
        level.coinLine(1_735, 215, 3, 43);
        level.coinLine(2_150, 332, 3, 48);
        level.coinLine(2_400, 258, 3, 50);
        level.coinLine(2_690, 190, 3, 48);
        level.coinLine(3_590, 246, 3, 48);
        level.coinLine(4_215, 342, 3, 50);
        level.coinLine(4_525, 276, 3, 48);
        level.coinLine(4_815, 211, 3, 50);
        level.coinLine(5_730, 266, 3, 50);
        level.coinLine(6_040, 206, 3, 48);
        level.coinLine(6_345, 316, 3, 48);

        level.enemy(700, FLOOR_Y - Enemy.HEIGHT, 560, 900);
        level.enemy(1_230, 370 - Enemy.HEIGHT, 1_135, 1_315);
        level.enemy(2_720, FLOOR_Y - Enemy.HEIGHT, 2_560, 2_980);
        level.enemy(3_510, FLOOR_Y - Enemy.HEIGHT, 3_330, 3_800);
        level.enemy(4_770, 245 - Enemy.HEIGHT, 4_770, 4_960);
        level.enemy(5_570, FLOOR_Y - Enemy.HEIGHT, 5_420, 5_920);

        return level;
    }

    private void ground(double x, double width)
    {
        platforms.add(new Platform(x, FLOOR_Y, width, 120, true));
    }

    private void platform(double x, double y, double width, double height)
    {
        platforms.add(new Platform(x, y, width, height, false));
    }

    private void coinLine(double x, double y, int count, double spacing)
    {
        for (int index = 0; index < count; index++) coins.add(new Coin(x + index * spacing, y));
    }

    private void enemy(double x, double y, double left, double right)
    {
        enemies.add(new Enemy(x, y, left, right));
    }

    List<Platform> platforms() { return Collections.unmodifiableList(platforms); }
    List<Coin> coins() { return Collections.unmodifiableList(coins); }
    List<Enemy> enemies() { return Collections.unmodifiableList(enemies); }
    int coinCount() { return coins.size(); }
}
