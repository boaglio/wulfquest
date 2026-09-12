package wulf.data;

/**
 * A problem with a file in the content database.
 *
 * <p>Per AGENTS.md §20.2 every such failure must name the file, the JSON
 * pointer inside it, what was found and what was expected — never a bare
 * stack trace. {@link wulf.ui.DataErrorScreen} renders one of these.
 */
public final class DataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String file;
    private final String pointer;
    private final String detail;

    public DataException(String file, String pointer, String detail) {
        this(file, pointer, detail, null);
    }

    public DataException(String file, String pointer, String detail, Throwable cause) {
        super(file + " at " + (pointer == null || pointer.isEmpty() ? "/" : pointer) + ": " + detail, cause);
        this.file = file;
        this.pointer = pointer == null || pointer.isEmpty() ? "/" : pointer;
        this.detail = detail;
    }

    /** Path or resource name of the offending file. */
    public String file() {
        return file;
    }

    /** JSON pointer to the offending value, e.g. {@code /entries/3/rgb}. */
    public String pointer() {
        return pointer;
    }

    /** What was wrong, in one sentence. */
    public String detail() {
        return detail;
    }
}
