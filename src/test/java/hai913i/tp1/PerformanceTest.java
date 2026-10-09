package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.SourceGenerator.Generated;
import hai913i.tp1.model.ProjectFacts;

/**
 * Performance. Le test rapide tourne toujours ; le test d'echelle est reserve a : mvn test -Dperf=true
 * (il compare les temps pour N et 4N classes : un comportement quadratique donnerait un rapport proche de 16).
 */
class PerformanceTest {

    private static Generated write(Path dir, int classes) {
        Generated g = SourceGenerator.generate(2026, classes, 6);
        g.files().forEach((p, c) -> TestSupport.write(dir, p, c));
        return g;
    }

    private static Duration time(Path dir) throws IOException{
        long start = System.nanoTime();
        TestSupport.facts(dir);
        return Duration.ofNanos(System.nanoTime() - start);
    }

    @Test
    @Timeout(120)
    void threeHundredClassesAreAnalysedQuicklyAndCorrectly(@TempDir Path tmp) throws IOException{
        Generated g = write(tmp, 300);
        long start = System.nanoTime();
        ProjectFacts f = TestSupport.facts(tmp);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("300 classes (" + g.files().size() + " fichiers, " + g.totalLines() + " lignes) : " + ms + " ms");
        assertEquals(g.types().size(), f.types().size());
        assertEquals(g.totalLines(), f.totalLines());
        long expectedCalls = g.types().stream().flatMap(t -> t.methods().stream()).mapToLong(m -> m.calls().size()).sum();
        assertEquals(expectedCalls, TestSupport.allCalls(f).size());
    }

    @Test
    @Timeout(900)
    void timeGrowsRoughlyLinearlyWithProjectSize(@TempDir Path tmp)throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("perf"), "test d'echelle desactive (ajouter -Dperf=true)");
        write(tmp.resolve("warmup"), 100);
        time(tmp.resolve("warmup"));

        write(tmp.resolve("n"), 500);
        write(tmp.resolve("4n"), 2000);
        Duration small = time(tmp.resolve("n"));
        Duration big = time(tmp.resolve("4n"));
        double ratio = (double) big.toNanos() / small.toNanos();
        System.out.printf("500 classes : %d ms ; 2000 classes : %d ms ; rapport %.1f (lineaire = 4, quadratique = 16)%n",
                small.toMillis(), big.toMillis(), ratio);
        assertTrue(ratio < 10, "rapport " + ratio + " : croissance suspecte (quadratique ?)");
        assertTrue(big.toSeconds() < 300, "2000 classes en " + big.toSeconds() + " s");
    }
}
