import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Draws the ForgeIDE application icon — an anvil with a white-hot ingot and a
/// few sparks on a dark tile — and writes it out in the two forms the app needs:
///
/// - **PNGs** in `src/main/resources/.../icons/app/`, which `AppIcons` hands to
///   Swing so the window title bar and taskbar show the icon.
/// - **`forge.ico`** in `native/cpp-native/`, which the C++ launcher embeds as a
///   Windows resource so Explorer shows the icon on `ForgeIDE.exe`.
///
/// The icon is drawn, not hand-pixelled, so every size comes from the same
/// shapes. Everything is designed on a 256×256 grid and scaled down. Very small
/// sizes drop the sparks and thicken the ingot, because a 1-pixel detail at
/// 16×16 turns into mush rather than a spark.
///
/// Run from the repository root (no build needed, Java runs single source files):
///
/// ```
/// java packaging/icon/ForgeIconGenerator.java
/// ```
public final class ForgeIconGenerator
{
    private static final int[] PNG_SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};
    private static final int[] ICO_SIZES = {16, 20, 24, 32, 40, 48, 64, 256};

    private static final Path PNG_DIR = Path.of("src/main/resources/com/willclay/forgeide/icons/app");
    private static final Path ICO_FILE = Path.of("native/cpp-native/forge.ico");

    // Palette: cool steel on a night-forge tile, warmed by the ingot's glow.
    private static final Color TILE_TOP = new Color(0x2B3042);
    private static final Color TILE_BOTTOM = new Color(0x141720);
    private static final Color STEEL_LIGHT = new Color(0xF0F3F8);
    private static final Color STEEL_DARK = new Color(0x8A93A6);
    private static final Color EMBER = new Color(0xFF7A1A);
    private static final Color HOT = new Color(0xFFC14D);
    private static final Color WHITE_HOT = new Color(0xFFF6DC);

    public static void main(String[] args) throws IOException
    {
        Files.createDirectories(PNG_DIR);

        for (int size : PNG_SIZES)
        {
            ImageIO.write(render(size), "png", PNG_DIR.resolve("forge-" + size + ".png").toFile());
        }

        List<byte[]> icoImages = new ArrayList<>();
        for (int size : ICO_SIZES) icoImages.add(toPng(render(size)));
        writeIco(ICO_FILE, ICO_SIZES, icoImages);

        System.out.println("Wrote " + PNG_SIZES.length + " PNGs to " + PNG_DIR + " and " + ICO_FILE);
    }

    static BufferedImage render(int size)
    {
        boolean tiny = size <= 24;

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(size / 256.0, size / 256.0);

        // Tiny icons use the whole canvas; there is no pixel to spare for margin.
        double inset = tiny ? 0 : 8;
        Shape tile = new RoundRectangle2D.Double(inset, inset, 256 - 2 * inset, 256 - 2 * inset, 60, 60);

        g.setPaint(new GradientPaint(0, 0, TILE_TOP, 0, 256, TILE_BOTTOM));
        g.fill(tile);

        // The forge's glow: warm light spilling from the ingot across the tile.
        g.setClip(tile);

        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(154, 108), 120,
                new float[] {0f, 0.55f, 1f},
                new Color[] {withAlpha(EMBER, 120), withAlpha(EMBER, 28), withAlpha(EMBER, 0)})
        );

        g.fill(tile);
        g.setClip(null);

        if (!tiny)
        {
            // A faint rim of light so the tile doesn't vanish on a dark taskbar.
            g.setStroke(new BasicStroke(3f));
            g.setColor(new Color(255, 255, 255, 22));
            g.draw(new RoundRectangle2D.Double(inset + 1.5, inset + 1.5, 253 - 2 * inset, 253 - 2 * inset, 58, 58));
        }

        Shape anvil = anvil();
        g.setPaint(new GradientPaint(0, 112, STEEL_LIGHT, 0, 212, STEEL_DARK));
        g.fill(anvil);

        // The face of the anvil catches the ingot's light.
        g.setClip(anvil);
        g.setPaint(new GradientPaint(0, 112, withAlpha(HOT, 150), 0, 128, withAlpha(HOT, 0)));
        g.fillRect(40, 112, 180, 16);
        g.setClip(null);

        // The ingot: wider and thicker at tiny sizes so it survives as a stripe.
        double ingotTop = tiny ? 84 : 94;
        double ingotX = tiny ? 104 : 116;
        double ingotW = tiny ? 104 : 80;
        Shape ingot = new RoundRectangle2D.Double(ingotX, ingotTop, ingotW, 112 - ingotTop, 12, 12);

        if (!tiny) glow(g, ingotX + ingotW / 2, 103, 70);

        g.setPaint(new GradientPaint(0, (float) ingotTop, WHITE_HOT, 0, 112, HOT));
        g.fill(ingot);

        if (size >= 32)
        {
            spark(g, 206, 70, 8);
            spark(g, 226, 44, 5);
            spark(g, 178, 54, 4.5);
            if (size >= 64) spark(g, 152, 66, 3);
        }

        g.dispose();
        return image;
    }

    /// A classic London-pattern anvil: flat face, horn tapering to the left,
    /// pinched waist and a flared foot. Drawn on the 256 grid.
    private static Shape anvil()
    {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(212, 112);                 // top right of the face
        p.lineTo(212, 144);                 // heel
        p.quadTo(188, 148, 182, 164);       // under the heel, into the waist
        p.lineTo(182, 178);
        p.quadTo(182, 186, 200, 190);       // flare out to the foot
        p.lineTo(210, 196);
        p.lineTo(210, 212);
        p.lineTo(78, 212);
        p.lineTo(78, 196);
        p.lineTo(88, 190);
        p.quadTo(106, 186, 106, 178);
        p.lineTo(106, 164);
        p.quadTo(100, 150, 80, 146);        // under the horn
        p.quadTo(60, 140, 40, 112);         // the horn's point
        p.closePath();
        return p;
    }

    private static void glow(Graphics2D g, double cx, double cy, double radius)
    {
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(cx, cy),
                (float) radius,
                new float[] {0f, 0.4f, 1f},
                new Color[] {withAlpha(HOT, 170), withAlpha(EMBER, 60), withAlpha(EMBER, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE)
        );

        g.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
    }

    private static void spark(Graphics2D g, double cx, double cy, double r)
    {
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(cx, cy),
                (float) (r * 3),
                new float[] {0f, 1f},
                new Color[] {withAlpha(HOT, 140), withAlpha(EMBER, 0)})
        );

        g.fill(new Ellipse2D.Double(cx - r * 3, cy - r * 3, r * 6, r * 6));

        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(WHITE_HOT);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    private static Color withAlpha(Color c, int alpha)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static byte[] toPng(BufferedImage image) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    /// Writes a Windows `.ico` whose entries are PNG-compressed, which every
    /// Windows since Vista understands. The format is a tiny header, one
    /// 16-byte directory entry per image, then the image bytes themselves.
    /// All numbers are little-endian.
    private static void writeIco(Path file, int[] sizes, List<byte[]> pngs) throws IOException
    {
        int headerSize = 6 + 16 * sizes.length;
        ByteBuffer header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0);            // reserved
        header.putShort((short) 1);            // type: 1 = icon
        header.putShort((short) sizes.length);

        int offset = headerSize;
        for (int i = 0; i < sizes.length; i++)
        {
            int size = sizes[i];

            header.put((byte) (size >= 256 ? 0 : size)); // width, 0 means 256
            header.put((byte) (size >= 256 ? 0 : size)); // height
            header.put((byte) 0);              // palette colours: none
            header.put((byte) 0);              // reserved
            header.putShort((short) 1);        // colour planes
            header.putShort((short) 32);       // bits per pixel

            header.putInt(pngs.get(i).length);
            header.putInt(offset);

            offset += pngs.get(i).length;
        }

        try (OutputStream out = new DataOutputStream(Files.newOutputStream(file)))
        {
            out.write(header.array());
            for (byte[] png : pngs) out.write(png);
        }
    }
}
