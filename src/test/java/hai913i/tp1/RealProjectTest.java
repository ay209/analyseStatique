package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.ProjectSources;

/**
 * Projets reels : l'analyseur s'analyse lui-meme (auto-analyse), et, en option, n'importe quel projet :
 *   mvn test -Dreal.project.dir=/chemin/vers/un/projet   (par exemple un depot clone depuis GitHub).
 */
class RealProjectTest {

    private static ProjectFacts analyse(Path root, List<String> classpath) throws IOException {
        return TestSupport.extract(JdtParser.parse(ProjectSources.of(root), classpath));
    }

    private static String stats(String label, ProjectFacts f) {
        List<CallFact> calls = TestSupport.allCalls(f);
        long resolved = calls.stream().filter(CallFact::resolved).count();
        return String.format("%s : %d types, %d methodes, %d appels (%d resolus, %d internes), %d lignes", label,
                f.types().size(), TestSupport.allMethods(f).size(), calls.size(), resolved,
                calls.stream().filter(CallFact::internal).count(), f.totalLines());
    }

    private static List<String> testClasspath() {
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path", ""));
        return Arrays.stream(cp.split(File.pathSeparator)).filter(s -> !s.isBlank()).toList();
    }

    @Test
    void theAnalyserCanAnalyseItself() throws IOException{
        Path root = Path.of("").toAbsolutePath();
        Assumptions.assumeTrue(root.resolve("src/main/java").toFile().isDirectory(), "pas lance depuis la racine du projet");

        ProjectFacts bare = analyse(root, List.of());
        ProjectFacts withCp = analyse(root, testClasspath());
        System.out.println(stats("auto-analyse sans classpath", bare));
        System.out.println(stats("auto-analyse avec classpath ", withCp));

        for (String expected : List.of("StructureVisitor", "CallVisitor", "FactExtractor", "JdtParser", "TypeFact")) {
            assertTrue(TestSupport.hasType(bare, expected), expected);
        }
        assertEquals(bare.types().stream().map(t -> t.qualifiedName()).toList(),
                withCp.types().stream().map(t -> t.qualifiedName()).toList(),
                "le classpath ne change pas la STRUCTURE, seulement la resolution des appels");
        long resolvedBare = TestSupport.allCalls(bare).stream().filter(CallFact::resolved).count();
        long resolvedCp = TestSupport.allCalls(withCp).stream().filter(CallFact::resolved).count();
        assertTrue(resolvedCp >= resolvedBare, "ajouter le classpath ne peut que resoudre plus d'appels");
        assertTrue(TestSupport.allCalls(withCp).size() >= 100, "le code de l'analyseur contient bien plus de 100 appels");

        FactsInvariants.checkStrict(bare);
        FactsInvariants.checkStrict(withCp);
        assertEquals(bare, analyse(root, List.of()), "deterministe");
    }

    @Test
    void anyRealProjectGivenOnTheCommandLine()throws IOException {
        String dir = System.getProperty("real.project.dir");
        Assumptions.assumeTrue(dir != null, "pas de -Dreal.project.dir");
        Path root = Path.of(dir);
        long start = System.nanoTime();
        ProjectFacts f = analyse(root, List.of());
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println(stats(root.getFileName().toString(), f) + " en " + ms + " ms");
        assertTrue(f.types().size() > 0, "aucun type trouve dans " + root);
        FactsInvariants.checkWeak(f);
        assertEquals(f, analyse(root, List.of()), "deterministe");
    }
}
