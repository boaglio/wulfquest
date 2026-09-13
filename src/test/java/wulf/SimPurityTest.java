package wulf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * AGENTS.md §4 and §22.4: the simulation is pure. It never touches AWT, Swing
 * or sound, never uses java.util.Random, never reads the wall clock, and has no
 * float or double in any signature.
 */
class SimPurityTest {

    private static final Path CLASSES = Path.of("target", "classes");

    private static List<Path> classesIn(String pkg) throws IOException {
        Path dir = CLASSES.resolve(pkg);
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".class")).sorted().toList();
        }
    }

    private static String constantPool(Path classFile) throws IOException {
        // Class-file constant pools hold referenced class and member names as UTF-8.
        return new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
    }

    @Test
    void simWorldAndEngineNeverReferenceUiSoundOrRandom() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String pkg : List.of("wulf/sim", "wulf/world", "wulf/engine")) {
            List<Path> files = classesIn(pkg);
            assertThat(files).as("classes found in %s", pkg).isNotEmpty();
            for (Path f : files) {
                String pool = constantPool(f);
                for (String banned : List.of("java/awt/", "javax/swing/", "javax/sound/", "java/util/Random")) {
                    if (pool.contains(banned)) {
                        violations.add(f.getFileName() + " references " + banned);
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void theSimulationNeverReadsTheClock() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path f : classesIn("wulf/sim")) {
            String pool = constantPool(f);
            for (String banned : List.of("currentTimeMillis", "nanoTime", "java/time/")) {
                if (pool.contains(banned)) {
                    violations.add(f.getFileName() + " references " + banned);
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void noFloatOrDoubleInAnySimulationSignature() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String pkg : List.of("wulf/sim", "wulf/world", "wulf/engine")) {
            for (Path f : classesIn(pkg)) {
                String name = pkg.replace('/', '.') + "." + f.getFileName().toString().replace(".class", "");
                Class<?> type = Class.forName(name, false, getClass().getClassLoader());
                for (Field field : type.getDeclaredFields()) {
                    if (floating(field.getType())) {
                        violations.add(name + "." + field.getName());
                    }
                }
                for (Method m : type.getDeclaredMethods()) {
                    if (floating(m.getReturnType())) {
                        violations.add(name + "." + m.getName() + " returns " + m.getReturnType());
                    }
                    for (Class<?> p : m.getParameterTypes()) {
                        if (floating(p)) {
                            violations.add(name + "." + m.getName() + " takes " + p);
                        }
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    private static boolean floating(Class<?> t) {
        return t == float.class || t == double.class || t == Float.class || t == Double.class;
    }
}
