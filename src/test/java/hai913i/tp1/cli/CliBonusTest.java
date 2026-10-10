package hai913i.tp1.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import hai913i.tp1.TestSupport;

/** Bonus C2 et C3 de bout en bout (meme JVM) sur le projet de validation. */
class CliBonusTest {

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

    private static int arcs(String graphOutput) {
        for (String line : graphOutput.split("\n")) {
            if (line.startsWith("Arcs   ")) {
                return Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        throw new AssertionError("ligne 'Arcs' absente : " + graphOutput);
    }

    // ---------- C3 : CHA ----------

    @Test
    void chaAddsArcsAndKeepsTheSiteStatistics() {
        Result plain = run(validation(), "graph");
        Result cha = run(validation(), "graph", "--cha");
        assertEquals(0, cha.exit(), cha.err());
        assertTrue(arcs(cha.out()) > arcs(plain.out()), cha.out());
        assertTrue(cha.out().contains("Mode CHA"), cha.out());
        assertFalse(plain.out().contains("Mode CHA"));
        assertTrue(cha.out().contains("Noeuds (methodes)       : 59"), cha.out());
        assertTrue(cha.out().contains("Sites d'appel (total)   : 94"), cha.out());
    }

    @Test
    void chaMakesItemCheckOutReachableThroughTheInterface() {
        Result r = run(validation(), "callers", "Item#checkOut", "--cha");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("LoanService#borrow("), r.out());
    }

    @Test
    void withoutChaTheResultsOfTheSubjectAreUnchanged() {
        Result r = run(validation(), "graph");
        assertTrue(r.out().contains("Arcs                    : 44"), r.out());
    }

    // ---------- C2 : DOT filtre ----------

    @Test
    void focusDrawsOnlyTheNeighbourhood() {
        Result r = run(validation(), "dot", "--focus", "LoanService#borrow");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().startsWith("digraph calls {"));
        assertTrue(r.out().contains("LoanService#borrow("));
        assertTrue(r.out().length() < run(validation(), "dot").out().length());
    }

    @Test
    void packageFilterKeepsOnlyThatPackage() {
        Result r = run(validation(), "dot", "--package", "library.model");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.out().contains("library.model."));
        assertFalse(r.out().contains("library.service."), r.out());
    }

    @Test
    void unknownPackageWarnsButSucceeds() {
        Result r = run(validation(), "dot", "--package", "nope");
        assertEquals(0, r.exit(), r.err());
        assertTrue(r.err().contains("nope"), r.err());
        assertTrue(r.out().trim().endsWith("}"));
    }

    @Test
    void unknownFocusIsAnAnalysisError() {
        Result r = run(validation(), "dot", "--focus", "Nope#nothing");
        assertEquals(1, r.exit());
        assertTrue(r.err().contains("Nope#nothing"));
        assertTrue(r.out().isBlank());
    }

    @Test
    void invalidOptionsAreUsageErrors() {
        assertEquals(2, run(validation(), "structure", "--cha").exit());
        assertEquals(2, run(validation(), "graph", "--package", "x").exit());
        assertEquals(2, run(validation(), "dot", "--focus").exit());
        assertEquals(2, run(validation(), "dot", "--bogus").exit());
    }
}
