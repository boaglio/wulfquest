package wulf.engine;

/**
 * The fixed-timestep loop (AGENTS.md §6.1): logic runs at a fixed rate, the
 * frame is drawn once per tick, and time debt is dropped rather than allowed
 * to spiral.
 */
public final class GameLoop {

    /** What the loop drives. Implementations must not read the wall clock. */
    public interface Stepper {
        void tick();

        void render();

        boolean running();
    }

    private final long tickNanos;
    private final int maxCatchup;

    private long ticks;
    private long frames;

    public GameLoop(int tickHz, int maxCatchupTicks) {
        this.tickNanos = 1_000_000_000L / tickHz;
        this.maxCatchup = maxCatchupTicks;
    }

    public long ticks() {
        return ticks;
    }

    public long frames() {
        return frames;
    }

    public void run(Stepper stepper) {
        long accumulated = 0;
        long previous = System.nanoTime();
        while (stepper.running()) {
            long now = System.nanoTime();
            accumulated += now - previous;
            previous = now;

            int steps = 0;
            while (accumulated >= tickNanos && steps < maxCatchup) {
                stepper.tick();
                ticks++;
                accumulated -= tickNanos;
                steps++;
            }
            if (accumulated >= tickNanos) {
                accumulated = 0; // drop the debt; never accumulate it
            }

            stepper.render();
            frames++;

            if (steps == 0) {
                long sleep = (tickNanos - accumulated) / 1_000_000L;
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}
