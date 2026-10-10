package hai913i.tp1.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import hai913i.tp1.cli.Command.Action;

/** Options du bonus : --cha, --package, --focus. */
class CommandLineOptionsTest {

    private static Command parse(String... args) {
        return CommandLine.parse(List.of(args));
    }

    private static CliException refused(String... args) {
        CliException e = assertThrows(CliException.class, () -> parse(args));
        assertEquals(Cli.USAGE_ERROR, e.exitCode());
        return e;
    }

    @Test
    void noOptionByDefault() {
        Command c = parse("p", "dot");
        assertFalse(c.cha());
        assertNull(c.packageFilter());
        assertNull(c.focus());
    }

    @Test
    void chaIsAcceptedBeforeOrAfterTheArgument() {
        assertTrue(parse("p", "graph", "--cha").cha());
        assertTrue(parse("p", "dot", "--cha").cha());
        Command c = parse("p", "callers", "--cha", "Item#checkOut");
        assertTrue(c.cha());
        assertEquals("Item#checkOut", c.method());
        assertTrue(parse("p", "callees", "Item#checkOut", "--cha").cha());
    }

    @Test
    void dotAcceptsPackageAndFocus() {
        Command c = parse("p", "dot", "--package", "library.service", "--focus", "LoanService#borrow", "--cha");
        assertEquals(Action.DOT, c.action());
        assertEquals("library.service", c.packageFilter());
        assertEquals("LoanService#borrow", c.focus());
        assertTrue(c.cha());
    }

    @Test
    void optionNeedingAValueRefusesAMissingOne() {
        assertTrue(refused("p", "dot", "--package").getMessage().contains("--package"));
        assertTrue(refused("p", "dot", "--focus").getMessage().contains("--focus"));
        assertTrue(refused("p", "dot", "--focus", "--cha").getMessage().contains("--focus"));
    }

    @Test
    void unknownOptionIsRefusedAndQuoted() {
        assertTrue(refused("p", "graph", "--verbose").getMessage().contains("--verbose"));
    }

    @Test
    void chaIsRefusedOnActionsWithoutAGraph() {
        assertTrue(refused("p", "structure", "--cha").getMessage().contains("--cha"));
        assertTrue(refused("p", "calls", "--cha").getMessage().contains("structure") == false);
        assertTrue(refused("p", "metrics", "5", "--cha").getMessage().contains("metrics"));
    }

    @Test
    void packageAndFocusAreRefusedOutsideDot() {
        assertTrue(refused("p", "graph", "--package", "a").getMessage().contains("dot"));
        assertTrue(refused("p", "callers", "M", "--focus", "M").getMessage().contains("dot"));
    }

    @Test
    void optionsDoNotHideMissingArguments() {
        assertTrue(refused("p", "callers", "--cha").getMessage().contains("methode"));
        assertTrue(refused("p", "metrics").getMessage().contains("X"));
    }

    @Test
    void extraPositionalArgumentsAreStillRefused() {
        assertTrue(refused("p", "graph", "--cha", "extra").getMessage().contains("extra"));
    }

    @Test
    void usageDocumentsTheOptions() {
        assertTrue(CommandLine.USAGE.contains("--cha"));
        assertTrue(CommandLine.USAGE.contains("--package"));
        assertTrue(CommandLine.USAGE.contains("--focus"));
    }
}
