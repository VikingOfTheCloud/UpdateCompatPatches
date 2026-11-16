package jay.compat;

/** Everything goes to System.out with a fixed prefix; PZ copies stdout into ~/Zomboid/console.txt and the DebugLog. */
public final class Log {
    private static final String P = "[CompatPatches] ";
    private static final java.util.Set<String> ONCE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void info(String m) { System.out.println(P + m); }
    public static void warn(String m) { System.out.println(P + "WARNING: " + m); }
    public static void once(String key, String m) { if (ONCE.add(key)) info(m); }

    public static void error(String m, Throwable t) {
        System.out.println(P + "ERROR: " + m);
        if (t != null) t.printStackTrace(System.out);
    }

    private Log() {}
}
