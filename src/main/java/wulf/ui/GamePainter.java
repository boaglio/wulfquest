package wulf.ui;

import wulf.Content;
import wulf.data.DisplayConfig;
import wulf.data.OrchidValidator;
import wulf.data.Palette;
import wulf.render.CreaturePainter;
import wulf.render.Fonts;
import wulf.render.Framebuffer;
import wulf.render.OrchidPainter;
import wulf.render.PanelPainter;
import wulf.render.PlayerPainter;
import wulf.render.QuestPainter;
import wulf.render.RoomPainter;
import wulf.render.SpriteBank;
import wulf.sim.Simulation;
import wulf.world.RoomBaker;

/**
 * One frame of the jungle: the room, its flowers, its creatures, Vale, the
 * quest's loot and the panel (AGENTS.md §5.3, §17.3).
 *
 * <p>Gathered into one place so that everything which shows a game — playing it,
 * pausing it, dying in it, and watching the attract demo play it — draws exactly
 * the same picture.
 */
public final class GamePainter {

    private final DisplayConfig display;
    private final RoomBaker rooms;
    private final RoomPainter roomPainter;
    private final OrchidPainter flowers;
    private final QuestPainter quest;
    private final CreaturePainter beasts;
    private final PlayerPainter vale;
    private final PanelPainter panel;
    private final int[] effectColours;
    private final int border;
    private final int alarm;
    private final int parry;
    private final int maskColour;

    public GamePainter(Content c, Fonts fonts) {
        Palette palette = c.palette();
        SpriteBank sprites = new SpriteBank(c.sprites());
        this.display = c.display();
        this.rooms = c.rooms();
        this.roomPainter = new RoomPainter(display, sprites, c.scenery(), palette.indexOf("black"));
        this.flowers = new OrchidPainter(display, sprites, c.orchids());
        this.quest = new QuestPainter(display, sprites, c.landmarks(), palette.indexOf("black"));
        this.beasts = new CreaturePainter(display, sprites, c.creatures(), palette);
        this.vale = new PlayerPainter(display, sprites, c.player(), palette);
        this.panel = new PanelPainter(display, fonts, palette, sprites.get(c.landmarks().amulet().sprite()),
                QuestPainter.framesBySlot(c.landmarks()));
        this.effectColours = new int[c.orchids().orchids().size()];
        for (int i = 0; i < effectColours.length; i++) {
            effectColours[i] = palette.indexOf(OrchidValidator.bright(c.orchids().orchids().get(i).colour()));
        }
        this.border = palette.indexOf(display.border().idleColour());
        this.alarm = palette.indexOf("brightRed");
        this.parry = palette.indexOf("brightWhite");
        this.maskColour = palette.indexOf("brightRed");
    }

    public PanelPainter panel() {
        return panel;
    }

    /** The playfield alone — no panel — which is what the attract demo shows. */
    public void paintWorld(Framebuffer fb, Simulation sim, boolean showMask) {
        fb.clear(!display.border().flashOnEvent() ? border : switch (sim.borderFlash()) {
            case ALARM -> alarm;
            case PARRY, PICKUP -> parry;   // bright white for a parry (§13.5) and a quarter taken (§14.6)
            case NONE -> border;
        });
        roomPainter.paint(fb, rooms.room(sim.room()));
        if (showMask) {
            roomPainter.paintMask(fb, rooms.room(sim.room()), maskColour);
        }
        flowers.paint(fb, sim);
        quest.paintLoot(fb, sim);
        beasts.paint(fb, sim);
        vale.paint(fb, sim);
        quest.paintEscape(fb, sim);
    }

    /** The whole screen: the jungle and the panel under it. */
    public void paint(Framebuffer fb, Simulation sim, long best, String message, boolean showMask) {
        paintWorld(fb, sim, showMask);
        boolean under = sim.effect().active();
        panel.paint(fb, sim.score(), best, sim.player().lives(), sim.quest().slotMask(),
                under ? effectColours[sim.effect().orchid()] : -1,
                under ? sim.effect().remainingPerMille() : 0, message);
        quest.paintFlight(fb, sim, panel);
    }

    public DisplayConfig display() {
        return display;
    }
}
