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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SQUELETTE B4 : le CLI n'existe pas encore, donc DESACTIVE. Test de bout en bout contre le JAR (apres mvn package),
 * d'ou le suffixe IT (surefire l'ignore ; failsafe le lancera quand on le configurera, etape CI).
 *
 * A VALIDER AVEC LE SUJET AVANT D'ACTIVER : le contrat ci-dessous (0 = succes, non nul = erreur, message court sur stderr,
 * jamais de trace Java) est une proposition. Retirer @Disabled et ajuster les attentes des que B4 est ecrit.
 */
@Disabled("B4 : CLI pas encore ecrit")
class CliIT {

    private static final Path JAR = Path.of("target/hai913i-tp1-analyzer.jar");

    private record Result(int exit, String out, String err) {
    }

    private static Result run(String... args) throws IOException, InterruptedException {
        assertTrue(Files.exists(JAR), "construire le jar d'abord : mvn package -DskipTests");
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
        assertFalse(r.err().contains("\tat ") || r.err().contains("Exception in thread"), "pas de trace de pile : " + r.err());
    }

    @Test
    void noArgumentsPrintsUsage() throws Exception {
        Result r = run();
        assertCleanError(r);
        assertTrue(r.err().toLowerCase().contains("usage") || r.err().toLowerCase().contains("utilisation"), r.err());
    }

    @Test
    void unknownPathIsReportedWithThePath() throws Exception {
        Result r = run("/chemin/qui/n/existe/pas", "report");
        assertCleanError(r);
        assertTrue(r.err().contains("/chemin/qui/n/existe/pas"), r.err());
    }

    @Test
    void pathThatIsAFileIsRejected(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("x.txt"), "x");
        assertCleanError(run(file.toString(), "report"));
    }

    @Test
    void unknownActionIsRejected() throws Exception {
        assertCleanError(run(TestSupport.validationRoot().toString(), "action-inconnue"));
    }

    @Test
    void negativeXIsRejected() throws Exception {
        assertCleanError(run(TestSupport.validationRoot().toString(), "classes-with-more-methods-than", "-1"));
    }

    @Test
    void nonNumericXIsRejected() throws Exception {
        assertCleanError(run(TestSupport.validationRoot().toString(), "classes-with-more-methods-than", "abc"));
    }

    @Test
    void missingXIsRejected() throws Exception {
        assertCleanError(run(TestSupport.validationRoot().toString(), "classes-with-more-methods-than"));
    }

    @Test
    void emptyProjectIsNotAnError(@TempDir Path tmp) throws Exception {
        Result r = run(tmp.toString(), "report");
        assertEquals(0, r.exit(), r.err());
        assertFalse(r.out().isBlank());
    }

    @Test
    void validationReportSucceeds() throws Exception {
        Result r = run(TestSupport.validationRoot().toString(), "report");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("15"), "15 classes attendues dans le rapport");
    }

    @Test
    void pathWithSpacesWorks(@TempDir Path tmp) throws Exception {
        Path copy = Files.createDirectories(tmp.resolve("mon projet"));
        TestSupport.copyTree(TestSupport.validationRoot(), copy);
        assertEquals(0, run(copy.toString(), "report").exit());
    }
}
