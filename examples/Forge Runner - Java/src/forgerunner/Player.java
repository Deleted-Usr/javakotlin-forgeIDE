package forgerunner;

import java.awt.geom.Rectangle2D;

/// Player movement, including forgiving coyote-time jumps and a short dash.
final class Player
{
    static final double WIDTH = 34;
    static final double HEIGHT = 44;

    private static final double RUN_SPEED = 245;
    private static final double JUMP_SPEED = 555;
    private static final double GRAVITY = 1_500;
    private static final double DASH_SPEED = 690;

    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private double coyoteTime;
    private double jumpBuffer;
    private double dashTime;
    private double dashCooldown;
    private int facing = 1;
    private boolean onGround;

    Player(double x, double y)
    {
        respawn(x, y);
    }

    void update(double seconds, boolean left, boolean right, boolean jumpPressed,
                boolean jumpHeld, boolean dashPressed, Level level)
    {
        if (onGround) coyoteTime = 0.11;
        else coyoteTime = Math.max(0, coyoteTime - seconds);

        jumpBuffer = jumpPressed ? 0.12 : Math.max(0, jumpBuffer - seconds);
        dashCooldown = Math.max(0, dashCooldown - seconds);
        if (left != right) facing = right ? 1 : -1;

        if (dashPressed && dashCooldown <= 0)
        {
            dashTime = 0.13;
            dashCooldown = 0.72;
            velocityX = facing * DASH_SPEED;
            velocityY = 0;
        }

        if (dashTime > 0)
        {
            dashTime = Math.max(0, dashTime - seconds);
        }
        else
        {
            double direction = (right ? 1 : 0) - (left ? 1 : 0);
            double target = direction * RUN_SPEED;
            double acceleration = onGround ? 2_200 : 1_250;
            velocityX = moveTowards(velocityX, target, acceleration * seconds);

            if (jumpBuffer > 0 && coyoteTime > 0)
            {
                velocityY = -JUMP_SPEED;
                jumpBuffer = 0;
                coyoteTime = 0;
                onGround = false;
            }
            if (!jumpHeld && velocityY < -220) velocityY = -220;
            velocityY = Math.min(820, velocityY + GRAVITY * seconds);
        }

        moveHorizontal(seconds, level);
        moveVertical(seconds, level);
    }

    private void moveHorizontal(double seconds, Level level)
    {
        x += velocityX * seconds;
        Rectangle2D.Double player = bounds();

        for (Level.Platform platform : level.platforms())
        {
            Rectangle2D.Double solid = platform.bounds();
            if (!player.intersects(solid)) continue;

            if (velocityX > 0) x = solid.x - WIDTH;
            else if (velocityX < 0) x = solid.x + solid.width;
            velocityX = 0;
            player = bounds();
        }

        x = Math.max(0, Math.min(Level.WORLD_WIDTH - WIDTH, x));
    }

    private void moveVertical(double seconds, Level level)
    {
        y += velocityY * seconds;
        onGround = false;
        Rectangle2D.Double player = bounds();

        for (Level.Platform platform : level.platforms())
        {
            Rectangle2D.Double solid = platform.bounds();
            if (!player.intersects(solid)) continue;

            if (velocityY > 0)
            {
                y = solid.y - HEIGHT;
                onGround = true;
            }
            else if (velocityY < 0)
            {
                y = solid.y + solid.height;
            }
            velocityY = 0;
            player = bounds();
        }
    }

    private static double moveTowards(double value, double target, double amount)
    {
        if (value < target) return Math.min(value + amount, target);
        return Math.max(value - amount, target);
    }

    void respawn(double spawnX, double spawnY)
    {
        x = spawnX;
        y = spawnY;
        velocityX = 0;
        velocityY = 0;
        dashTime = 0;
        dashCooldown = 0;
        onGround = false;
    }

    void bounce()
    {
        velocityY = -390;
        onGround = false;
    }

    Rectangle2D.Double bounds()
    {
        return new Rectangle2D.Double(x, y, WIDTH, HEIGHT);
    }

    double x() { return x; }
    double y() { return y; }
    double velocityY() { return velocityY; }
    int facing() { return facing; }
    boolean isDashing() { return dashTime > 0; }
    double dashReadiness() { return 1 - Math.min(1, dashCooldown / 0.72); }
}
