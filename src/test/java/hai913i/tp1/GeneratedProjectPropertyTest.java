package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.SourceGenerator.Call;
import hai913i.tp1.SourceGenerator.Generated;
import hai913i.tp1.SourceGenerator.Type;
import hai913i.tp1.model.FieldFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.model.TypeKind;
import hai913i.tp1.model.Visibility;
import hai913i.tp1.parse.JdtParser.ParsedFile;

/**
 * Tests de proprietes : des projets aleatoires (graine fixe, donc reproductibles) dont le modele exact est connu
 * par construction. En cas d'echec, le message donne la graine : SourceGenerator.generate(seed, ...) la rejoue.
 */
class GeneratedProjectPropertyTest {

    private static Generated write(Path dir, long seed, int classes, int maxMethods) {
        Generated g = SourceGenerator.generate(seed, classes, maxMethods);
        g.files().forEach((p, c) -> TestSupport.write(dir, p, c));
        return g;
    }

    private static void assertMatches(long seed, Generated g, ProjectFacts f) {
        String ctx = " [graine " + seed + "]";
        assertEquals(g.types().stream().map(Type::qualifiedName).sorted().toList(),
                f.types().stream().map(TypeFact::qualifiedName).toList(), "types" + ctx);
        assertEquals(g.totalLines(), f.totalLines(), "lignes" + ctx);
        assertEquals(g.files().size(), f.sourceFiles().size(), "fichiers" + ctx);

        for (Type gt : g.types()) {
            TypeFact tf = TestSupport.type(f, gt.qualifiedName());
            String at = gt.qualifiedName() + ctx;
            assertEquals(gt.isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS, tf.kind(), "genre " + at);
            assertEquals("gen", tf.packageName(), "paquetage " + at);
            assertTrue(tf.superclasses().isEmpty(), "superclasses " + at);
            assertTrue(tf.interfaces().isEmpty(), "interfaces " + at);

            assertEquals(gt.fields().stream().map(SourceGenerator.Field::name).sorted().toList(),
                    tf.fields().stream().map(FieldFact::name).toList(), "champs " + at);
            for (SourceGenerator.Field gf : gt.fields()) {
                FieldFact ff = tf.fields().stream().filter(x -> x.name().equals(gf.name())).findFirst().orElseThrow();
                assertEquals(gf.type(), ff.declaredType(), "type du champ " + gf.name() + " de " + at);
                assertEquals(Visibility.valueOf(gf.visibility()), ff.visibility(),
                        "visibilite du champ " + gf.name() + " de " + at);
            }

            assertEquals(gt.methods().stream().map(SourceGenerator.Method::id).sorted().toList(),
                    tf.methods().stream().map(MethodFact::id).toList(), "methodes " + at);
            for (SourceGenerator.Method gm : gt.methods()) {
                MethodFact mf = tf.methods().stream().filter(x -> x.id().equals(gm.id())).findFirst().orElseThrow();
                String am = gm.id() + ctx;
                assertEquals(gm.name(), mf.name(), "nom " + am);
                assertEquals(gm.paramTypes(), mf.parameterTypes(), "parametres " + am);
                assertEquals(false, mf.constructor(), "constructeur " + am);
                assertEquals(gm.bodyLoc(), mf.bodyLoc(), "lignes du corps " + am);

                List<Call> expected = gm.calls().stream().sorted(Comparator.comparingInt(Call::line)).toList();
                List<Call> actual = mf.calls().stream()
                        .map(c -> new Call(c.callerId(), c.targetId().orElse("?"), c.staticReceiverType(), c.sourceLine()))
                        .toList();
                assertEquals(expected, actual, "appels " + am);
                assertTrue(mf.calls().stream().allMatch(c -> c.resolved() && c.internal()), "resolus et internes " + am);
            }
        }
    }

    @Test
    void extractedFactsMatchTheGeneratorGroundTruth(@TempDir Path tmp) throws IOException{
        for (long seed = 1; seed <= 40; seed++) {
            Path dir = tmp.resolve("s" + seed);
            Generated g = write(dir, seed, 3 + (int) (seed % 7), 5);
            ProjectFacts f = TestSupport.facts(dir);
            assertMatches(seed, g, f);
            FactsInvariants.checkStrict(f);
        }
    }

    @Test
    void extractionIsIdempotentAndIndependentFromFileOrder(@TempDir Path tmp) throws IOException {
        for (long seed = 100; seed < 115; seed++) {
            Path dir = tmp.resolve("s" + seed);
            write(dir, seed, 6, 5);
            List<ParsedFile> files = new ArrayList<>(TestSupport.parse(dir));
            ProjectFacts base = TestSupport.extract(files);
            assertEquals(base, TestSupport.extract(TestSupport.parse(dir)), "deux extractions [graine " + seed + "]");
            Collections.reverse(files);
            assertEquals(base, TestSupport.extract(files), "ordre inverse [graine " + seed + "]");
            Collections.shuffle(files, new Random(seed));
            assertEquals(base, TestSupport.extract(files), "ordre melange [graine " + seed + "]");
        }
    }

    @Test
    void sumOfCallsEqualsGeneratorTotal(@TempDir Path tmp) throws IOException{
        Generated g = write(tmp, 5150, 12, 6);
        ProjectFacts f = TestSupport.facts(tmp);
        long expected = g.types().stream().flatMap(t -> t.methods().stream()).mapToLong(m -> m.calls().size()).sum();
        assertEquals(expected, TestSupport.allCalls(f).size());
    }

    // ---------- code corrompu : le pipeline ne doit jamais planter ----------

    private static String mutate(Random r, String src, StringBuilder how) {
        int op = r.nextInt(6);
        int pos = src.isEmpty() ? 0 : r.nextInt(src.length());
        switch (op) {
            case 0:
                how.append("suppression du caractere ").append(pos);
                return src.isEmpty() ? src : src.substring(0, pos) + src.substring(pos + 1);
            case 1: {
                String junk = "{}();,\"'/*<>@#\\";
                char c = junk.charAt(r.nextInt(junk.length()));
                how.append("insertion de '").append(c).append("' en ").append(pos);
                return src.substring(0, pos) + c + src.substring(pos);
            }
            case 2:
                how.append("troncature a ").append(pos).append(" caracteres");
                return src.substring(0, pos);
            case 3: {
                String[] lines = src.split("\n", -1);
                int a = r.nextInt(lines.length);
                int b = r.nextInt(lines.length);
                how.append("echange des lignes ").append(a).append(" et ").append(b);
                String t = lines[a];
                lines[a] = lines[b];
                lines[b] = t;
                return String.join("\n", lines);
            }
            case 4: {
                String[] lines = src.split("\n", -1);
                int a = r.nextInt(lines.length);
                how.append("duplication de la ligne ").append(a);
                List<String> l = new ArrayList<>(List.of(lines));
                l.add(a, lines[a]);
                return String.join("\n", l);
            }
            default: {
                String[] lines = src.split("\n", -1);
                int a = r.nextInt(lines.length);
                how.append("suppression de la ligne ").append(a);
                List<String> l = new ArrayList<>(List.of(lines));
                l.remove(a);
                return String.join("\n", l);
            }
        }
    }

    private static String causeOf(Throwable t) throws IOException{
        StackTraceElement where = java.util.Arrays.stream(t.getStackTrace())
                .filter(e -> e.getClassName().startsWith("hai913i.tp1.") && !e.getClassName().contains("Test"))
                .findFirst().orElse(t.getStackTrace().length > 0 ? t.getStackTrace()[0] : null);
        String loc = where == null ? "?" : where.getClassName().substring(where.getClassName().lastIndexOf('.') + 1)
                + "." + where.getMethodName() + ":" + where.getLineNumber();
        String msg = t instanceof AssertionError && t.getMessage() != null
                ? " - " + t.getMessage().lines().findFirst().orElse("") : "";
        return t.getClass().getSimpleName() + " dans " + loc + msg;
    }

    /**
     * Un echec ici est une VRAIE decouverte (exemple : binding de declaration null sur du code casse, donc
     * NullPointerException). Les 150 mutations sont toutes jouees ; les echecs sont regroupes par cause avec UN exemple
     * chacun (mutation + fichier corrompu), pour corriger plusieurs bugs par execution.
     */
    @Test
    void corruptedSourcesNeverCrashThePipeline(@TempDir Path tmp)throws IOException {
        Random r = new Random(7);
        Map<String, String> examples = new java.util.LinkedHashMap<>();
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 150; i++) {
            Path dir = tmp.resolve("m" + i);
            Generated g = write(dir, 1000 + i, 4, 4);
            List<String> names = new ArrayList<>(g.files().keySet());
            String victim = names.get(r.nextInt(names.size()));
            StringBuilder how = new StringBuilder();
            String mutated = mutate(r, g.files().get(victim), how);
            TestSupport.write(dir, victim, mutated);
            try {
                ProjectFacts f = TestSupport.facts(dir);
                assertEquals(f, TestSupport.facts(dir), "determinisme sur code corrompu");
                FactsInvariants.checkWeak(f);
            } catch (Throwable t) {
                String cause = causeOf(t);
                counts.merge(cause, 1, Integer::sum);
                examples.putIfAbsent(cause, "mutation n" + i + " (" + how + ") sur " + victim
                        + "\n--- fichier corrompu ---\n" + mutated);
            }
        }
        if (!examples.isEmpty()) {
            StringBuilder report = new StringBuilder(counts.values().stream().mapToInt(Integer::intValue).sum()
                    + " mutations sur 150 ont fait echouer le pipeline, " + examples.size() + " cause(s) distincte(s) :\n");
            examples.forEach((cause, example) -> report.append("\n=== ").append(counts.get(cause)).append(" x ")
                    .append(cause).append("\n").append(example).append("\n"));
            org.junit.jupiter.api.Assertions.fail(report.toString());
        }
    }

    @Test
    void typesAreAllDistinctAndKindsAreCoherent(@TempDir Path tmp) throws IOException{
        Generated g = write(tmp, 77, 25, 5);
        ProjectFacts f = TestSupport.facts(tmp);
        Set<String> names = f.types().stream().map(TypeFact::qualifiedName).collect(Collectors.toSet());
        assertEquals(f.types().size(), names.size());
        Map<Boolean, Long> byKind = f.types().stream()
                .collect(Collectors.partitioningBy(t -> t.kind() == TypeKind.INTERFACE, Collectors.counting()));
        assertEquals(g.types().stream().filter(Type::isInterface).count(), byKind.get(true).longValue());
        assertEquals(g.types().stream().filter(t -> !t.isInterface()).count(), byKind.get(false).longValue());
    }
}