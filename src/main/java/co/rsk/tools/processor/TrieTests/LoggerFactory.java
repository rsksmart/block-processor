package co.rsk.tools.processor.TrieTests;

public class LoggerFactory {
     // Just one instance for all
    static private Logger instance;

    static public Logger getLogger(String name) {
        if (instance==null)
            instance = new Logger("tests");
        return instance;
    }
}
