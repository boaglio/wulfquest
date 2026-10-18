package wulf.ui;

import wulf.Content;
import wulf.data.DisplayConfig;
import wulf.data.OrchidValidator;
import wulf.data.Palette;
import wulf.render.AttributeClash;
import wulf.render.CreaturePainter;
import wulf.render.Fonts;
import wulf.render.Framebuffer;
import wulf.render.OrchidPainter;
import wulf.render.PanelPainter;
import wulf.render.PlayerPainter;
import wulf.render.QuestPainter;
import wulf.render.RoomPainter;
import wulf.render.SpriteBank;
import wulf.render.WayPainter;
import wulf.sim.Simulation;
import wulf.world.RoomBaker;
import wulf.world.Wayfinder;
import wulf.world.WorldGrid;

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
    private final wulf.render.AmuletFrames amulets;
    private final WayPainter way;
    private final int[] effectColours;
    private final int border;
    private final int alarm;
    private final int parry;
    private final int maskColour;
    private final int paper;
    private final AttributeClash clash;
    private boolean clashing;
    private final String easyLabel;
    private final int easyColour;

    public GamePainter(Content c, Fonts fonts) {
        Palette palette = c.palette();
        SpriteBank sprites = new SpriteBank(c.sprites());
        this.display = c.display();
        this.rooms = c.rooms();
        this.roomPainter = new RoomPainter(display, sprites, c.scenery(), palette.indexOf("black"));
        this.flowers = new OrchidPainter(display, sprites, c.orchids());
        int kinds = c.treasures().kinds().size();
        String[] treasureFrames = new String[kinds];
        int[] treasureInks = new int[kinds];
        for (int i = 0; i < kinds; i++) {
            treasureFrames[i] = c.treasures().kinds().get(i).frame();
            treasureInks[i] = palette.indexOf(c.treasures().kinds().get(i).ink());
        }
        this.amulets = wulf.render.AmuletFrames.of(c.landmarks(), c.shell());
        this.quest = new QuestPainter(display, sprites, c.landmarks(), palette.indexOf("black"),
                new QuestPainter.TreasureArt(c.treasurePlaces(), c.treasures().sprite(), treasureFrames, treasureInks))
                .amulets(amulets);
        this.way = new WayPainter(display, sprites, c.landmarks(), c.player().collisionBox(),
                new Wayfinder(new WorldGrid(c.rooms())));
        this.beasts = new CreaturePainter(display, sprites, c.creatures(), palette,
                c.game().feature("inkedCreatures"));
        this.vale = new PlayerPainter(display, sprites, c.player(), palette);
        this.panel = new PanelPainter(display, fonts, palette, sprites.get(c.landmarks().amulet().sprite()),
                QuestPainter.framesBySlot(c.landmarks()));
        this.effectColours = new int[c.orchids().orchids().size()];
        for (int i = 0; i < effectColours.length; i++) {
            effectColours[i] = palette.indexOf(OrchidValidator.bright(c.orchids().orchids().get(i).colour()));
        }
        vale.tints(effectColours);
        this.border = palette.indexOf(display.border().idleColour());
        this.alarm = palette.indexOf("brightRed");
        this.parry = palette.indexOf("brightWhite");
        this.maskColour = palette.indexOf("brightRed");
        this.paper = palette.indexOf("black");
        this.easyLabel = c.shell().difficulty().panelLabel();
        this.easyColour = palette.indexOf(c.shell().difficulty().easyColour());
        this.clash = new AttributeClash(display.playfield(), paper);
        this.clashing = display.crt().attributeClash();
    }

    /**
     * §17.9: the options page's switches, as the player has them now — the colour clash, and the
     * original's one ink a creature. Read every frame, so a change shows at once.
     */
    public void retro(wulf.data.Settings s) {
        this.clashing = s.crt().attributeClash();
        beasts.inked(Shell.optionOn(s, "inkedCreatures"));
        vale.inked(Shell.optionOn(s, "inkedCreatures"));   // one ink for every sprite, Vale too
        beasts.flicker(display.spriteFlicker() || Shell.optionOn(s, "spriteFlicker"));
    }

    /** Each job's amulet, slot by slot (§14.9). */
    public wulf.render.AmuletFrames amulets() {
        return amulets;
    }

    public PanelPainter panel() {
        return panel;
    }

    /** The way a looking glass shows (§14.10), which the map draws too. */
    public WayPainter way() {
        return way;
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
        if (clashing && !showMask) {
            clash.scenery(fb);   // §5.4: the room's own colours, before anything that moves
        }
        quest.paintLoot(fb, sim);
        beasts.paint(fb, sim);
        vale.paint(fb, sim);
        way.paintArrow(fb, sim);
        quest.paintEscape(fb, sim);
        if (clashing && !showMask) {
            clash.apply(fb);   // §5.4; the debug mask would only be repainted in someone else's ink
        }
    }

    /** The whole screen: the jungle and the panel under it. */
    public void paint(Framebuffer fb, Simulation sim, long best, String message, boolean showMask) {
        paintWorld(fb, sim, showMask);
        boolean under = sim.effect().active();
        panel.paint(fb, sim.score(), best, sim.player().lives(), sim.quest().slotMask(),
                under ? effectColours[sim.effect().orchid()] : -1,
                under ? sim.effect().remainingPerMille() : 0, message, amulets.forLevel(sim.level()));
        if (sim.easy()) {
            panel.paintMode(fb, easyLabel, easyColour);   // §17.8
        }
        quest.paintFlight(fb, sim, panel);
    }

    public DisplayConfig display() {
        return display;
    }
}
