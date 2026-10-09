package hai913i.tp1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generateur de projets Java aleatoires MAIS deterministes (graine) dont on connait le modele exact :
 * il sert de "verite terrain" pour les tests de proprietes et de performance. Aucune dependance JDT.
 *
 * Forme des projets : paquetage "gen", classes C0..Cn (champs, methodes statiques, classe imbriquee N optionnelle),
 * interfaces I<k> (methodes abstraites). Chaque appel est sur sa propre ligne, donc sa ligne est connue.
 */
public final class SourceGenerator {

    public record Call(String callerId, String targetId, String receiver, int line) {
    }

    public record Method(String id, String name, List<String> paramTypes, int bodyLoc, List<Call> calls) {
    }

    /** visibility : nom de hai913i.tp1.model.Visibility (PUBLIC, PRIVATE, PROTECTED, PACKAGE). */
    public record Field(String name, String type, String visibility) {
    }

    public record Type(String qualifiedName, boolean isInterface, List<Field> fields, List<Method> methods) {
    }

    /** files : chemin relatif (gen/C0.java) -> contenu. */
    public record Generated(Map<String, String> files, List<Type> types, int totalLines) {
    }

    private record Sig(String name, int params) {
    }

    private record Callable(String qualifiedName, String expression, List<Sig> methods) {
    }

    private static final class Lines {
        private final StringBuilder sb = new StringBuilder();
        private int count;

        /** Ajoute une ligne et renvoie son numero (1-based). */
        int add(String s) {
            sb.append(s).append('\n');
            return ++count;
        }
    }

    private static final String[] VIS_KEYWORD = {"", "private ", "protected ", "public "};
    private static final String[] VIS_NAME = {"PACKAGE", "PRIVATE", "PROTECTED", "PUBLIC"};

    private SourceGenerator() {
    }

    public static Generated generate(long seed, int classes, int maxMethods) {
        Random r = new Random(seed);
        Callable[] tops = new Callable[classes];
        Callable[] nested = new Callable[classes];
        List<Callable> pool = new ArrayList<>();
        for (int i = 0; i < classes; i++) {
            tops[i] = new Callable("gen.C" + i, "C" + i, sigs("m", 1 + r.nextInt(maxMethods), r));
            pool.add(tops[i]);
            if (r.nextInt(10) < 3) {
                nested[i] = new Callable("gen.C" + i + ".N", "C" + i + ".N", sigs("n", 1 + r.nextInt(3), r));
                pool.add(nested[i]);
            }
        }

        Map<String, String> files = new LinkedHashMap<>();
        List<Type> types = new ArrayList<>();
        int total = 0;

        for (int i = 0; i < classes; i++) {
            Lines w = new Lines();
            w.add("package gen;");
            w.add("");
            w.add("class C" + i + " {");

            List<Field> fields = new ArrayList<>();
            int idx = 0;
            int declarations = r.nextInt(4);
            for (int d = 0; d < declarations; d++) {
                int v = r.nextInt(4);
                String type = r.nextInt(3) == 0 ? "String" : "int";
                String modifier = r.nextInt(4) == 0 ? "static " : "";
                int vars = 1 + r.nextInt(2);
                List<String> names = new ArrayList<>();
                for (int k = 0; k < vars; k++) {
                    String name = "f" + idx++;
                    names.add(name);
                    fields.add(new Field(name, type, VIS_NAME[v]));
                }
                w.add("    " + VIS_KEYWORD[v] + modifier + type + " " + String.join(", ", names) + ";");
            }

            List<Method> methods = new ArrayList<>();
            for (Sig s : tops[i].methods()) {
                methods.add(writeMethod(w, "    ", tops[i], s, pool, r));
            }
            types.add(new Type(tops[i].qualifiedName(), false, fields, methods));

            if (nested[i] != null) {
                w.add("    static class N {");
                List<Method> nm = new ArrayList<>();
                for (Sig s : nested[i].methods()) {
                    nm.add(writeMethod(w, "        ", nested[i], s, pool, r));
                }
                w.add("    }");
                types.add(new Type(nested[i].qualifiedName(), false, List.of(), nm));
            }
            w.add("}");
            files.put("gen/C" + i + ".java", w.sb.toString());
            total += w.count;

            if (r.nextInt(10) < 2) {
                Lines iw = new Lines();
                iw.add("package gen;");
                iw.add("");
                iw.add("interface I" + i + " {");
                List<Method> im = new ArrayList<>();
                int n = 1 + r.nextInt(3);
                for (int k = 0; k < n; k++) {
                    int params = r.nextInt(3);
                    List<String> ptypes = new ArrayList<>();
                    List<String> decl = new ArrayList<>();
                    for (int p = 0; p < params; p++) {
                        ptypes.add("int");
                        decl.add("int x" + p);
                    }
                    String name = "a" + k;
                    String ret = r.nextBoolean() ? "void" : "int";
                    iw.add("    " + ret + " " + name + "(" + String.join(", ", decl) + ");");
                    im.add(new Method("gen.I" + i + "#" + name + "(" + String.join(",", ptypes) + ")", name, ptypes,
                            0, List.of()));
                }
                iw.add("}");
                files.put("gen/I" + i + ".java", iw.sb.toString());
                total += iw.count;
                types.add(new Type("gen.I" + i, true, List.of(), im));
            }
        }
        return new Generated(files, types, total);
    }

    private static List<Sig> sigs(String prefix, int count, Random r) {
        List<Sig> l = new ArrayList<>();
        for (int j = 0; j < count; j++) {
            l.add(new Sig(prefix + j, r.nextInt(4)));
        }
        return l;
    }

    private static String idOf(String owner, Sig s) {
        return owner + "#" + s.name() + "(" + String.join(",", java.util.Collections.nCopies(s.params(), "int")) + ")";
    }

    private static Method writeMethod(Lines w, String indent, Callable owner, Sig sig, List<Callable> pool, Random r) {
        List<String> decl = new ArrayList<>();
        for (int p = 0; p < sig.params(); p++) {
            decl.add("int a" + p);
        }
        String id = idOf(owner.qualifiedName(), sig);
        List<String> ptypes = java.util.Collections.nCopies(sig.params(), "int");
        String header = indent + "static void " + sig.name() + "(" + String.join(", ", decl) + ")";
        List<Call> calls = new ArrayList<>();

        int inner = r.nextInt(5);
        if (inner == 0) {
            w.add(header + " { }");
            return new Method(id, sig.name(), ptypes, 1, calls);
        }
        int start = w.add(header + " {");
        for (int k = 0; k < inner; k++) {
            int kind = r.nextInt(10);
            if (kind == 0) {
                w.add(indent + "    // commentaire " + k);
            } else if (kind == 1) {
                w.add("");
            } else {
                boolean qualified = r.nextInt(10) < 6;
                Callable target = qualified ? pool.get(r.nextInt(pool.size())) : owner;
                Sig t = target.methods().get(r.nextInt(target.methods().size()));
                String args = String.join(", ", java.util.Collections.nCopies(t.params(), "0"));
                String prefix = qualified ? target.expression() + "." : "";
                int line = w.add(indent + "    " + prefix + t.name() + "(" + args + ");");
                calls.add(new Call(id, idOf(target.qualifiedName(), t), target.qualifiedName(), line));
            }
        }
        int end = w.add(indent + "}");
        return new Method(id, sig.name(), ptypes, end - start + 1, calls);
    }
}
