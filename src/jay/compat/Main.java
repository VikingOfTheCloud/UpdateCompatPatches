package jay.compat;

import java.nio.file.Path;

/**
 * Entry points. ZombieBuddy calls them by name:
 *   PreMain.premain() - only when the jar is given to the agent as patches_jar=<jar>:jay.compat (JVM arguments).
 *                       Runs before the game starts, so the unlock is in place before the game builds its
 *                       first mod list (single player and the main menu included).
 *   Main.main()       - when the mod is loaded from the mod list, inside ZomboidFileSystem.loadMods(), in
 *                       mod-list order (keep it directly under ZombieBuddy). Enough for multiplayer: clients
 *                       take the server's list and dedicated servers their Mods= line without the startup
 *                       check that single player goes through.
 * Whichever runs first installs everything; the other only adds rules from the local file. Never throws.
 */
public class Main {
    public static final String VERSION = "0.2.0";
    private static boolean installed;
    private static Rules rules;

    public static void main(String[] args) {
        start("mod list", localDirLate());
    }

    static synchronized void start(String how, Path localDir) {
        try {
            if (installed) {
                Rules extra = Rules.loadLocalOnly(localDir, rules);
                if (extra != null) Unlock.add(extra);
                Log.info("already active since " + rules.source + "; nothing more to do");
                return;
            }
            Log.info("SPS Viewpoint Compatibility Patch 42.22.2 (" + VERSION + ") starting (" + how + ")");
            rules = Rules.load(localDir);
            rules.source = how;
            Unlock.install(rules);
            Remap.install(rules);
            installed = true;
            Log.info("ready: " + rules.unlocks.size() + " unlock(s), " + Remap.activeSummary());
        } catch (Throwable t) {
            Log.error("initialisation failed; the game carries on without these patches", t);
        }
    }

    /** The game's own Zomboid folder (honours -cachedir), available once the game is running. */
    static Path localDirLate() {
        try {
            String dir = zombie.ZomboidFileSystem.instance.getCacheDir();
            if (dir != null && !dir.isBlank()) return Path.of(dir);
        } catch (Throwable ignored) {
        }
        return localDirEarly();
    }

    /** Before the game starts nothing of it may be touched; -cachedir is not parsed yet. */
    static Path localDirEarly() {
        return Path.of(System.getProperty("user.home"), "Zomboid");
    }
}
