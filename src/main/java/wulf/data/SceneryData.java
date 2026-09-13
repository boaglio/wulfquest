package wulf.data;

import java.util.List;
import java.util.Map;

/** Bound view of {@code data/world/scenery.json} (AGENTS.md §9, §7.3). */
public record SceneryData(int schemaVersion, int solidCoveragePercent, Map<String, SceneryObject> objects) {

    public SceneryData {
        objects = Map.copyOf(objects);
    }

    public record Cells(int w, int h) {
    }

    /**
     * One placeable maze piece. {@code collisionCells} is optional: when absent,
     * collision is derived from the sprite's art (§7.3).
     */
    public record SceneryObject(
            Cells cells,
            String sprite,
            String subject,
            String biomeHint,
            List<String> collisionCells,
            String fidelity) {

        public SceneryObject {
            collisionCells = collisionCells == null ? null : List.copyOf(collisionCells);
        }
    }
}
