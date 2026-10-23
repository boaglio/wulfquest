package wulf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * DEVELOPER.md, Modules: core is the game for every platform, so none of it may lean on the
 * desktop's Java. The window, the keys, gamepads and the speaker belong to a platform module.
 */
class CorePurityTest {

    private static final Path CLASSES = Path.of("core", "target", "classes");
    private static final List<String> DESKTOP_ONLY =
            List.of("java/awt/", "javax/swing/", "javax/sound/", "javax/imageio/", "com/studiohartman/");

    @Test
    void nothingInCoreTouchesTheWindowTheKeysOrTheSpeaker() throws IOException {
        List<String> violations;
        try (Stream<Path> s = Files.walk(CLASSES)) {
            violations = s.filter(p -> p.toString().endsWith(".class"))
                    .flatMap(p -> {
                        String pool = constantPool(p);
                        return DESKTOP_ONLY.stream().filter(pool::contains)
                                .map(banned -> CLASSES.relativize(p) + " references " + banned);
                    })
                    .sorted().toList();
        }
        assertThat(violations).isEmpty();
    }

    private static String constantPool(Path classFile) {
        // Class-file constant pools hold referenced class and member names as UTF-8.
        try {
            return new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
