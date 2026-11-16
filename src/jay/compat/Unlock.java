package jay.compat;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import zombie.core.Core;
import zombie.core.GameVersion;
import zombie.gameStates.ChooseGameInfo;

/**
 * Version unlock. The game decides whether a mod may load in ChooseGameInfo.Mod.isAvailableSelf():
 * versionMin <= game and game <= versionMax, comparing major.minor only (so 42.21 covers every 42.21.x).
 * isAvailable() caches that answer per mod, and every loader in the game (main menu list, server mod list,
 * ZombieBuddy's Java loader, Lua's getActivatedMods) goes through it.
 *
 * For a mod named in an unlock rule we clear the cache before isAvailable() runs and turn a "no" from
 * isAvailableSelf() into "yes" when: the game is no newer than the rule's version, and the mod's versionMin
 * is still satisfied. The required-mods check is left to the game. A rule written for 42.22 stops applying on
 * 42.23, so a later update re-locks the mod until the rule is reviewed.
 *
 * Called from code that ZombieBuddy inlines into the game class, so these methods must stay public.
 */
public final class Unlock {
    private static volatile Map<String, Integer> caps = Map.of();
    private static volatile Map<String, String> capText = Map.of();
    private static volatile Field availableDone;
    private static volatile GameVersion game;

    /** No game class may be initialised here: at launch (patches_jar) the game has not started yet. */
    static void install(Rules rules) {
        add(rules);
    }

    /** Looked up on first use, so nothing of the game is loaded at launch. */
    private static Field availableDone() {
        Field f = availableDone;
        if (f != null || noField) return f;
        try {
            f = ChooseGameInfo.Mod.class.getDeclaredField("availableDone");
            f.setAccessible(true);
            return availableDone = f;
        } catch (Throwable e) {
            noField = true;
            Log.warn("cannot reach ChooseGameInfo.Mod.availableDone (" + e + "); a mod the game already turned down"
                    + " this launch stays down until the next launch");
            return null;
        }
    }

    private static volatile boolean noField;

    /** "42.22" -> 42022, the same number GameVersion.getInt() gives (major * 1000 + minor). -1 if unreadable. */
    static int versionInt(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+).*").matcher(s.trim());
        if (!m.matches()) return -1;
        return Integer.parseInt(m.group(1)) * 1000 + Integer.parseInt(m.group(2));
    }

    static synchronized void add(Rules rules) {
        Map<String, Integer> c = new HashMap<>(caps);
        Map<String, String> t = new HashMap<>(capText);
        for (Map.Entry<String, String> e : rules.unlocks.entrySet()) {
            int v = versionInt(e.getValue());
            if (v <= 0) {
                Log.warn("unlock " + e.getKey() + ": '" + e.getValue() + "' is not a game version like 42.22; skipped");
                continue;
            }
            c.put(e.getKey(), v);
            t.put(e.getKey(), e.getValue());
        }
        caps = Map.copyOf(c);
        capText = Map.copyOf(t);
    }

    private static GameVersion game() {
        GameVersion g = game;
        if (g == null) game = g = Core.getInstance().getGameVersion();
        return g;
    }

    /** Before isAvailable(): forget a cached answer for unlocked mods so the patched check below runs again. */
    public static void beforeIsAvailable(Object self) {
        if (!(self instanceof ChooseGameInfo.Mod mod)) return;
        String id = mod.getId();
        if (id == null || !caps.containsKey(id)) return;
        Field f = availableDone();
        if (f == null) return;
        try {
            f.setBoolean(mod, false);
        } catch (Throwable ignored) {
        }
    }

    /** After isAvailableSelf(): the game's answer, or true when an unlock rule covers this mod and this game. */
    public static boolean afterIsAvailableSelf(Object self, boolean available) {
        if (available || !(self instanceof ChooseGameInfo.Mod mod)) return available;
        String id = mod.getId();
        Integer cap = id == null ? null : caps.get(id);
        if (cap == null) return false;
        GameVersion game = game();
        if (game.getInt() > cap) {
            Log.once("expired:" + id, "unlock " + id + " covers games up to " + capText.get(id) + "; this game is "
                    + game + ", so the mod stays locked until the rule is reviewed");
            return false;
        }
        GameVersion min = mod.getVersionMin();
        if (min != null && min.isGreaterThan(game)) return false;   // never let a mod in that needs a newer game
        Log.once("unlock:" + id, "unlocked " + id + ": its mod.info allows up to " + mod.getVersionMax()
                + ", this game is " + game + " (rule covers up to " + capText.get(id) + ")");
        return true;
    }

    private Unlock() {}
}
