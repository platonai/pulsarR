package `fun`.platonic.pulsar.ql.h2

/**
 *
 */
object DbConfig {

    val baseDir = "/tmp/scent-" + System.getenv("USER");

    var mvStore: Boolean = true

    /**
     * If remote database connections should be used.
     */
    var networked: Boolean = false

    /**
     * If in-memory databases should be used.
     */
    var memory: Boolean = false

    /**
     * If lazy queries should be used.
     */
    var lazy: Boolean = false

    /**
     * The cipher to use (null for unencrypted).
     */
    var cipher: String? = null

    /**
     * The file trace level value to use.
     */
    var traceLevelFile: Int = 0

    /**
     * If test trace information should be written (for debugging only).
     */
    var traceTest: Boolean = false

    /**
     * If testing on Google App Engine.
     */
    var googleAppEngine: Boolean = false

    /**
     * If a small cache and a low number for MAX_MEMORY_ROWS should be used.
     */
    var diskResult: Boolean = false

    /**
     * Test using the recording file system.
     */
    var reopen: Boolean = false

    /**
     * Test the split file system.
     */
    var splitFileSystem: Boolean = false

    /**
     * The lock timeout to use
     */
    var lockTimeout = 50

    /**
     * If the transaction log should be kept small (that is, the log should be
     * switched early).
     */
    var smallLog: Boolean = false

    /**
     * If SSL should be used for remote connections.
     */
    var ssl: Boolean = false

    /**
     * If MAX_MEMORY_UNDO=3 should be used.
     */
    var diskUndo: Boolean = false

    /**
     * If TRACE_LEVEL_SYSTEM_OUT should be set to 2 (for debugging only).
     */
    var traceSystemOut: Boolean = false

    /**
     * The THROTTLE value to use.
     */
    var throttle: Int = 0

    /**
     * The THROTTLE value to use by default.
     */
    var throttleDefault = Integer.parseInt(System.getProperty("throttle", "0"))

    /**
     * If the database should always be defragmented when closing.
     */
    var defrag: Boolean = false

    var port = 91000
}
