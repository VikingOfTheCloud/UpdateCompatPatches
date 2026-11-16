package jay.compat;
import java.lang.reflect.Field;
import zombie.core.Core;
import zombie.core.GameVersion;
import zombie.gameStates.ChooseGameInfo;
public class UnlockTest {
    static ChooseGameInfo.Mod mod(String id, String min, String max) throws Exception {
        ChooseGameInfo.Mod m = new ChooseGameInfo.Mod("/nonexistent/" + id);
        m.setId(id);
        set(m, "versionMin", min == null ? null : GameVersion.parse(min));
        set(m, "versionMax", max == null ? null : GameVersion.parse(max));
        return m;
    }
    static void set(Object o, String f, Object v) throws Exception { Field x = o.getClass().getDeclaredField(f); x.setAccessible(true); x.set(o, v); }
    public static void main(String[] a) throws Exception {
        System.out.println("game version: " + Core.getInstance().getGameVersion());
        Unlock.install(Rules.load());
        Object[][] cases = {
            {"ViewpointLogWalls", "42.21", "42.21"},        // listed, capped below game -> unlock
            {"PZVoxelStudioViewpoint", "42.21.0", "42.21.0"},
            {"SomeOtherMod", "42.21", "42.21"},             // not listed -> stays locked
            {"Viewpoint", "42.21", "42.22"},                // already fine
            {"ViewpointRollingHills", "42.23", null},       // listed but needs a newer game -> stays locked
        };
        for (Object[] c : cases) {
            ChooseGameInfo.Mod m = mod((String) c[0], (String) c[1], (String) c[2]);
            boolean self = m.isAvailableSelf();
            Unlock.beforeIsAvailable(m);
            System.out.printf("%-24s min=%-7s max=%-7s game says %-5s -> with unlock %s%n", c[0], c[1], c[2], self, Unlock.afterIsAvailableSelf(m, self));
        }
    }
}
