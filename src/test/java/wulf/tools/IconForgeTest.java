package wulf.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.SpriteData;
import wulf.render.AppIcon;

/** AGENTS.md §28.3: the icon is the whole amulet, and its files are what each OS reads. */
class IconForgeTest {

    private static SpriteData amulet;
    private static int[] argb;

    @BeforeAll
    static void load() {
        Content c = Content.load(Path.of("data"));
        amulet = c.sprites().get(c.landmarks().amulet().sprite());
        argb = c.palette().toArgb();
    }

    @Test
    void theIconIsTheAmuletOnATransparentGround() {
        BufferedImage img = AppIcon.draw(amulet, argb, 64);
        assertThat(img.getRGB(0, 0) >>> 24).as("corner").isZero();
        assertThat(img.getRGB(32, 32) >>> 24).as("the hole in the middle").isZero();
        // Every quarter is there, each in its guardian's colour (§14.6).
        int[] quarters = {img.getRGB(16, 20), img.getRGB(48, 20), img.getRGB(16, 44), img.getRGB(48, 44)};
        assertThat(Arrays.stream(quarters).map(c -> c >>> 24)).containsOnly(0xFF);
        assertThat(Arrays.stream(quarters).distinct().count()).isEqualTo(4);
    }

    @Test
    void theWindowsIconListsItsPngs() throws IOException {
        byte[] ico = IconForge.ico(amulet, argb);
        ByteBuffer b = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(b.getShort(0)).isZero();
        assertThat(b.getShort(2)).isEqualTo((short) 1);
        int count = b.getShort(4);
        assertThat(count).isEqualTo(6);
        for (int i = 0; i < count; i++) {
            int entry = 6 + 16 * i;
            int size = Byte.toUnsignedInt(ico[entry]);
            int len = b.getInt(entry + 8);
            int at = b.getInt(entry + 12);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(Arrays.copyOfRange(ico, at, at + len)));
            assertThat(img.getWidth()).isEqualTo(size == 0 ? 256 : size);
        }
    }

    @Test
    void theMacIconAddsUp() throws IOException {
        byte[] icns = IconForge.icns(amulet, argb);
        ByteBuffer b = ByteBuffer.wrap(icns);
        assertThat(new String(icns, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("icns");
        assertThat(b.getInt(4)).isEqualTo(icns.length);
        int at = 8;
        int entries = 0;
        while (at < icns.length) {
            int len = b.getInt(at + 4);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(Arrays.copyOfRange(icns, at + 8, at + len)));
            assertThat(img).isNotNull();
            at += len;
            entries++;
        }
        assertThat(at).isEqualTo(icns.length);
        assertThat(entries).isEqualTo(8);
    }
}
