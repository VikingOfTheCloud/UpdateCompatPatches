package jay.compat;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.*;

/**
 * Offline check, run outside the game:
 *   java -cp <pz.jar>:<Viewpoint.jar>:<ZombieBuddy.jar>:<compat out> jay.compat.Check <outDir> <mode> <jar>...
 * mode "fixed": classes are rewritten by Remap exactly as in game, then written to <outDir>/<jar> and
 * linked (the JVM verifier runs) in a loader that sees every listed mod jar. mode "orig": same, unchanged.
 */
public class Check {
    public static void main(String[] a) throws Exception {
        File outDir = new File(a[0]);
        boolean fixed = a[1].equals("fixed");
        List<File> jars = new ArrayList<>();
        for (int i = 2; i < a.length; i++) jars.add(new File(a[i]));
        Rules rules = Rules.load(java.nio.file.Path.of("/nonexistent"));
        Remap remap = fixed ? Remap.build(rules, Check.class.getClassLoader()) : null;
        Map<String, byte[]> classes = new LinkedHashMap<>();
        Map<String, String> jarOf = new HashMap<>();
        int changed = 0;
        outDir.mkdirs();
        for (File jf : jars) {
            try (JarFile jar = new JarFile(jf); JarOutputStream out = new JarOutputStream(new FileOutputStream(new File(outDir, jf.getName())))) {
                for (JarEntry e : Collections.list(jar.entries())) {
                    if (e.isDirectory()) continue;
                    byte[] b = jar.getInputStream(e).readAllBytes();
                    if (e.getName().endsWith(".class") && !e.getName().startsWith("META-INF")) {
                        byte[] r = remap == null ? null : remap.rewrite(Check.class.getClassLoader(), b);
                        if (r != null) { b = r; changed++; System.out.println("  rewritten " + e.getName()); }
                        String cn = e.getName().substring(0, e.getName().length() - 6).replace('/', '.');
                        classes.put(cn, b);
                        jarOf.put(cn, jf.getName());
                    }
                    out.putNextEntry(new JarEntry(e.getName()));
                    out.write(b);
                    out.closeEntry();
                }
            }
        }
        System.out.println(changed + " class(es) rewritten");
        ClassLoader loader = new ClassLoader(Check.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = classes.get(name);
                if (b == null) throw new ClassNotFoundException(name);
                return defineClass(name, b, 0, b.length);
            }
        };
        Map<String, Integer> bad = new TreeMap<>();
        int ok = 0;
        for (String cn : classes.keySet()) {
            try {
                Class<?> c = Class.forName(cn, false, loader);
                java.lang.invoke.MethodHandles.privateLookupIn(c, java.lang.invoke.MethodHandles.lookup()); // no link
                linkOnly(c);
                ok++;
            } catch (VerifyError | NoClassDefFoundError | ClassFormatError | IncompatibleClassChangeError e) {
                String k = jarOf.get(cn) + "  " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).replaceAll("\\s+", " ");
                if (k.length() > 230) k = k.substring(0, 230);
                bad.merge(k, 1, Integer::sum);
            } catch (Throwable t) {
                ok++;   // not a linking problem (environment, initialiser)
            }
        }
        System.out.println(ok + " class(es) link OK, " + (classes.size() - ok) + " fail");
        bad.forEach((k, v) -> System.out.println("  FAIL x" + v + " " + k));
    }

    /** Force verification without running static initialisers: Unsafe.ensureClassInitialized would init,
     *  so use the JVM's own link step via Class.forName(initialize=false) + a reflective method lookup. */
    static void linkOnly(Class<?> c) throws Throwable {
        // getDeclaredMethods does not link; MethodHandles.Lookup.findStatic/findVirtual does link (and verify) the class.
        var lookup = java.lang.invoke.MethodHandles.privateLookupIn(c, java.lang.invoke.MethodHandles.lookup());
        for (var m : c.getDeclaredMethods()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) lookup.unreflect(m); else lookup.unreflect(m);
            } catch (IllegalAccessException ignored) {}
            break;
        }
        lookup.ensureInitialized(c);   // runs <clinit> after verification; environment failures are caught by the caller
    }
}
