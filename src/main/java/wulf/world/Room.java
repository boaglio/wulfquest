package wulf.world;

import java.util.List;
import wulf.data.Placement;

/** One flip-screen: where it is, what it is built from, and what blocks movement (AGENTS.md §7.2). */
public record Room(RoomAddress address, int type, List<Placement> placements, CollisionMask mask) {
}
