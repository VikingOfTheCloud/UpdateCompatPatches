package at;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import zombie.core.GameVersion;
import zombie.gameStates.ChooseGameInfo;
public class AgentTest {
    static ChooseGameInfo.Mod mod(String id, String min, String max) throws Exception {
        ChooseGameInfo.Mod m = new ChooseGameInfo.Mod("/nonexistent/" + id);
        m.setId(id);
        set(m, "versionMin", min == null ? null : GameVersion.parse(min));
        set(m, "versionMax", max == null ? null : GameVersion.parse(max));
        return m;
    }
    static void set(Object o, String f, Object v) throws Exception { Field x = o.getClass().getDeclaredField(f); x.setAccessible(true); x.set(o, v); }
    public static void main(String[] a) throws Exception {
        System.out.println("TEST isAvailable through ZombieBuddy-applied advice:");
        for (String[] c : new String[][]{{"ViewpointLogWalls","42.21","42.21"},{"SomeOtherMod","42.21","42.21"},{"PZVoxelStudioViewpoint","42.21.0","42.21.0"}}) {
            ChooseGameInfo.Mod m = mod(c[0], c[1], c[2]);
            set(m, "availableDone", true); set(m, "available", false);   // as if the game had already said no
            System.out.println("  " + c[0] + " isAvailable=" + m.isAvailable());
        }
        System.out.println("TEST class adapter on a mod class loaded now:");
        URLClassLoader l = new URLClassLoader(new URL[]{new java.io.File(a[0]).toURI().toURL()}, AgentTest.class.getClassLoader());
        Class<?> ladder = Class.forName("pvconstruction.Ladder", false, l);
        Method m = null;
        for (Method x : ladder.getDeclaredMethods()) if (x.getName().equals("metadataObjects")) m = x;
        System.out.println("  Ladder.metadataObjects signature now: " + m);
        m.setAccessible(true);
        System.out.println("  Ladder.metadataObjects(empty list) = " + m.invoke(null, new ArrayList<>()));
    }
}
