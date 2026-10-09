package hai913i.tp1.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import hai913i.tp1.cli.Command.Action;

/** B4 : lecture des arguments, sans rien executer. */
class CommandLineTest {

    private static CliException refused(String... args) {
        CliException e = assertThrows(CliException.class, () -> CommandLine.parse(List.of(args)));
        assertEquals(Cli.USAGE_ERROR, e.exitCode());
        return e;
    }

    @Test
    void simpleActionsTakeNoArgument() {
        for (Action a : List.of(Action.STRUCTURE, Action.CALLS, Action.GRAPH, Action.DOT)) {
            Command c = CommandLine.parse(List.of("proj", a.keyword()));
            assertEquals("proj", c.project());
            assertEquals(a, c.action());
            assertNull(c.method());
        }
    }

    @Test
    void actionsAreCaseInsensitive() {
        assertEquals(Action.GRAPH, CommandLine.parse(List.of("p", "GRAPH")).action());
    }

    @Test
    void metricsReadsTheThreshold() {
        Command c = CommandLine.parse(List.of("p", "metrics", "5"));
        assertEquals(Action.METRICS, c.action());
        assertEquals(5, c.threshold());
        assertEquals(0, CommandLine.parse(List.of("p", "metrics", "0")).threshold());
        assertEquals(7, CommandLine.parse(List.of("p", "metrics", " 7 ")).threshold());
    }

    @Test
    void callersAndCalleesReadTheMethod() {
        Command c = CommandLine.parse(List.of("p", "callers", "Item#checkOut"));
        assertEquals(Action.CALLERS, c.action());
        assertEquals("Item#checkOut", c.method());
        assertEquals(Action.CALLEES, CommandLine.parse(List.of("p", "callees", "X#y")).action());
    }

    @Test
    void projectPathWithSpacesIsKeptWhole() {
        assertEquals("mon projet (copie)", CommandLine.parse(List.of("mon projet (copie)", "graph")).project());
    }

    @Test
    void missingPiecesAreRefused() {
        assertTrue(refused().getMessage().contains("manquant"));
        assertTrue(refused("p").getMessage().contains("Action"));
        assertTrue(refused("p", "metrics").getMessage().contains("X"));
        assertTrue(refused("p", "callers").getMessage().contains("methode"));
        assertTrue(refused("p", "callees").getMessage().contains("methode"));
    }

    @Test
    void unknownActionIsNamed() {
        assertTrue(refused("p", "zzz").getMessage().contains("zzz"));
    }

    @Test
    void badThresholdsAreRefusedAndQuoted() {
        for (String bad : List.of("abc", "-1", "5.5", "", "1e3", "99999999999", "cinq")) {
            CliException e = refused("p", "metrics", bad);
            assertTrue(e.getMessage().contains("'" + bad + "'"), e.getMessage());
        }
    }

    @Test
    void extraArgumentsAreRefused() {
        assertTrue(refused("p", "metrics", "5", "6").getMessage().contains("6"));
        assertTrue(refused("p", "graph", "5").getMessage().contains("5"));
        assertTrue(refused("p", "callers", "a", "b").getMessage().contains("b"));
    }

    @Test
    void usageDocumentsEveryAction() {
        for (Action a : Action.values()) {
            assertTrue(CommandLine.USAGE.contains(a.keyword()), a.keyword());
        }
        assertTrue(CommandLine.USAGE.contains("Codes de sortie"));
    }
}
