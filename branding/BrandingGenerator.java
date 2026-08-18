import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws every BetterController branding asset. Run it from the repository root:
 *
 * <pre>java branding/BrandingGenerator.java</pre>
 *
 * Everything is vector geometry scaled to the requested size, so each file is rendered at its
 * native resolution rather than downscaled from one master - small icons stay crisp.
 *
 * <p>The palette is the one the mod already draws its HUD with, so the branding and the in-game
 * overlay look like the same product.
 */
public final class BrandingGenerator {
    /** HUD prompt accent, ControllerHUDRenderer. */
    private static final Color ACCENT = new Color(0xE2E8F0);
    /** HUD chip fill. */
    private static final Color INK = new Color(0x0B0F14);
    /** HUD chip border. */
    private static final Color SLATE = new Color(0x4E647A);
    /** Minecraft green, the README badge. */
    private static final Color GREEN = new Color(0x62B132);
    private static final Color BACKDROP_TOP = new Color(0x1B2430);
    private static final Color BACKDROP_BOTTOM = new Color(0x0E141C);

    /** The icon is designed on a 512 grid; every other size is the same geometry, scaled. */
    private static final double DESIGN = 512.0D;

    public static void main(String[] args) throws IOException {
        File outputDir = new File("branding");
        File modResource = new File("src/main/resources/assets/bettercontroller");
        outputDir.mkdirs();
        modResource.mkdirs();

        writeIcon(new File(outputDir, "logo-512.png"), 512);
        writeIcon(new File(outputDir, "logo-256.png"), 256);
        writeIcon(new File(outputDir, "logo-128.png"), 128);
        writeIcon(new File(outputDir, "logo-64.png"), 64);
        writeIcon(new File(modResource, "icon.png"), 128);
        writeBanner(new File(outputDir, "banner-1280x720.png"));

        File shots = new File("run/screenshots");
        writeGalleryCard(
            new File(shots, "2026-08-18_02.35.59.png"),
            new File(outputDir, "gallery-1-gameplay.png"),
            "Play with any gamepad",
            "Analog look and movement, with button prompts that match your controller."
        );
        writeGalleryCard(
            new File(shots, "2026-08-18_02.35.37.png"),
            new File(outputDir, "gallery-2-overlay.png"),
            "See exactly what the mod reads",
            "F8 shows raw axes, triggers, the detected pad, its slot and its GUID."
        );
        writeGalleryCard(
            new File(shots, "2026-08-18_02.36.33.png"),
            new File(outputDir, "gallery-3-settings.png"),
            "Tuning without leaving the game",
            "The five values most people change. Everything else lives in bettercontroller.json."
        );

        System.out.println("Branding written to branding/ and src/main/resources/assets/bettercontroller/icon.png");
    }

    private static void writeIcon(File file, int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createGraphics(image);
        g.scale(size / DESIGN, size / DESIGN);

        paintBackdrop(g, 512, 512, 96);
        paintGlow(g, 256, 268, 250);
        paintGamepad(g, 256, 268, 1.0D);

        g.dispose();
        ImageIO.write(image, "png", file);
        System.out.println("  " + file.getPath() + "  " + size + "x" + size);
    }

    private static void writeBanner(File file) throws IOException {
        int width = 1280;
        int height = 720;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createGraphics(image);

        paintBackdrop(g, width, height, 0);
        paintGlow(g, 330, 360, 420);
        paintGamepad(g, 330, 360, 1.15D);

        int textX = 620;
        g.setColor(ACCENT);
        g.setFont(displayFont(Font.BOLD, 70));
        g.drawString("BetterController", textX, 322);

        g.setColor(new Color(0x9FB3C8));
        g.setFont(displayFont(Font.PLAIN, 30));
        g.drawString("Play Minecraft Java with any gamepad.", textX, 376);

        String[] badges = {"Minecraft 1.21.11", "Fabric", "MIT"};
        int badgeX = textX;
        for (String badge : badges) {
            badgeX += paintBadge(g, badge, badgeX, 430) + 14;
        }

        g.dispose();
        ImageIO.write(image, "png", file);
        System.out.println("  " + file.getPath() + "  " + width + "x" + height);
    }

    /**
     * Frames an in-game screenshot as a gallery card: the shot fills the frame, a scrim at the
     * bottom keeps the caption readable whatever the screenshot shows behind it. Skipped silently
     * when the source is missing, so the generator still runs on a fresh clone.
     */
    private static void writeGalleryCard(File source, File out, String title, String subtitle) throws IOException {
        if (!source.isFile()) {
            System.out.println("  skipped " + out.getName() + " - missing " + source.getPath());
            return;
        }

        int width = 1280;
        int height = 720;
        BufferedImage shot = ImageIO.read(source);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createGraphics(image);

        paintBackdrop(g, width, height, 0);

        // The screenshot is framed rather than full-bleed: Minecraft's own HUD lives along the
        // bottom edge, which is exactly where a caption would sit and exactly what these images
        // are meant to show off.
        int frameX = 80;
        int frameY = 10;
        int frameWidth = 1120;
        int frameHeight = 630;
        double scale = Math.max(frameWidth / (double) shot.getWidth(), frameHeight / (double) shot.getHeight());
        int drawWidth = (int) Math.round(shot.getWidth() * scale);
        int drawHeight = (int) Math.round(shot.getHeight() * scale);

        java.awt.Shape savedClip = g.getClip();
        g.clip(new RoundRectangle2D.Double(frameX, frameY, frameWidth, frameHeight, 18, 18));
        g.drawImage(
            shot,
            frameX + (frameWidth - drawWidth) / 2,
            frameY + (frameHeight - drawHeight) / 2,
            drawWidth,
            drawHeight,
            null
        );
        g.setClip(savedClip);

        g.setColor(SLATE);
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(frameX, frameY, frameWidth, frameHeight, 18, 18));

        g.setColor(GREEN);
        g.fill(new RoundRectangle2D.Double(frameX, 660, 6, 48, 6, 6));

        g.setColor(ACCENT);
        g.setFont(displayFont(Font.BOLD, 29));
        g.drawString(title, frameX + 20, 685);

        g.setColor(new Color(0x9FB3C8));
        g.setFont(displayFont(Font.PLAIN, 19));
        g.drawString(subtitle, frameX + 20, 709);

        g.dispose();
        ImageIO.write(image, "png", out);
        System.out.println("  " + out.getPath() + "  " + width + "x" + height);
    }

    private static int paintBadge(Graphics2D g, String label, int x, int y) {
        g.setFont(displayFont(Font.BOLD, 22));
        int textWidth = g.getFontMetrics().stringWidth(label);
        int width = textWidth + 32;
        int height = 40;

        g.setColor(new Color(0x1F2A38));
        g.fill(new RoundRectangle2D.Double(x, y, width, height, 20, 20));
        g.setColor(SLATE);
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(x, y, width, height, 20, 20));
        g.setColor(ACCENT);
        g.drawString(label, x + 16, y + 27);
        return width;
    }

    private static void paintBackdrop(Graphics2D g, int width, int height, int corner) {
        g.setPaint(new GradientPaint(0, 0, BACKDROP_TOP, 0, height, BACKDROP_BOTTOM));
        if (corner > 0) {
            g.fill(new RoundRectangle2D.Double(0, 0, width, height, corner, corner));
        } else {
            g.fill(new Rectangle2D.Double(0, 0, width, height));
        }
    }

    private static void paintGlow(Graphics2D g, double centerX, double centerY, double radius) {
        g.setPaint(new RadialGradientPaint(
            new Point2D.Double(centerX, centerY),
            (float) radius,
            new float[] {0.0f, 1.0f},
            new Color[] {new Color(98, 177, 50, 70), new Color(98, 177, 50, 0)}
        ));
        g.fill(new Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2, radius * 2));
    }

    /**
     * The pad is built as one filled silhouette (body, grip lobes, handles) so the outline stays
     * continuous, then the controls are drawn on top.
     */
    private static void paintGamepad(Graphics2D g, double centerX, double centerY, double scale) {
        AffineTransform saved = g.getTransform();
        g.translate(centerX, centerY);
        g.scale(scale, scale);

        // Shoulder buttons, tucked behind the top edge of the body.
        g.setColor(SLATE);
        g.fill(new RoundRectangle2D.Double(-108, -112, 76, 46, 24, 24));
        g.fill(new RoundRectangle2D.Double(32, -112, 76, 46, 24, 24));

        // One continuous silhouette: body, grip lobes, angled handles, minus the notch between them.
        Area pad = new Area(new RoundRectangle2D.Double(-130, -78, 260, 150, 60, 60));
        pad.add(new Area(new Ellipse2D.Double(-202, -75, 144, 144)));
        pad.add(new Area(new Ellipse2D.Double(58, -75, 144, 144)));
        pad.add(new Area(rotated(new RoundRectangle2D.Double(-170, -20, 78, 155, 58, 58), 15, -132, -8)));
        pad.add(new Area(rotated(new RoundRectangle2D.Double(92, -20, 78, 155, 58, 58), -15, 132, -8)));
        pad.subtract(new Area(new Ellipse2D.Double(-66, 62, 132, 132)));

        g.setColor(ACCENT);
        g.fill(pad);
        g.setColor(INK);
        g.setStroke(new BasicStroke(7f));
        g.draw(pad);

        // D-pad, upper left.
        g.setColor(GREEN);
        g.fill(new RoundRectangle2D.Double(-116, -44, 72, 24, 9, 9));
        g.fill(new RoundRectangle2D.Double(-92, -68, 24, 72, 9, 9));

        // Face buttons, upper right.
        g.setColor(SLATE);
        double faceX = 80;
        double faceY = -32;
        double spread = 30;
        double r = 12.5;
        g.fill(new Ellipse2D.Double(faceX - r, faceY - spread - r, r * 2, r * 2));
        g.fill(new Ellipse2D.Double(faceX - r, faceY + spread - r, r * 2, r * 2));
        g.fill(new Ellipse2D.Double(faceX - spread - r, faceY - r, r * 2, r * 2));
        g.fill(new Ellipse2D.Double(faceX + spread - r, faceY - r, r * 2, r * 2));

        // Sticks, the part of the pad this mod is actually about - kept inside the body outline.
        paintStick(g, -44, 38);
        paintStick(g, 44, 38);

        g.setTransform(saved);
    }

    private static void paintStick(Graphics2D g, double x, double y) {
        double outer = 24;
        double inner = 16;
        g.setColor(INK);
        g.fill(new Ellipse2D.Double(x - outer, y - outer, outer * 2, outer * 2));
        g.setColor(SLATE);
        g.fill(new Ellipse2D.Double(x - inner, y - inner, inner * 2, inner * 2));
    }

    private static java.awt.Shape rotated(java.awt.Shape shape, double degrees, double anchorX, double anchorY) {
        return AffineTransform
            .getRotateInstance(Math.toRadians(degrees), anchorX, anchorY)
            .createTransformedShape(shape);
    }

    private static Font displayFont(int style, int size) {
        Font font = new Font("Segoe UI", style, size);
        if ("Dialog".equals(font.getFamily())) {
            return new Font(Font.SANS_SERIF, style, size);
        }
        return font;
    }

    private static Graphics2D createGraphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g;
    }

    private BrandingGenerator() {
    }
}

