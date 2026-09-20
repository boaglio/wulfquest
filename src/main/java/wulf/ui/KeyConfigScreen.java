package wulf.ui;

import java.util.List;
import java.util.Map;
import wulf.data.InputConfig;
import wulf.data.ShellConfig;
import wulf.render.Framebuffer;

/**
 * The keys page (AGENTS.md §17.1 {@code KEY_CONFIG}, §19.1). It shows every
 * profile {@code input.json} defines side by side and lets the player pick one
 * with a digit; the choice is written to {@code settings.json} and survives the
 * session.
 *
 * <p>The period layout — Q/A/O/P/M — is offered here as the nod §19.1 asks for,
 * and is never the default.
 */
public final class KeyConfigScreen {

    private final Chrome chrome;
    private final ShellConfig.KeyConfig config;

    public KeyConfigScreen(Chrome chrome, ShellConfig.KeyConfig config) {
        this.chrome = chrome;
        this.config = config;
    }

    public void paint(Framebuffer fb, InputConfig input, String active) {
        int y = chrome.page(fb, config.heading());
        List<String> profiles = List.copyOf(input.profiles().keySet());
        int labelX = 24;
        int firstColumn = 140;
        int columnW = 76;

        for (int p = 0; p < profiles.size(); p++) {
            String name = profiles.get(p);
            boolean chosen = name.equals(active);
            String header = (p + 1) + " " + name.toUpperCase(java.util.Locale.ROOT) + (chosen ? " *" : "");
            chrome.small().draw(fb, header, firstColumn + p * columnW, y, chosen ? chrome.bright() : chrome.dim());
        }
        y += chrome.small().lineHeight() * 2;

        for (String action : config.actionOrder()) {
            chrome.small().draw(fb, action, labelX, y, chrome.body());
            for (int p = 0; p < profiles.size(); p++) {
                InputConfig.Profile profile = input.profiles().get(profiles.get(p));
                String keys = String.join(" ", keysFor(profile, action));
                boolean chosen = profiles.get(p).equals(active);
                chrome.small().draw(fb, keys, firstColumn + p * columnW, y,
                        chosen ? chrome.bright() : chrome.dim());
            }
            y += chrome.small().lineHeight() * 2;
        }
        chrome.footer(fb, config.prompt());
    }

    /** The profile's bindings for one action name; unknown names are a data error, caught at load. */
    public static List<String> keysFor(InputConfig.Profile profile, String action) {
        Map<String, List<String>> byAction = Map.of(
                "UP", profile.up(), "DOWN", profile.down(), "LEFT", profile.left(), "RIGHT", profile.right(),
                "FIRE", profile.fire(), "PAUSE", profile.pause(), "QUIT", profile.quit());
        List<String> keys = byAction.get(action);
        if (keys == null) {
            throw new IllegalArgumentException("no action '" + action + "' in an input profile");
        }
        return keys;
    }
}
