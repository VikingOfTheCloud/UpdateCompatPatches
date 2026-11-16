package jay.compat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The patch list. One rule per line, '#' starts a comment, names use the JVM's internal form (a/b/C$D).
 *
 *   unlock <modId> <game major.minor>      the mod may load on games up to and including that version
 *   class  <old> <new> [interface]         a game class was renamed/replaced; 'interface' if <new> is an interface
 *   desc   <owner> <name> <desc> <newDesc> a game method changed its descriptor (desc as it reads after class rules)
 *   static <owner> <name> <desc> <newOwner> <newName> <newDesc>
 *                                          an instance method became static elsewhere; the receiver is dropped
 *                                          (only for methods taking exactly one single-slot argument)
 */
final class Rules {
    record ClassRule(String from, String to, boolean toInterface) {}
    record DescRule(String owner, String name, String desc, String newDesc) {}
    record StaticRule(String owner, String name, String desc, String newOwner, String newName, String newDesc) {}

    String source = "";
    final Map<String, String> unlocks = new LinkedHashMap<>();   // modId -> "major.minor"
    final List<ClassRule> classes = new ArrayList<>();
    final List<DescRule> descs = new ArrayList<>();
    final List<StaticRule> statics = new ArrayList<>();

    static final String BUILT_IN = "/jay/compat/compat-rules.txt";
    static final String LOCAL_FILE = "update-compat-patches.txt";

    static Rules load(Path localDir) throws Exception {
        Rules r = new Rules();
        try (InputStream in = Rules.class.getResourceAsStream(BUILT_IN)) {
            if (in == null) throw new IllegalStateException("built-in rules missing from the jar: " + BUILT_IN);
            r.read(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), "built-in");
        }
        r.localRead = r.readLocal(localDir);
        return r;
    }

    /** Second entry point: rules from a local file not read yet (e.g. the server's -cachedir). Null if none. */
    static Rules loadLocalOnly(Path localDir, Rules already) throws Exception {
        Path f = localDir.resolve(LOCAL_FILE).toAbsolutePath().normalize();
        if (f.equals(already.localRead) || !Files.isRegularFile(f)) return null;
        Rules r = new Rules();
        r.localRead = r.readLocal(localDir);
        Log.info("local rules " + f + ": unlocks added; class rules there only take effect when loaded at launch");
        return r;
    }

    Path localRead;

    private Path readLocal(Path localDir) throws Exception {
        Path local = localDir.resolve(LOCAL_FILE).toAbsolutePath().normalize();
        if (!Files.isRegularFile(local)) return null;
        try (BufferedReader br = Files.newBufferedReader(local, StandardCharsets.UTF_8)) {
            read(br, local.toString());
        }
        Log.info("read local rules " + local);
        return local;
    }

    private void read(BufferedReader br, String source) throws Exception {
        String line;
        int n = 0;
        while ((line = br.readLine()) != null) {
            n++;
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] t = line.split("\\s+");
            try {
                switch (t[0]) {
                    case "unlock" -> { need(t, 3); unlocks.put(t[1], t[2]); }
                    case "class" -> { need(t, 3); classes.add(new ClassRule(t[1], t[2], t.length > 3 && t[3].equals("interface"))); }
                    case "desc" -> { need(t, 5); descs.add(new DescRule(t[1], t[2], t[3], t[4])); }
                    case "static" -> { need(t, 7); statics.add(new StaticRule(t[1], t[2], t[3], t[4], t[5], t[6])); }
                    default -> throw new IllegalArgumentException("unknown rule '" + t[0] + "'");
                }
            } catch (IllegalArgumentException e) {
                Log.warn(source + " line " + n + " ignored: " + e.getMessage());
            }
        }
    }

    private static void need(String[] t, int count) {
        if (t.length < count) throw new IllegalArgumentException("'" + t[0] + "' needs " + (count - 1) + " values");
    }
}
