package jay.compat;

/** ZombieBuddy agent entry for patches_jar=<jar>:jay.compat. Must not touch game classes: the game has not started. */
public class PreMain {
    public static void premain(String[] args) {
        Main.start("launch (patches_jar)", Main.localDirEarly());
    }
}
