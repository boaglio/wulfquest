package wulf.sim;

/** For tests outside the simulation: a game put straight into {@code WON}, as if Vale had just gone through the arch. */
public final class WonGames {

    private WonGames() {
    }

    public static void win(Simulation sim) {
        sim.player().mode = Player.Mode.WON;
    }
}
