package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B4 : test de bout en bout contre le JAR construit (mvn verify : failsafe le lance apres package).
 * Contrat : 0 succes ; 1 analyse impossible ; 2 arguments invalides ; message sur stderr, jamais de pile.
 */
class CliIT {

    private static final Path JAR = Path.of("target/hai913i-tp1-analyzer.jar");

    private record Result(int exit, String out, String err) {
    }

    private static Result run(String... args) throws IOException, InterruptedException {
        assertTrue(Files.exists(JAR), "construire le jar d'abord : mvn package");
        List<String> cmd = new ArrayList<>(List.of(System.getProperty("java.home") + "/bin/java", "-jar", JAR.toString()));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "le CLI ne termine pas");
        return new Result(p.exitValue(), out, err);
    }

    private static void assertCleanError(Result r) {
        assertNotEquals(0, r.exit(), "code de sortie non nul attendu");
        assertFalse(r.err().isBlank(), "un message d'erreur sur stderr est attendu");
        assertFalse(r.err().contains("\tat ") || r.err().contains("Exception in thread"), "pas de pile : " + r.err());
    }

    private static String validation() {
        return Path.of("src/test/resources/validation").toString();
    }

    @Test
    void noArgumentsPrintsUsage() throws Exception {
        Result r = run();
        assertCleanError(r);
        assertTrue(r.err().contains("Usage"), r.err());
    }

    @Test
    void unknownPathIsReportedWithThePath() throws Exception {
        Result r = run("/chemin/qui/n/existe/pas", "structure");
        assertCleanError(r);
        assertTrue(r.err().contains("/chemin/qui/n/existe/pas"), r.err());
    }

    @Test
    void directoryWithoutJavaFilesIsRejected(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("notes.txt"), "x");
        Result r = run(tmp.toString(), "structure");
        assertCleanError(r);
        assertEquals(1, r.exit());
    }

    @Test
    void nonNumericXIsRejected() throws Exception {
        Result r = run(validation(), "metrics", "abc");
        assertCleanError(r);
        assertEquals(2, r.exit());
        assertTrue(r.err().contains("abc"));
    }

    @Test
    void validationMetricsSucceed() throws Exception {
        Result r = run(validation(), "metrics", "5");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("3,93"), r.out());
    }

    @Test
    void validationGraphSucceeds() throws Exception {
        Result r = run(validation(), "graph");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("Arcs                    : 44"), r.out());
    }

    @Test
    void syntaxErrorDoesNotStopTheAnalysis(@TempDir Path tmp) throws Exception {
        Path src = Files.createDirectories(tmp.resolve("src/p"));
        Files.writeString(src.resolve("Broken.java"), "package p;\nclass Broken {\n    void f( {\n    }\n}\n");
        Files.writeString(src.resolve("Helper.java"), "package p;\nclass Helper {\n    void h() { }\n}\n");
        Result r = run(tmp.toString(), "structure");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.err().contains("Broken.java"));
        assertTrue(r.out().contains("p.Helper"));
    }

    @Test
    void pathWithSpacesWorks(@TempDir Path tmp) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("mon projet/src/p"));
        Files.writeString(dir.resolve("A.java"), "package p;\nclass A { void f() { } }\n");
        assertEquals(0, run(tmp.resolve("mon projet").toString(), "structure").exit());
    }
}
