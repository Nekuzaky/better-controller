import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Draws every BetterController branding asset. Run it from the repository root:
 *
 * <pre>java branding/BrandingGenerator.java</pre>
 *
 * The banner and the gallery cards are drawn with Minecraft's own bitmap font, dirt background and
 * bevelled panel border, so they look like they belong next to the game rather than like a generic
 * dark product card. Those assets are read out of the local Minecraft client JAR at generation
 * time and never copied into this repository.
 *
 * <p>The icon is deliberately not pixel art: it has to stay readable at 32 px in a mod list.
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

    /** Vanilla GUI panel colours. */
    private static final Color PANEL = new Color(0xC6C6C6);
    private static final Color PANEL_LIGHT = new Color(0xFFFFFF);
    private static final Color PANEL_SHADOW = new Color(0x555555);
    private static final Color MC_TEXT = new Color(0xFFFFFF);
    private static final Color MC_TEXT_DIM = new Color(0xA0A0A0);

    /** The icon is designed on a 512 grid; every other size is the same geometry, scaled. */
    private static final double DESIGN = 512.0D;

    private static BufferedImage fontTexture;
    private static int[] glyphAdvance;
    private static BufferedImage dirtTexture;
    private static final Map<Integer, BufferedImage> TINTED_FONTS = new HashMap<>();

    public static void main(String[] args) throws IOException {
        File outputDir = new File("branding");
        File modResource = new File("src/main/resources/assets/bettercontroller");
        outputDir.mkdirs();
        modResource.mkdirs();

        loadMinecraftAssets();

        writePixelIcon(new File(outputDir, "logo-512.png"), 512);
        writePixelIcon(new File(outputDir, "logo-256.png"), 256);
        writePixelIcon(new File(outputDir, "logo-128.png"), 128);
        writePixelIcon(new File(outputDir, "logo-64.png"), 64);
        writePixelIcon(new File(modResource, "icon.png"), 128);
        writeIcon(new File(outputDir, "logo-flat-512.png"), 512);
        writeBanner(new File(outputDir, "banner-1280x720.png"));

        File shots = new File("run/screenshots");
        writeGalleryCard(
            new File(shots, "2026-08-18_02.35.59.png"),
            new File(outputDir, "gallery-1-gameplay.png"),
            "Play with any gamepad",
            "Analog look and movement, with prompts that match your pad"
        );
        writeGalleryCard(
            new File(shots, "2026-08-18_02.35.37.png"),
            new File(outputDir, "gallery-2-overlay.png"),
            "See what the mod reads",
            "F8: raw axes, triggers, detected pad, slot and GUID"
        );
        writeGalleryCard(
            new File(shots, "2026-08-18_02.36.33.png"),
            new File(outputDir, "gallery-3-settings.png"),
            "Tune it without leaving the game",
            "The five values most people change. The rest lives in JSON"
        );

        System.out.println("Branding written to branding/ and src/main/resources/assets/bettercontroller/icon.png");
    }

    // ------------------------------------------------------------ pixel icon

    /**
     * The project avatar. CurseForge shows it around 64 px in search results, so it carries two
     * shapes and nothing else: a grass block, the one Minecraft cue readable at any size, and a
     * gamepad. No wordmark - the project name is already printed next to the avatar - and no
     * console marks, which CurseForge rejects as trademarked assets.
     *
     * <p>Drawn on a 32x32 grid and scaled by whole factors with nearest-neighbour, so every output
     * size lands on exact pixel boundaries.
     */
    private static void writePixelIcon(File file, int size) throws IOException {
        int grid = 32;
        BufferedImage base = new BufferedImage(grid, grid, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = base.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        paintGrassBlock(g, grid);
        paintPixelGamepad(g, grid);

        g.dispose();

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D out = image.createGraphics();
        out.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        out.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        out.drawImage(base, 0, 0, size, size, null);
        out.dispose();

        ImageIO.write(image, "png", file);
        System.out.println("  " + file.getPath() + "  " + size + "x" + size + " (pixel art)");
    }

    /** The icon sits on a grass block: real dirt and grass textures, with a jagged grass edge. */
    private static void paintGrassBlock(Graphics2D g, int grid) {
        for (int y = 0; y < grid; y += 16) {
            for (int x = 0; x < grid; x += 16) {
                g.drawImage(dirtTexture, x, y, 16, 16, null);
            }
        }
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(0, 0, grid, grid);

        int grassRows = 9;
        for (int y = 0; y < grassRows; y++) {
            for (int x = 0; x < grid; x++) {
                g.setColor(grassShade(x, y));
                g.fillRect(x, y, 1, 1);
            }
        }
        // Jagged grass/dirt boundary, the way the block side texture frays.
        int[] fringe = {1, 0, 1, 2, 1, 0, 0, 1, 2, 1, 0, 1, 1, 0, 2, 1, 0, 1, 2, 0, 1, 0, 1, 1, 2, 0, 1, 0, 0, 1, 2, 1};
        for (int x = 0; x < grid; x++) {
            for (int extra = 0; extra < fringe[x]; extra++) {
                g.setColor(grassShade(x, grassRows + extra));
                g.fillRect(x, grassRows + extra, 1, 1);
            }
        }
    }

    private static Color grassShade(int x, int y) {
        int noise = ((x * 7) + (y * 13)) % 3;
        return switch (noise) {
            case 0 -> new Color(0x6FAE4F);
            case 1 -> new Color(0x63A046);
            default -> new Color(0x7ABC58);
        };
    }

    /** The vector silhouette, rasterised without antialiasing so it becomes honest pixel art. */
    private static void paintPixelGamepad(Graphics2D g, int grid) {
        Area pad = new Area(new java.awt.Rectangle(5, 14, 22, 8));
        pad.add(new Area(new Ellipse2D.Double(3, 13, 10, 10)));
        pad.add(new Area(new Ellipse2D.Double(19, 13, 10, 10)));
        pad.add(new Area(new java.awt.Rectangle(4, 19, 6, 6)));
        pad.add(new Area(new java.awt.Rectangle(22, 19, 6, 6)));
        pad.subtract(new Area(new java.awt.Rectangle(12, 21, 8, 6)));

        // A 1 px outline, drawn by stamping the silhouette around itself: an offset drop shadow
        // leaks through the notch between the grips and reads as a black bar.
        g.setColor(INK);
        int[][] outline = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : outline) {
            g.translate(offset[0], offset[1]);
            g.fill(pad);
            g.translate(-offset[0], -offset[1]);
        }
        g.setColor(ACCENT);
        g.fill(pad);

        // D-pad.
        g.setColor(new Color(0x4E8F2A));
        g.fillRect(7, 16, 5, 2);
        g.fillRect(8, 15, 2, 4);

        // Face buttons.
        g.setColor(SLATE);
        g.fillRect(21, 15, 2, 2);
        g.fillRect(24, 17, 2, 2);
        g.fillRect(21, 19, 2, 2);
        g.fillRect(18, 17, 2, 2);
    }

    // ------------------------------------------------------------------ icon

    private static void writeIcon(File file, int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createGraphics(image);
        g.scale(size / DESIGN, size / DESIGN);

        g.setPaint(new GradientPaint(0, 0, BACKDROP_TOP, 0, 512, BACKDROP_BOTTOM));
        g.fill(new RoundRectangle2D.Double(0, 0, 512, 512, 96, 96));
        paintGlow(g, 256, 268, 250);
        paintGamepad(g, 256, 268, 1.0D);

        g.dispose();
        ImageIO.write(image, "png", file);
        System.out.println("  " + file.getPath() + "  " + size + "x" + size);
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

    private static void paintGamepad(Graphics2D g, double centerX, double centerY, double scale) {
        AffineTransform saved = g.getTransform();
        g.translate(centerX, centerY);
        g.scale(scale, scale);

        g.setColor(SLATE);
        g.fill(new RoundRectangle2D.Double(-108, -112, 76, 46, 24, 24));
        g.fill(new RoundRectangle2D.Double(32, -112, 76, 46, 24, 24));

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

        g.setColor(GREEN);
        g.fill(new RoundRectangle2D.Double(-116, -44, 72, 24, 9, 9));
        g.fill(new RoundRectangle2D.Double(-92, -68, 24, 72, 9, 9));

        g.setColor(SLATE);
        double faceX = 80;
        double faceY = -32;
        double spread = 30;
        double r = 12.5;
        g.fill(new Ellipse2D.Double(faceX - r, faceY - spread - r, r * 2, r * 2));
        g.fill(new Ellipse2D.Double(faceX - r, faceY + spread - r, r * 2, r * 2));
        g.fill(new Ellipse2D.Double(faceX - spread - r, faceY - r, r * 2, r * 2));
        g.fill(new Ellipse2D.Double(faceX + spread - r, faceY - r, r * 2, r * 2));

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

    // --------------------------------------------------------------- banner

    private static void writeBanner(File file) throws IOException {
        int width = 1280;
        int height = 720;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createGraphics(image);

        paintDirt(g, width, height);

        int padCenterX = 340;
        int padCenterY = 330;
        paintGlow(g, padCenterX, padCenterY, 380);
        paintGamepad(g, padCenterX, padCenterY, 1.05D);

        int textX = 640;
        drawText(g, "BetterController", textX, 250, 6, MC_TEXT);
        drawText(g, "Play Minecraft Java", textX, 330, 3, MC_TEXT_DIM);
        drawText(g, "with any gamepad", textX, 366, 3, MC_TEXT_DIM);

        String[] badges = {"1.21.11", "Fabric", "MIT"};
        int badgeX = textX;
        for (String badge : badges) {
            badgeX += paintBadge(g, badge, badgeX, 430) + 16;
        }

        drawText(g, "Client-side  -  no server install", textX, 520, 2, MC_TEXT_DIM);

        g.dispose();
        ImageIO.write(image, "png", file);
        System.out.println("  " + file.getPath() + "  " + width + "x" + height);
    }

    private static int paintBadge(Graphics2D g, String label, int x, int y) {
        int scale = 2;
        int textWidth = textWidth(label, scale);
        int width = textWidth + 24;
        int height = 34;
        paintPanel(g, x, y, width, height, 2);
        drawText(g, label, x + 12, y + 10, scale, MC_TEXT);
        return width;
    }

    // --------------------------------------------------------- gallery cards

    /**
     * Frames an in-game screenshot the way Minecraft frames its own panels. The caption sits below
     * the frame rather than over the screenshot: the game's HUD runs along the bottom edge of every
     * in-game shot, which is both where a caption would land and the thing these cards exist to
     * show. Skipped when the source is missing, so the generator still runs on a fresh clone.
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

        paintDirt(g, width, height);

        int frameX = 72;
        int frameY = 16;
        int frameWidth = 1136;
        int frameHeight = 639;
        paintPanel(g, frameX - 6, frameY - 6, frameWidth + 12, frameHeight + 12, 3);

        double scale = Math.max(frameWidth / (double) shot.getWidth(), frameHeight / (double) shot.getHeight());
        int drawWidth = (int) Math.round(shot.getWidth() * scale);
        int drawHeight = (int) Math.round(shot.getHeight() * scale);
        java.awt.Shape savedClip = g.getClip();
        g.clip(new Rectangle2D.Double(frameX, frameY, frameWidth, frameHeight));
        g.drawImage(
            shot,
            frameX + (frameWidth - drawWidth) / 2,
            frameY + (frameHeight - drawHeight) / 2,
            drawWidth,
            drawHeight,
            null
        );
        g.setClip(savedClip);

        // drawText anchors the top of the glyph row, not a baseline: 24 px for scale 3,
        // 16 px for scale 2, both inside the 720 px card.
        drawText(g, title, frameX, 666, 3, MC_TEXT);
        drawText(g, subtitle, frameX, 696, 2, MC_TEXT_DIM);

        g.dispose();
        ImageIO.write(image, "png", out);
        System.out.println("  " + out.getPath() + "  " + width + "x" + height);
    }

    // ------------------------------------------------------ Minecraft assets

    /** Tiled dirt, tinted the way the vanilla menu background is. */
    private static void paintDirt(Graphics2D g, int width, int height) {
        int tile = 32;
        Object saved = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int y = 0; y < height; y += tile) {
            for (int x = 0; x < width; x += tile) {
                g.drawImage(dirtTexture, x, y, tile, tile, null);
            }
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, saved);
        g.setColor(new Color(0, 0, 0, 168));
        g.fill(new Rectangle2D.Double(0, 0, width, height));
    }

    /** A vanilla-looking bevelled panel: light top-left, shadow bottom-right, black outline. */
    private static void paintPanel(Graphics2D g, int x, int y, int width, int height, int bevel) {
        g.setColor(Color.BLACK);
        g.fill(new Rectangle2D.Double(x - 1, y - 1, width + 2, height + 2));
        g.setColor(PANEL);
        g.fill(new Rectangle2D.Double(x, y, width, height));
        g.setColor(PANEL_LIGHT);
        g.fill(new Rectangle2D.Double(x, y, width, bevel));
        g.fill(new Rectangle2D.Double(x, y, bevel, height));
        g.setColor(PANEL_SHADOW);
        g.fill(new Rectangle2D.Double(x, y + height - bevel, width, bevel));
        g.fill(new Rectangle2D.Double(x + width - bevel, y, bevel, height));
    }

    /** Draws text in Minecraft's bitmap font, with its drop shadow. */
    private static void drawText(Graphics2D g, String text, int x, int y, int scale, Color color) {
        drawGlyphs(g, text, x + scale, y + scale, scale, shadowOf(color));
        drawGlyphs(g, text, x, y, scale, color);
    }

    /** Minecraft darkens a text colour to a quarter for its shadow. */
    private static Color shadowOf(Color color) {
        return new Color((color.getRGB() & 0xFCFCFC) >> 2);
    }

    private static void drawGlyphs(Graphics2D g, String text, int x, int y, int scale, Color color) {
        BufferedImage atlas = tintedFont(color);
        Object saved = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 255) {
                continue;
            }
            if (c == ' ') {
                cursor += 4 * scale;
                continue;
            }
            int column = c % 16;
            int row = c / 16;
            g.drawImage(
                atlas,
                cursor, y, cursor + (8 * scale), y + (8 * scale),
                column * 8, row * 8, (column * 8) + 8, (row * 8) + 8,
                null
            );
            cursor += glyphAdvance[c] * scale;
        }

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, saved);
    }

    private static int textWidth(String text, int scale) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 255) {
                continue;
            }
            total += (c == ' ' ? 4 : glyphAdvance[c]) * scale;
        }
        return total;
    }

    private static BufferedImage tintedFont(Color color) {
        return TINTED_FONTS.computeIfAbsent(color.getRGB(), rgb -> {
            BufferedImage tinted = new BufferedImage(
                fontTexture.getWidth(), fontTexture.getHeight(), BufferedImage.TYPE_INT_ARGB
            );
            for (int y = 0; y < fontTexture.getHeight(); y++) {
                for (int x = 0; x < fontTexture.getWidth(); x++) {
                    int alpha = (fontTexture.getRGB(x, y) >>> 24);
                    tinted.setRGB(x, y, (alpha << 24) | (rgb & 0xFFFFFF));
                }
            }
            return tinted;
        });
    }

    /**
     * Reads the font and dirt textures out of the local Minecraft client JAR. They are used at
     * generation time only and never committed here, so no Mojang asset is redistributed by this
     * repository.
     */
    private static void loadMinecraftAssets() throws IOException {
        Path jar = findClientJar();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            fontTexture = readEntry(zip, "assets/minecraft/textures/font/ascii.png");
            dirtTexture = readEntry(zip, "assets/minecraft/textures/block/dirt.png");
        }
        glyphAdvance = measureGlyphs(fontTexture);
        System.out.println("  using Minecraft assets from " + jar);
    }

    private static BufferedImage readEntry(ZipFile zip, String name) throws IOException {
        var entry = zip.getEntry(name);
        if (entry == null) {
            throw new IOException("Missing " + name + " in " + zip.getName());
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return ImageIO.read(in);
        }
    }

    /** Minecraft derives each glyph's advance from its rightmost non-empty column, plus a pixel. */
    private static int[] measureGlyphs(BufferedImage atlas) {
        int[] advance = new int[256];
        for (int code = 0; code < 256; code++) {
            int column = code % 16;
            int row = code / 16;
            int rightmost = -1;
            for (int x = 7; x >= 0; x--) {
                boolean empty = true;
                for (int y = 0; y < 8; y++) {
                    if ((atlas.getRGB((column * 8) + x, (row * 8) + y) >>> 24) != 0) {
                        empty = false;
                        break;
                    }
                }
                if (!empty) {
                    rightmost = x;
                    break;
                }
            }
            advance[code] = rightmost < 0 ? 4 : rightmost + 2;
        }
        return advance;
    }

    private static Path findClientJar() throws IOException {
        String override = System.getenv("MINECRAFT_CLIENT_JAR");
        if (override != null && Files.isRegularFile(Path.of(override))) {
            return Path.of(override);
        }

        String version = readMinecraftVersion();
        Path home = Path.of(System.getProperty("user.home"));
        Path[] candidates = {
            home.resolve(".gradle/caches/fabric-loom/" + version + "/minecraft-client.jar"),
            home.resolve("AppData/Roaming/.minecraft/versions/" + version + "/" + version + ".jar"),
            home.resolve(".minecraft/versions/" + version + "/" + version + ".jar")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IOException(
            "Minecraft client JAR for " + version + " not found. Run ./gradlew build first, "
                + "or set MINECRAFT_CLIENT_JAR to its path."
        );
    }

    private static String readMinecraftVersion() throws IOException {
        for (String line : Files.readAllLines(Path.of("gradle.properties"))) {
            if (line.startsWith("minecraft_version=")) {
                return line.substring("minecraft_version=".length()).trim();
            }
        }
        throw new IOException("minecraft_version not found in gradle.properties");
    }

    private static Graphics2D createGraphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g;
    }

    private BrandingGenerator() {
    }
}
