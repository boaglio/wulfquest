package wulf.data;

import java.util.List;
import java.util.Map;

/**
 * Bound view of {@code data/world/original_map.json} — extracted canon
 * (AGENTS.md §8). Do not edit the file; do not reshape this record.
 */
public record OriginalMap(
        int gridW,
        int gridH,
        int startCol,
        int startRow,
        List<List<Integer>> roomTypeGrid,
        int numTemplates,
        Map<String, List<Placement>> templates) {

    public OriginalMap {
        roomTypeGrid = roomTypeGrid.stream().map(List::copyOf).toList();
        templates = Map.copyOf(templates);
    }
}
