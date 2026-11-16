package jay.compat;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.commons.ClassRemapper;
import net.bytebuddy.jar.asm.commons.SimpleRemapper;

/**
 * Class adapter. When a mod class is first loaded, and only if its bytes mention something a rule covers,
 * rewrite its references in memory:
 *   class rules  - every use of the old type becomes the new one (ASM ClassRemapper: descriptors, casts,
 *                  generic signatures, frames); calls on a type that became an interface switch from
 *                  invokevirtual to invokeinterface, which the JVM requires
 *   desc rules   - a call whose descriptor (after class rules) no longer exists is pointed at the new descriptor
 *   static rules - obj.m(x) becomes NewOwner.m(x): SWAP + POP drop the receiver, then invokestatic
 * A rule only switches on when the game really lacks the old thing (the old class is absent, or the old
 * method is gone), so the same jar is harmless on a game version where the old code still exists.
 *
 * ASM is the copy ByteBuddy ships inside ZombieBuddy (net.bytebuddy.jar.asm), so nothing extra is bundled.
 * A class that fails to rewrite loads unchanged; the failure is logged.
 */
final class Remap implements ClassFileTransformer {
    private final Map<String, String> classMap;          // active class rules: old -> new
    private final Set<String> interfaceTargets;           // new names that are interfaces
    private final List<Rules.DescRule> descs;
    private final List<Rules.StaticRule> statics;         // active static rules
    private final List<byte[]> triggers;                  // a class is only parsed if its bytes contain one of these
    private final Map<String, Boolean> assignable = new ConcurrentHashMap<>();
    private final AtomicInteger adapted = new AtomicInteger();
    private static volatile Remap active;
    private static final String[] SKIP = {"java/", "javax/", "jdk/", "sun/", "com/sun/", "zombie/", "jay/compat/",
            "net/bytebuddy/", "me/zed_0xff/"};

    private Remap(Map<String, String> classMap, Set<String> interfaceTargets, List<Rules.DescRule> descs,
                  List<Rules.StaticRule> statics) {
        this.classMap = classMap;
        this.interfaceTargets = interfaceTargets;
        this.descs = descs;
        this.statics = statics;
        List<byte[]> t = new ArrayList<>();
        for (String old : classMap.keySet()) t.add(old.getBytes(StandardCharsets.UTF_8));
        for (Rules.StaticRule s : statics) t.add(s.name().getBytes(StandardCharsets.UTF_8));
        this.triggers = t;
    }

    static synchronized void install(Rules rules) throws Exception {
        if (active != null) return;
        Remap r = build(rules, Main.class.getClassLoader());
        if (r == null) {
            Log.info("no class rules apply to this game; the class adapter stays off");
            return;
        }
        instrumentation().addTransformer(r, false);
        active = r;
    }

    /** The adapter for this game, or null when no rule applies (also used by the offline checker). */
    static Remap build(Rules rules, ClassLoader game) {
        Map<String, String> classMap = new HashMap<>();
        Set<String> ifaces = new HashSet<>();
        for (Rules.ClassRule c : rules.classes) {
            if (game.getResource(c.from() + ".class") != null) {
                Log.info("class rule " + c.from() + " -> " + c.to() + ": the old class still exists in this game; off");
                continue;
            }
            classMap.put(c.from(), c.to());
            if (c.toInterface()) ifaces.add(c.to());
        }
        List<Rules.StaticRule> statics = new ArrayList<>();
        for (Rules.StaticRule s : rules.statics) {
            if (hasMethod(game, s.owner(), s.name(), s.desc())) {
                Log.info("static rule " + s.owner() + "." + s.name() + ": the old method still exists in this game; off");
                continue;
            }
            statics.add(s);
        }
        if (classMap.isEmpty() && statics.isEmpty()) return null;
        return new Remap(Map.copyOf(classMap), Set.copyOf(ifaces), List.copyOf(rules.descs), List.copyOf(statics));
    }

    static String activeSummary() {
        Remap r = active;
        if (r == null) return "class adapter off";
        return "class adapter on (" + r.classMap.size() + " class, " + r.descs.size() + " descriptor, "
                + r.statics.size() + " static rule(s))";
    }

    /** ZombieBuddy keeps the agent's Instrumentation in a package-private static; there is no public getter. */
    private static Instrumentation instrumentation() throws Exception {
        Class<?> loader = Class.forName("me.zed_0xff.zombie_buddy.Loader");
        Field f = loader.getDeclaredField("g_instrumentation");
        f.setAccessible(true);
        Object o = f.get(null);
        if (!(o instanceof Instrumentation inst)) throw new IllegalStateException("ZombieBuddy has no Instrumentation");
        return inst;
    }

    /** Does owner (or a superclass) declare name+desc? Read from the class file, so nothing gets loaded. */
    private static boolean hasMethod(ClassLoader cl, String owner, String name, String desc) {
        String cls = owner;
        for (int depth = 0; cls != null && depth < 16; depth++) {
            try (InputStream in = cl.getResourceAsStream(cls + ".class")) {
                if (in == null) return false;
                ClassReader cr = net.bytebuddy.utility.OpenedClassReader.of(in.readAllBytes());
                boolean[] found = {false};
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex) {
                        if (n.equals(name) && d.equals(desc)) found[0] = true;
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                if (found[0]) return true;
                cls = cr.getSuperName();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined, ProtectionDomain pd, byte[] bytes) {
        if (loader == null || className == null || redefined != null || bytes == null) return null;
        for (String s : SKIP) if (className.startsWith(s)) return null;
        if (!mentionsAny(bytes)) return null;
        try {
            byte[] out = rewrite(loader, bytes);
            if (out != null) {
                adapted.incrementAndGet();
                Log.info("adapted " + className.replace('/', '.'));
            }
            return out;
        } catch (Throwable t) {
            Log.error("could not adapt " + className.replace('/', '.') + "; it loads unchanged", t);
            return null;
        }
    }

    /** Pure function of the input bytes and rules; also used by the offline checker. Null = nothing to change. */
    byte[] rewrite(ClassLoader loader, byte[] bytes) {
        ClassReader cr = net.bytebuddy.utility.OpenedClassReader.of(bytes);
        ClassWriter cw = new ClassWriter(0);
        boolean[] touched = {false};
        ClassVisitor fix = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(acc, n, d, sig, ex)) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (op == Opcodes.INVOKEVIRTUAL && interfaceTargets.contains(owner)) {
                            op = Opcodes.INVOKEINTERFACE;
                            itf = true;
                            touched[0] = true;
                        }
                        for (Rules.DescRule r : descs) {
                            if (r.name().equals(name) && r.desc().equals(desc) && isA(loader, owner, r.owner())) {
                                desc = r.newDesc();
                                touched[0] = true;
                            }
                        }
                        if (op == Opcodes.INVOKEVIRTUAL) {
                            for (Rules.StaticRule r : statics) {
                                if (r.name().equals(name) && r.desc().equals(desc) && isA(loader, owner, r.owner())) {
                                    super.visitInsn(Opcodes.SWAP);
                                    super.visitInsn(Opcodes.POP);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, r.newOwner(), r.newName(), r.newDesc(), false);
                                    touched[0] = true;
                                    return;
                                }
                            }
                        }
                        super.visitMethodInsn(op, owner, name, desc, itf);
                    }
                };
            }
        };
        SimpleRemapper names = new SimpleRemapper(Opcodes.ASM9, classMap) {
            @Override
            public String map(String key) {
                String to = super.map(key);
                if (to != null) touched[0] = true;
                return to;
            }
        };
        cr.accept(new ClassRemapper(fix, names), 0);
        return touched[0] ? cw.toByteArray() : null;
    }

    private boolean mentionsAny(byte[] b) {
        for (byte[] t : triggers) if (indexOf(b, t) >= 0) return true;
        return false;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0, n = hay.length - needle.length; i <= n; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    /** Is internal-name type 'owner' the rule's owner or a subclass of it? Answered once per pair. */
    private boolean isA(ClassLoader loader, String owner, String ruleOwner) {
        if (owner.equals(ruleOwner)) return true;
        return assignable.computeIfAbsent(owner + ">" + ruleOwner, k -> {
            try {
                Class<?> o = Class.forName(owner.replace('/', '.'), false, loader);
                Class<?> r = Class.forName(ruleOwner.replace('/', '.'), false, loader);
                return r.isAssignableFrom(o);
            } catch (Throwable t) {
                return false;
            }
        });
    }
}
