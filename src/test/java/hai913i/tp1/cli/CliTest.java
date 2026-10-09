package hai913i.tp1.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.TestSupport;

/** B4 : la ligne de commande de bout en bout, dans la meme JVM (le test sur le JAR est CliIT). */
class CliTest {

    private record Result(int exit, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new Cli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)).run(args);
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static String validation() {
        return TestSupport.validationRoot().toString();
    }

    private static void assertCleanError(Result r, int expectedExit) {
        assertEquals(expectedExit, r.exit(), r.err());
        assertFalse(r.err().isBlank(), "un message d'erreur est attendu");
        assertFalse(r.err().contains("\tat ") || r.err().contains("Exception in thread"), "pas de pile : " + r.err());
        assertTrue(r.out().isBlank(), "rien sur la sortie standard en cas d'erreur : " + r.out());
    }

    // ---------- aide et arguments ----------

    @Test
    void helpPrintsUsageOnStdoutAndSucceeds() {
        for (String flag : new String[] { "--help", "-h", "help" }) {
            Result r = run(flag);
            assertEquals(0, r.exit());
            assertTrue(r.out().contains("Usage"));
            assertTrue(r.err().isBlank());
        }
    }

    @Test
    void noArgumentsIsAUsageError() {
        Result r = run();
        assertEquals(2, r.exit());
        assertTrue(r.err().contains("Usage"), r.err());
    }

    @Test
    void badArgumentsAreUsageErrors() {
        assertCleanErrorWithUsage(run(validation(), "zzz"));
        assertCleanErrorWithUsage(run(validation(), "metrics"));
        assertCleanErrorWithUsage(run(validation(), "metrics", "abc"));
        assertCleanErrorWithUsage(run(validation(), "metrics", "-1"));
        assertCleanErrorWithUsage(run(validation(), "callers"));
    }

    private static void assertCleanErrorWithUsage(Result r) {
        assertCleanError(r, 2);
        assertTrue(r.err().contains("Usage"), r.err());
    }

    @Test
    void badThresholdIsCheckedBeforeAnyAnalysis() {
        // meme avec un dossier inexistant, c'est l'erreur d'arguments qui est signalee
        Result r = run("/chemin/qui/n/existe/pas", "metrics", "abc");
        assertEquals(2, r.exit());
        assertTrue(r.err().contains("abc"));
    }

    // ---------- dossier ----------

    @Test
    void missingDirectoryIsReportedWithItsPath() {
        Result r = run("/chemin/qui/n/existe/pas", "structure");
        assertCleanError(r, 1);
        assertTrue(r.err().contains("/chemin/qui/n/existe/pas"), r.err());
    }

    @Test
    void aFileInsteadOfADirectoryIsRejected(@TempDir Path tmp) throws IOException {
        Path file = Files.writeString(tmp.resolve("x.txt"), "x");
        assertCleanError(run(file.toString(), "structure"), 1);
    }

    @Test
    void directoryWithoutJavaFilesIsRejected(@TempDir Path tmp) {
        TestSupport.write(tmp, "notes.txt", "pas de java ici");
        Result r = run(tmp.toString(), "structure");
        assertCleanError(r, 1);
        assertTrue(r.err().contains("Aucun fichier Java"), r.err());
    }

    @Test
    void emptyDirectoryIsRejected(@TempDir Path tmp) {
        assertCleanError(run(tmp.toString(), "metrics", "5"), 1);
    }

    // ---------- points de controle sur le projet de validation ----------

    @Test
    void structureListsTheFifteenTypes() {
        Result r = run(validation(), "structure");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.err().isBlank(), r.err());
        assertTrue(r.out().contains("library.model.Item [CLASS]"), r.out());
        assertTrue(r.out().contains("library.model.Category [ENUM]"));
        assertTrue(r.out().contains("14 fichier(s), 15 type(s), 59 methode(s), 493 ligne(s)."), r.out());
    }

    @Test
    void callsShowsInternalAndExternalTargets() {
        Result r = run(validation(), "calls");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("94 appel(s)."), r.out());
        assertTrue(r.out().contains("[interne]"));
        assertTrue(r.out().contains("[externe]"));
        assertFalse(r.out().contains("[non resolu]"));
    }

    @Test
    void metricsMatchTheCheckpoint() {
        Result r = run(validation(), "metrics", "5");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("Q1  classes                  : 15"), r.out());
        assertTrue(r.out().contains(": 3,93"));
        assertTrue(r.out().contains(": 4,64"));
        assertTrue(r.out().contains(": 1,40"));
        assertTrue(r.out().contains("library.model.Item(10)"));
        assertTrue(r.out().contains("library.util.Statistics#report("));
    }

    @Test
    void thresholdChangesQuestion11() {
        String five = run(validation(), "metrics", "5").out();
        String four = run(validation(), "metrics", "4").out();
        assertNotEquals(five, four);
        assertTrue(four.contains("library.util.TextUtils(5)"));
        assertFalse(five.contains("library.util.TextUtils(5)"));
    }

    @Test
    void graphMatchesTheCheckpoint() {
        Result r = run(validation(), "graph");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("Noeuds (methodes)       : 59"), r.out());
        assertTrue(r.out().contains("Arcs                    : 44"));
        assertTrue(r.out().contains("internes              : 46"));
        assertTrue(r.out().contains("externes              : 48"));
        assertTrue(r.out().contains("non resolus           : 0"));
    }

    @Test
    void dotIsAGraphvizDocument() {
        Result r = run(validation(), "dot");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().startsWith("digraph calls {"));
        assertTrue(r.out().trim().endsWith("}"));
    }

    @Test
    void callersOfTheInterfaceMethodIsOnlyBorrow() {
        Result r = run(validation(), "callers", "Loanable#checkOut");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("LoanService#borrow("), r.out());
    }

    @Test
    void itemCheckOutHasNoCaller() {
        Result r = run(validation(), "callers", "Item#checkOut");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("(aucun)"), r.out());
    }

    @Test
    void calleesAreListed() {
        Result r = run(validation(), "callees", "LoanService#borrow");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("library.model.Loanable#checkOut"), r.out());
    }

    @Test
    void unknownMethodIsAnAnalysisError() {
        Result r = run(validation(), "callers", "Nope#nothing");
        assertCleanError(r, 1);
        assertTrue(r.err().contains("Nope#nothing"));
    }

    // ---------- robustesse ----------

    @Test
    void syntaxErrorsAreReportedButTheOtherFilesAreAnalysed(@TempDir Path tmp) {
        TestSupport.write(tmp, "src/p/Broken.java", "package p;\nclass Broken {\n    void f( {\n    }\n}\n");
        TestSupport.write(tmp, "src/p/Helper.java", "package p;\nclass Helper {\n    void h() { }\n}\n");
        Result r = run(tmp.toString(), "structure");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.err().contains("Avertissement"), r.err());
        assertTrue(r.err().contains("Broken.java"), r.err());
        assertFalse(r.err().contains("Helper.java"), r.err());
        assertTrue(r.out().contains("p.Helper"), r.out());
        assertFalse(r.err().contains("\tat "));
    }

    @Test
    void missingDependencyIsAnUnresolvedCallAndTheAnalysisGoesOn(@TempDir Path tmp) {
        TestSupport.write(tmp, "src/p/A.java", """
                package p;
                import org.nolib.Fmt;
                class A {
                    int f(String s) {
                        Fmt.shout(s);
                        return s.length();
                    }
                }
                """);
        Result r = run(tmp.toString(), "calls");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("[non resolu]"), r.out());
        assertTrue(r.out().contains("[externe]"), r.out());
        assertTrue(r.err().contains("Avertissement"), r.err());
    }

    @Test
    void pathWithSpacesWorks(@TempDir Path tmp) throws IOException {
        Path copy = Files.createDirectories(tmp.resolve("mon projet (copie)"));
        TestSupport.copyTree(TestSupport.validationRoot(), copy);
        Result r = run(copy.toString(), "graph");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("Arcs                    : 44"));
    }

    @Test
    void outputIsIdenticalAcrossRuns() {
        assertEquals(run(validation(), "structure").out(), run(validation(), "structure").out());
        assertEquals(run(validation(), "graph").out(), run(validation(), "graph").out());
        assertEquals(run(validation(), "metrics", "5").out(), run(validation(), "metrics", "5").out());
    }
}
