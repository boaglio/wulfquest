package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.OrchidData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.sim.effects.EffectKind;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;

/** AGENTS.md §15.2 — the original's five orchids: four colours that make him untouchable at a price, and the cure. */
class OriginalOrchidTest {

    private static final CollisionWorld OPEN = SimFixtures.OPEN;
    private static final InputState RIGHT = SimFixtures.RIGHT;
    private static final InputState STILL = InputState.NONE;
    private static OrchidData data;

    @BeforeAll
    static void load() {
        data = Content.load(Path.of("data")).orchids();
    }

    /** The shipped flowers, none growing; and a tribesman standing where the player stands. */
    private static Simulation sim(boolean tribesman) {
        OrchidData bare = new OrchidData(data.schemaVersion(), data.fidelity(), data.sprite(), data.cycle(),
                new OrchidData.Anchors(0, data.anchors().minSpacingPx(), data.anchors().attempts(), data.anchors().insetPx()),
                data.orchids(), data.inventedOrchids(), data.tint(), data.delirium(), data.immunity());
        RoomAddress room = EcoFixtures.ROOM;
        Ecosystem eco = new Ecosystem(EcoFixtures.CREATURES, (spawner, at, seed, visit) -> {
            if (tribesman && at.equals(room)) {
                spawner.spawn("tribesman", 128, 100, Herd.NONE, new wulf.engine.Rng(4));
            }
        }, 0, WulfRules.NONE, QuestRules.NONE, bare);
        return Simulation.at(SimFixtures.RULES, OPEN, 0, Fixed.fp(room.col() * Simulation.ROOM_W_PX + 128),
                Fixed.fp(room.row() * Simulation.ROOM_H_PX + 100), eco, 5L);
    }

    private static int walkedRight(Simulation s, int ticks) {
        int from = s.player().xFp();
        SimFixtures.run(s, RIGHT, ticks);
        return s.player().xFp() - from;
    }

    @Test
    void fiveColoursAndTheirOdds() {
        // $A6FD: sixteen entries, white four times, the others three each.
        assertThat(data.orchids()).extracting(OrchidData.Orchid::colour)
                .containsExactly("red", "cyan", "magenta", "yellow", "white");
        assertThat(data.orchids()).extracting(OrchidData.Orchid::weight).containsExactly(3, 3, 3, 3, 4);
        assertThat(data.orchids()).extracting(OrchidData.Orchid::score).as("no points for a flower").containsOnly(0);
    }

    @Test
    void everyColourButWhiteMakesHimUntouchable() {
        Simulation plain = sim(true);
        SimFixtures.run(plain, STILL, 60);
        assertThat(plain.player().mode()).as("white, a touch kills").isNotEqualTo(Player.Mode.ALIVE);
        for (String effect : new String[] {"TORPOR", "HASTE", "REVERSAL", "KNOCKDOWN"}) {
            Simulation s = sim(true);
            assertThat(s.giveEffect(effect)).isTrue();
            SimFixtures.run(s, STILL, 60);
            assertThat(s.player().mode()).as("%s: nothing can hurt him", effect).isEqualTo(Player.Mode.ALIVE);
            assertThat(s.effect().untouchable()).isTrue();
        }
    }

    @Test
    void redIsSlowAndCyanIsFast() {
        // $AFC1: at most 2 px a frame on red, 4 on cyan, against 3.
        int normal = walkedRight(sim(false), 200);
        Simulation red = sim(false);
        red.giveEffect("TORPOR");
        Simulation cyan = sim(false);
        cyan.giveEffect("HASTE");
        assertThat(walkedRight(red, 200)).isLessThan(normal);
        assertThat(walkedRight(cyan, 200)).isGreaterThan(normal);
    }

    @Test
    void yellowKnocksHimDownUntilItWearsOff() {
        Simulation s = sim(false);
        s.giveEffect("KNOCKDOWN");
        assertThat(s.effect().kind()).isEqualTo(EffectKind.KNOCKDOWN);
        assertThat(walkedRight(s, 100)).as("he cannot get up").isZero();
        SimFixtures.run(s, STILL, data.orchid(data.indexOfEffect("KNOCKDOWN")).ticks());
        assertThat(s.effect().active()).isFalse();
        assertThat(walkedRight(s, 50)).as("then he walks again").isPositive();
    }

    @Test
    void whiteCuresWhateverWasOn() {
        // $A757: the white flower clears the effect and leaves nothing in its place.
        Simulation s = sim(false);
        s.giveEffect("REVERSAL");
        assertThat(s.effect().active()).isTrue();
        s.giveEffect("CURE");
        assertThat(s.effect().active()).isFalse();
        assertThat(s.effect().untouchable()).isFalse();
    }

    @Test
    void inGodModeNoFlowerTakesHim() {
        // §17.6: --god walks through the flowers — the same spot without it picks one.
        RoomAddress room = new RoomAddress(5, 5);
        OrchidField field = new OrchidField(data);
        int[] anchors = field.anchors(room, OPEN, 3L);
        Ecosystem eco = new Ecosystem(EcoFixtures.CREATURES, RoomPopulator.NONE, 0, WulfRules.NONE, QuestRules.NONE, data);
        Simulation mortal = Simulation.at(SimFixtures.RULES, OPEN, 0, Fixed.fp(room.col() * Simulation.ROOM_W_PX + anchors[0]),
                Fixed.fp(room.row() * Simulation.ROOM_H_PX + anchors[1] - 4), eco, 3L);
        Simulation god = Simulation.at(SimFixtures.RULES, OPEN, 0, Fixed.fp(room.col() * Simulation.ROOM_W_PX + anchors[0]),
                Fixed.fp(room.row() * Simulation.ROOM_H_PX + anchors[1] - 4), eco, 3L);
        god.god(true);
        boolean ever = false;
        for (int t = 0; t < 6000; t++) {
            mortal.tick(STILL);
            god.tick(STILL);
            ever |= god.effect().active();
        }
        assertThat(mortal.orchidsByColour()).as("without --god he picks them").isNotEmpty();
        assertThat(god.orchidsByColour()).as("with it, none").isEmpty();
        assertThat(ever).isFalse();
    }

    @Test
    void heWearsTheFlowersColourAndFlashesAsItEnds() {
        Simulation s = sim(false);
        s.giveEffect("TORPOR");
        int red = data.indexOfEffect("TORPOR");
        assertThat(s.effectTint()).isEqualTo(red);
        OrchidData.Tint t = data.tint();
        int ticks = data.orchid(red).ticks();
        SimFixtures.run(s, STILL, ticks - t.warnTicks() + 1);
        boolean flashed = false;
        boolean wore = false;
        for (int i = 0; i < t.warnTicks() - 2; i++) {
            s.tick(STILL);
            flashed |= s.effectTint() < 0;
            wore |= s.effectTint() == red;
        }
        assertThat(flashed && wore).as("$AF7A: in and out of colour as it ends").isTrue();
        SimFixtures.run(s, STILL, 10);
        assertThat(s.effectTint()).isEqualTo(-1);
    }
}
