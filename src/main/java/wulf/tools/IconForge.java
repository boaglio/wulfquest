package wulf.tools;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import wulf.Content;
import wulf.data.DataException;
import wulf.data.SpriteData;
import wulf.render.AppIcon;

/**
 * Writes the packaged apps' icons (AGENTS.md §28.3) from the amulet's sprite JSON:
 * {@code WulfQuest.png} for Linux, {@code WulfQuest.ico} for Windows and
 * {@code WulfQuest.icns} for macOS, each holding PNG images.
 *
 * <p>Build output only. The release workflow writes them under {@code target/}, which
 * the no-binaries gate (§2.4) does not look at; written anywhere else in the
 * repository they fail the build, as they should.
 *
 * <pre>
 *   java -cp wulfquest.jar wulf.tools.IconForge OUT_DIR
 * </pre>
 */
public final class IconForge {

    /** Sizes in the Windows icon; 256 is the largest Explorer asks for. */
    private static final List<Integer> ICO_SIZES = List.of(16, 32, 48, 64, 128, 256);

    /** macOS icon types that hold a PNG, and the size each one is. */
    private record IcnsType(String code, int size) {
    }

    private static final List<IcnsType> ICNS_TYPES = List.of(
            new IcnsType("ic11", 32), new IcnsType("ic12", 64), new IcnsType("ic07", 128),
            new IcnsType("ic13", 256), new IcnsType("ic08", 256), new IcnsType("ic14", 512),
            new IcnsType("ic09", 512), new IcnsType("ic10", 1024));

    private IconForge() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: IconForge OUT_DIR");
            System.exit(64);
        }
        Content c;
        try {
            // The working tree's data/ when there is one, else the jar's own copy (§20.1).
            Path local = Path.of("data");
            c = Content.load(Files.isDirectory(local) ? local : null);
        } catch (DataException e) {
            System.err.println("DATA ERROR\n  file    : " + e.file() + "\n  pointer : " + e.pointer()
                    + "\n  problem : " + e.detail());
            System.exit(2);
            return;
        }
        SpriteData amulet = c.sprites().get(c.landmarks().amulet().sprite());
        int[] argb = c.palette().toArgb();
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        Files.write(out.resolve("WulfQuest.png"), png(AppIcon.draw(amulet, argb, 512)));
        Files.write(out.resolve("WulfQuest.ico"), ico(amulet, argb));
        Files.write(out.resolve("WulfQuest.icns"), icns(amulet, argb));
        System.out.println("IconForge: WulfQuest.png, .ico and .icns in " + out);
    }

    static byte[] png(BufferedImage img) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** An ICO directory of PNG entries (Windows Vista and later), little-endian. */
    static byte[] ico(SpriteData amulet, int[] argb) {
        byte[][] images = new byte[ICO_SIZES.size()][];
        int total = 6 + 16 * images.length;
        for (int i = 0; i < images.length; i++) {
            images[i] = png(AppIcon.draw(amulet, argb, ICO_SIZES.get(i)));
            total += images[i].length;
        }
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) images.length);
        int offset = 6 + 16 * images.length;
        for (int i = 0; i < images.length; i++) {
            int s = ICO_SIZES.get(i);
            b.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s));   // 0 means 256
            b.put((byte) 0).put((byte) 0);                                         // no palette, reserved
            b.putShort((short) 1).putShort((short) 32);                            // planes, bits per pixel
            b.putInt(images[i].length).putInt(offset);
            offset += images[i].length;
        }
        for (byte[] img : images) {
            b.put(img);
        }
        return b.array();
    }

    /** An ICNS file of PNG entries, big-endian: "icns", the length, then type, length, data. */
    static byte[] icns(SpriteData amulet, int[] argb) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (IcnsType t : ICNS_TYPES) {
            byte[] img = png(AppIcon.draw(amulet, argb, t.size()));
            body.writeBytes(t.code().getBytes(StandardCharsets.US_ASCII));
            body.writeBytes(ByteBuffer.allocate(4).putInt(8 + img.length).array());
            body.writeBytes(img);
        }
        ByteBuffer b = ByteBuffer.allocate(8 + body.size());
        b.put("icns".getBytes(StandardCharsets.US_ASCII)).putInt(8 + body.size());
        b.put(body.toByteArray());
        return b.array();
    }
}
