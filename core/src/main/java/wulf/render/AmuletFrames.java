package wulf.render;

import java.util.ArrayList;
import java.util.List;
import wulf.data.LandmarksData;
import wulf.data.ShellConfig;

/**
 * Which frame of the amulet sprite each panel slot shows, job by job (AGENTS.md §14.6, §14.9):
 * the game's own quarters, from its lairs, and each later job's own amulet. The panel, the
 * pieces on their pedestals and in flight, the reveal and the closing scene all draw from it,
 * so they always show the same amulet.
 */
public record AmuletFrames(List<List<String>> byLevel) {

    public AmuletFrames {
        byLevel = byLevel.stream().map(List::copyOf).toList();
    }

    /** The game's quarters for level 0, and each job's own where it names one (else the game's). */
    public static AmuletFrames of(LandmarksData marks, ShellConfig shell) {
        List<String> own = QuestPainter.framesBySlot(marks);
        List<List<String>> levels = new ArrayList<>();
        levels.add(own);
        if (shell != null) {
            for (int n = 1; n < shell.newGamePlus().levels().size(); n++) {
                List<String> job = shell.amuletFor(n);
                levels.add(job == null ? own : job);
            }
        }
        return new AmuletFrames(levels);
    }

    /** The frames, slot by slot, for job {@code level}; the game's own beyond the jobs known. */
    public List<String> forLevel(int level) {
        return byLevel.get(level >= 0 && level < byLevel.size() ? level : 0);
    }
}
