package wulf;

/**
 * Entry point.
 *
 * <p>M0 placeholder: the toolchain and the no-binaries gate are in place, but
 * nothing is loaded or rendered yet. M1 (AGENTS.md §24) replaces this with the
 * real boot sequence: CLI parsing, {@code JsonDb} load and validation, the
 * window and framebuffer, and the {@code DATA_ERROR} screen.
 *
 * <p>No game values belong in this class, or in any other {@code .java} file —
 * see AGENTS.md §27 rule 2.
 */
public final class Boot {

    private Boot() {
    }

    public static void main(String[] args) {
        System.out.println("Wulf Quest " + version() + " — M0 skeleton");
        System.out.println("Content DB: data/ (see AGENTS.md §20)");
        System.out.println("Nothing to run yet. Next milestone: M1, data spine and a window.");
    }

    static String version() {
        String v = Boot.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }
}
