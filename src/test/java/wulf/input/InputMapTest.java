package wulf.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wulf.data.DataException;
import wulf.data.InputConfig;
import wulf.data.JsonDb;

/** AGENTS.md §19.1 — key bindings. */
class InputMapTest {

    private final InputConfig config = new JsonDb(Path.of("data")).load("config/input", InputConfig.class);

    @Test
    void theModernProfileBindsArrowsAndWasd() {
        InputMap map = new InputMap(config);
        assertThat(map.held(InputMap.Action.LEFT, Set.of(KeyEvent.VK_LEFT))).isTrue();
        assertThat(map.held(InputMap.Action.LEFT, Set.of(KeyEvent.VK_A))).isTrue();
        assertThat(map.held(InputMap.Action.FIRE, Set.of(KeyEvent.VK_SPACE))).isTrue();
        assertThat(map.held(InputMap.Action.FIRE, Set.of(KeyEvent.VK_LEFT))).isFalse();
    }

    @Test
    void thePeriodProfileIsQaopM() {
        InputMap map = new InputMap(new InputConfig(1, "period", config.profiles(), config.gamepad()));
        assertThat(map.held(InputMap.Action.UP, Set.of(KeyEvent.VK_Q))).isTrue();
        assertThat(map.held(InputMap.Action.RIGHT, Set.of(KeyEvent.VK_P))).isTrue();
        assertThat(map.held(InputMap.Action.FIRE, Set.of(KeyEvent.VK_M))).isTrue();
    }

    @Test
    void anUnknownActiveProfileIsADataError() {
        DataException e = catchThrowableOfType(DataException.class,
                () -> new InputMap(new InputConfig(1, "arcade", config.profiles(), config.gamepad())));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/active");
    }

    @Test
    void oneKeyForTwoActionsIsADataError() {
        InputConfig.Profile m = config.profiles().get("modern");
        InputConfig.Profile clash = new InputConfig.Profile(m.up(), m.down(), m.left(), m.right(),
                List.of("SPACE"), List.of("SPACE"), m.quit(), m.devKill(), m.devMask());
        DataException e = catchThrowableOfType(DataException.class,
                () -> new InputMap(new InputConfig(1, "clash", Map.of("clash", clash), config.gamepad())));
        assertThat(e).isNotNull();
        assertThat(e.detail()).contains("FIRE").contains("PAUSE");
    }

    @Test
    void resolvesLettersDigitsAndNamedKeys() {
        assertThat(InputMap.codeOf("Q", "/x")).isEqualTo(KeyEvent.VK_Q);
        assertThat(InputMap.codeOf("7", "/x")).isEqualTo(KeyEvent.VK_7);
        assertThat(InputMap.codeOf("ESCAPE", "/x")).isEqualTo(KeyEvent.VK_ESCAPE);
        DataException e = catchThrowableOfType(DataException.class, () -> InputMap.codeOf("F13", "/p/0"));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/p/0");
    }
}
