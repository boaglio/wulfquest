package wulf.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import wulf.input.RecordedInput;
import wulf.world.RoomAddress;

/**
 * A recorded run (AGENTS.md §22.6): where and with which seed it started, every
 * tick's input, and the simulation's state hash after every {@code checkpointEvery}
 * ticks. Replaying it through {@code ReplayRunner} must reproduce every hash; a
 * divergence means determinism broke (§6.4), and is never fixed by re-recording
 * until the cause is found.
 *
 * @param contentHash the content database's hash when recorded. Informational: a
 *                    replay may survive a content change that does not touch the
 *                    simulation, and its hashes decide
 */
public record Replay(
        int schemaVersion,
        String name,
        String note,
        String contentHash,
        long seed,
        String startRoom,
        boolean dev,
        int checkpointEvery,
        List<int[]> input,
        List<String> hashes) {

    public static final int SCHEMA_VERSION = 1;

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    public Replay {
        input = List.copyOf(input);
        hashes = List.copyOf(hashes);
    }

    public RoomAddress start() {
        String[] parts = startRoom.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("startRoom must be \"col,row\", got \"" + startRoom + "\"");
        }
        return new RoomAddress(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    public RecordedInput recorded() {
        return RecordedInput.of(input);
    }

    public static String hex(long hash) {
        return String.format("%016x", hash);
    }

    public static Replay read(Path file) {
        Replay r;
        try {
            r = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), Replay.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read replay " + file, e);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("replay " + file + " is malformed: " + e.getOriginalMessage(), e);
        }
        if (r.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("replay " + file + " has schemaVersion " + r.schemaVersion()
                    + "; this build reads " + SCHEMA_VERSION);
        }
        if (r.checkpointEvery() < 1) {
            throw new IllegalArgumentException("replay " + file + " has checkpointEvery " + r.checkpointEvery());
        }
        int ticks = r.recorded().ticks();
        if (r.hashes().size() != ticks / r.checkpointEvery()) {
            throw new IllegalArgumentException("replay " + file + " has " + ticks + " ticks but " + r.hashes().size()
                    + " hashes; expected " + ticks / r.checkpointEvery());
        }
        r.start();
        return r;
    }

    /** Written by hand so the input and hashes stay compact and diffable. */
    public void write(Path file) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"schemaVersion\": ").append(schemaVersion).append(",\n");
        sb.append("  \"name\": ").append(quote(name)).append(",\n");
        sb.append("  \"note\": ").append(quote(note)).append(",\n");
        sb.append("  \"contentHash\": ").append(quote(contentHash)).append(",\n");
        sb.append("  \"seed\": ").append(seed).append(",\n");
        sb.append("  \"startRoom\": ").append(quote(startRoom)).append(",\n");
        sb.append("  \"dev\": ").append(dev).append(",\n");
        sb.append("  \"checkpointEvery\": ").append(checkpointEvery).append(",\n");
        sb.append("  \"input\": [");
        for (int i = 0; i < input.size(); i++) {
            sb.append(i == 0 ? "\n    " : i % 10 == 0 ? ",\n    " : ", ");
            sb.append('[').append(input.get(i)[0]).append(", ").append(input.get(i)[1]).append(']');
        }
        sb.append(input.isEmpty() ? "],\n" : "\n  ],\n");
        sb.append("  \"hashes\": [");
        for (int i = 0; i < hashes.size(); i++) {
            sb.append(i == 0 ? "\n    " : i % 4 == 0 ? ",\n    " : ", ");
            sb.append(quote(hashes.get(i)));
        }
        sb.append(hashes.isEmpty() ? "]\n" : "\n  ]\n");
        sb.append("}\n");
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write replay " + file, e);
        }
    }

    private static String quote(String s) {
        return MAPPER.writeValueAsString(s);
    }
}
