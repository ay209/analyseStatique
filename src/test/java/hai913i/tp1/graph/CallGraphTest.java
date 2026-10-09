package hai913i.tp1.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import hai913i.tp1.graph.CallGraph.Arc;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.model.TypeKind;

/** B3 : graphe d'appel sur des modeles construits a la main (sans JDT, sans fichier). */
class CallGraphTest {

    private static CallFact internal(String from, String to) {
        return new CallFact(from, Optional.of(to), "p.X", 1, true, true);
    }

    private static CallFact external(String from, String to) {
        return new CallFact(from, Optional.of(to), "java.util.List", 1, true, false);
    }

    private static CallFact unresolved(String from) {
        return new CallFact(from, Optional.empty(), "?", 1, false, false);
    }

    private static MethodFact m(String id, CallFact... calls) {
        String name = id.substring(id.indexOf('#') + 1, id.indexOf('('));
        return new MethodFact(id, name, List.of(), false, 1, List.of(calls));
    }

    private static ProjectFacts facts(MethodFact... methods) {
        TypeFact t = new TypeFact("p.X", "p", TypeKind.CLASS, List.of(), List.of(), List.of(), List.of(methods));
        return new ProjectFacts(List.of(t), List.<Path>of(), 10);
    }

    @Test
    void emptyProject() {
        CallGraph g = CallGraph.build(new ProjectFacts(List.of(), List.<Path>of(), 0));
        assertEquals(0, g.nodeCount());
        assertEquals(0, g.arcCount());
        assertEquals(0, g.callSiteCount());
        assertTrue(g.arcs().isEmpty());
    }

    @Test
    void everyMethodIsANodeEvenIfIsolated() {
        CallGraph g = CallGraph.build(facts(m("p.X#a()"), m("p.X#b()")));
        assertEquals(2, g.nodeCount());
        assertEquals(0, g.arcCount());
        assertTrue(g.callees("p.X#a()").isEmpty());
        assertTrue(g.callers("p.X#a()").isEmpty());
    }

    @Test
    void repeatedCallsBecomeOneWeightedArc() {
        CallGraph g = CallGraph.build(facts(
                m("p.X#a()", internal("p.X#a()", "p.X#b()"), internal("p.X#a()", "p.X#b()"),
                        internal("p.X#a()", "p.X#b()")),
                m("p.X#b()")));
        assertEquals(1, g.arcCount());
        assertEquals(List.of(new Arc("p.X#a()", "p.X#b()", 3)), g.arcs());
        assertEquals(3, g.internalCallSites());
    }

    @Test
    void selfCallIsAnArc() {
        CallGraph g = CallGraph.build(facts(m("p.X#a()", internal("p.X#a()", "p.X#a()"))));
        assertEquals(List.of(new Arc("p.X#a()", "p.X#a()", 1)), g.arcs());
        assertEquals(1, g.callers("p.X#a()").size());
        assertEquals(1, g.callees("p.X#a()").size());
    }

    @Test
    void externalAndUnresolvedCallsAreCountedButNotArcs() {
        CallGraph g = CallGraph.build(facts(
                m("p.X#a()", external("p.X#a()", "java.util.List#add(java.lang.Object)"), unresolved("p.X#a()"),
                        internal("p.X#a()", "p.X#b()")),
                m("p.X#b()")));
        assertEquals(1, g.arcCount());
        assertEquals(3, g.callSiteCount());
        assertEquals(1, g.internalCallSites());
        assertEquals(1, g.externalCallSites());
        assertEquals(1, g.unresolvedCallSites());
        assertEquals(0, g.danglingCallSites());
    }

    @Test
    void internalCallToAnUnknownNodeIsCountedButNotAnArc() {
        CallGraph g = CallGraph.build(facts(m("p.X#a()", internal("p.X#a()", "p.Y#ghost()"))));
        assertEquals(0, g.arcCount());
        assertEquals(1, g.danglingCallSites());
        assertEquals(1, g.callSiteCount());
    }

    @Test
    void callersAndCalleesAreSortedAndConsistent() {
        CallGraph g = CallGraph.build(facts(
                m("p.X#c()", internal("p.X#c()", "p.X#a()")),
                m("p.X#b()", internal("p.X#b()", "p.X#a()"), internal("p.X#b()", "p.X#a()")),
                m("p.X#a()")));
        assertEquals(List.of(new Arc("p.X#b()", "p.X#a()", 2), new Arc("p.X#c()", "p.X#a()", 1)),
                g.callers("p.X#a()"));
        // tout arc sortant d'un nœud est entrant pour l'autre
        for (Arc arc : g.arcs()) {
            assertTrue(g.callees(arc.caller()).contains(arc));
            assertTrue(g.callers(arc.callee()).contains(arc));
        }
        List<Arc> sorted = new ArrayList<>(g.arcs());
        sorted.sort((x, y) -> (x.caller() + x.callee()).compareTo(y.caller() + y.callee()));
        assertEquals(sorted, g.arcs());
    }

    @Test
    void unknownMethodIsRejected() {
        CallGraph g = CallGraph.build(facts(m("p.X#a()")));
        assertThrows(IllegalArgumentException.class, () -> g.callers("p.X#nope()"));
        assertThrows(IllegalArgumentException.class, () -> g.callees("p.X#nope()"));
        assertFalse(g.contains("p.X#nope()"));
    }

    @Test
    void findAcceptsExactIdTypeAndMethodOrQualifiedName() {
        CallGraph g = CallGraph.build(facts(m("p.X#a()"), m("p.X#a(int)"), m("p.X#b()")));
        assertEquals(List.of("p.X#b()"), g.find("p.X#b()"));
        assertEquals(List.of("p.X#b()"), g.find("X#b"));
        assertEquals(List.of("p.X#b()"), g.find("p.X#b"));
        assertEquals(List.of("p.X#a()", "p.X#a(int)"), g.find("X#a"), "surcharges : plusieurs resultats");
        assertTrue(g.find("X#zzz").isEmpty());
        assertTrue(g.find("Y#b").isEmpty());
    }

    @Test
    void dotListsEveryArcAndShowsWeights() {
        CallGraph g = CallGraph.build(facts(
                m("p.X#a()", internal("p.X#a()", "p.X#b()"), internal("p.X#a()", "p.X#b()")), m("p.X#b()")));
        String dot = g.toDot();
        assertTrue(dot.startsWith("digraph calls {"));
        assertTrue(dot.contains("\"p.X#a()\" -> \"p.X#b()\" [label=\"2\"];"), dot);
        assertTrue(dot.trim().endsWith("}"));
    }
}
