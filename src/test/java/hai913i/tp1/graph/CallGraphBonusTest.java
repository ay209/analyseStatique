package hai913i.tp1.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import hai913i.tp1.graph.CallGraph.Arc;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.model.TypeKind;

/** Bonus C2 (DOT filtre) et C3 (option CHA), sur des modeles construits a la main. */
class CallGraphBonusTest {

    private static MethodFact method(String id, boolean constructor, CallFact... calls) {
        String name = id.substring(id.indexOf('#') + 1, id.indexOf('('));
        return new MethodFact(id, name, List.of(), constructor, 1, List.of(calls));
    }

    private static MethodFact method(String id, CallFact... calls) {
        return method(id, false, calls);
    }

    private static CallFact call(String from, String to) {
        return new CallFact(from, Optional.of(to), "x", 1, true, true);
    }

    private static TypeFact type(String name, TypeKind kind, List<String> superclasses, List<String> interfaces,
            MethodFact... methods) {
        String pkg = name.substring(0, name.lastIndexOf('.'));
        return new TypeFact(name, pkg, kind, superclasses, interfaces, List.of(), List.of(methods));
    }

    private static ProjectFacts facts(TypeFact... types) {
        return new ProjectFacts(List.of(types), List.<Path>of(), 10);
    }

    /** Shape (interface) <- Circle, Square (classes) ; Square <- Cube ; Client appelle Shape#area. */
    private static ProjectFacts shapes() {
        return facts(
                type("p.Shape", TypeKind.INTERFACE, List.of(), List.of(), method("p.Shape#area()")),
                type("p.Circle", TypeKind.CLASS, List.of(), List.of("p.Shape"), method("p.Circle#area()")),
                type("p.Square", TypeKind.CLASS, List.of(), List.of("p.Shape"), method("p.Square#area()")),
                type("p.Cube", TypeKind.CLASS, List.of("p.Square"), List.of(), method("p.Cube#area()")),
                type("p.Other", TypeKind.CLASS, List.of(), List.of(), method("p.Other#area()")),
                type("p.Client", TypeKind.CLASS, List.of(), List.of(),
                        method("p.Client#run()", call("p.Client#run()", "p.Shape#area()"))));
    }

    private static List<String> callees(CallGraph g, String id) {
        return g.callees(id).stream().map(Arc::callee).toList();
    }

    // ---------- C3 : CHA ----------

    @Test
    void withoutChaOnlyTheDeclaredTargetIsLinked() {
        CallGraph g = CallGraph.build(shapes());
        assertEquals(List.of("p.Shape#area()"), callees(g, "p.Client#run()"));
        assertFalse(g.chaEnabled());
        assertEquals(0, g.chaCallSites());
    }

    @Test
    void buildWithFalseIsTheDefaultGraph() {
        assertEquals(CallGraph.build(shapes()).arcs(), CallGraph.build(shapes(), false).arcs());
    }

    @Test
    void chaAddsEveryKnownOverrideThroughTheWholeHierarchy() {
        CallGraph g = CallGraph.build(shapes(), true);
        assertEquals(List.of("p.Circle#area()", "p.Cube#area()", "p.Shape#area()", "p.Square#area()"),
                callees(g, "p.Client#run()"));
        assertTrue(g.chaEnabled());
        assertEquals(3, g.chaCallSites());
        assertEquals(4, g.arcCount());
    }

    @Test
    void chaIgnoresUnrelatedTypesWithTheSameSignature() {
        assertFalse(callees(CallGraph.build(shapes(), true), "p.Client#run()").contains("p.Other#area()"));
    }

    @Test
    void chaDoesNotChangeCallSiteStatistics() {
        CallGraph plain = CallGraph.build(shapes());
        CallGraph cha = CallGraph.build(shapes(), true);
        assertEquals(plain.callSiteCount(), cha.callSiteCount());
        assertEquals(plain.internalCallSites(), cha.internalCallSites());
        assertEquals(plain.nodeCount(), cha.nodeCount());
    }

    @Test
    void chaWeightsFollowTheNumberOfCallSites() {
        TypeFact client = type("p.Client", TypeKind.CLASS, List.of(), List.of(),
                method("p.Client#run()", call("p.Client#run()", "p.Shape#area()"),
                        call("p.Client#run()", "p.Shape#area()")));
        ProjectFacts twice = facts(
                type("p.Shape", TypeKind.INTERFACE, List.of(), List.of(), method("p.Shape#area()")),
                type("p.Circle", TypeKind.CLASS, List.of(), List.of("p.Shape"), method("p.Circle#area()")),
                client);
        CallGraph g = CallGraph.build(twice, true);
        assertEquals(2, g.callees("p.Client#run()").stream()
                .filter(a -> a.callee().equals("p.Circle#area()")).findFirst().orElseThrow().weight());
        assertEquals(2, g.chaCallSites());
    }

    @Test
    void chaDoesNotLinkAnOverrideToItself() {
        TypeFact base = type("p.A", TypeKind.CLASS, List.of(), List.of(), method("p.A#m()"));
        TypeFact sub = type("p.B", TypeKind.CLASS, List.of("p.A"), List.of(), method("p.B#m()",
                call("p.B#m()", "p.A#m()")));
        CallGraph g = CallGraph.build(facts(base, sub), true);
        // super.m() depuis B#m : seul A#m est lie, pas une boucle B#m -> B#m
        assertEquals(List.of("p.A#m()"), callees(g, "p.B#m()"));
        assertEquals(0, g.chaCallSites());
    }

    @Test
    void chaIgnoresConstructorsAndAnonymousTypes() {
        TypeFact a = type("p.A", TypeKind.CLASS, List.of(), List.of(), method("p.A#A()", true),
                method("p.A#run()", call("p.A#run()", "p.A#A()")));
        TypeFact b = type("p.B", TypeKind.CLASS, List.of("p.A"), List.of(), method("p.B#A()", true));
        TypeFact anonymous = new TypeFact("", "p", TypeKind.CLASS, List.of("p.A"), List.of(), List.of(),
                List.of(method("#run()")));
        CallGraph g = CallGraph.build(facts(a, b, anonymous), true);
        assertEquals(List.of("p.A#A()"), callees(g, "p.A#run()"));
        assertEquals(0, g.chaCallSites());
    }

    @Test
    void chaSurvivesACyclicHierarchy() {
        TypeFact a = type("p.A", TypeKind.CLASS, List.of("p.B"), List.of(), method("p.A#m()"));
        TypeFact b = type("p.B", TypeKind.CLASS, List.of("p.A"), List.of(), method("p.B#m()",
                call("p.B#m()", "p.A#m()")));
        CallGraph g = CallGraph.build(facts(a, b), true);
        assertTrue(g.contains("p.B#m()"));
    }

    @Test
    void chaFollowsInterfaceInheritance() {
        TypeFact top = type("p.Top", TypeKind.INTERFACE, List.of(), List.of(), method("p.Top#go()"));
        TypeFact mid = type("p.Mid", TypeKind.INTERFACE, List.of(), List.of("p.Top"));
        TypeFact impl = type("p.Impl", TypeKind.CLASS, List.of(), List.of("p.Mid"), method("p.Impl#go()"));
        TypeFact client = type("p.C", TypeKind.CLASS, List.of(), List.of(),
                method("p.C#run()", call("p.C#run()", "p.Top#go()")));
        CallGraph g = CallGraph.build(facts(top, mid, impl, client), true);
        assertEquals(List.of("p.Impl#go()", "p.Top#go()"), callees(g, "p.C#run()"));
    }

    @Test
    void chaDoesNotInventLinksForExternalOrUnresolvedCalls() {
        TypeFact a = type("p.A", TypeKind.CLASS, List.of(), List.of(), method("p.A#m()",
                new CallFact("p.A#m()", Optional.of("java.util.List#add(java.lang.Object)"), "java.util.List", 1, true,
                        false),
                new CallFact("p.A#m()", Optional.empty(), "?", 2, false, false)));
        CallGraph g = CallGraph.build(facts(a), true);
        assertEquals(0, g.arcCount());
        assertEquals(1, g.externalCallSites());
        assertEquals(1, g.unresolvedCallSites());
    }

    // ---------- C2 : DOT filtre ----------

    private static ProjectFacts layered() {
        return facts(
                type("a.X", TypeKind.CLASS, List.of(), List.of(),
                        method("a.X#one()", call("a.X#one()", "b.Y#two()")),
                        method("a.X#lone()")),
                type("b.Y", TypeKind.CLASS, List.of(), List.of(),
                        method("b.Y#two()", call("b.Y#two()", "b.sub.Z#three()")),
                        method("b.Y#back()", call("b.Y#back()", "a.X#one()"))),
                type("b.sub.Z", TypeKind.CLASS, List.of(), List.of(), method("b.sub.Z#three()")));
    }

    @Test
    void neighbourhoodContainsTheNodeItsCallersAndItsCallees() {
        CallGraph g = CallGraph.build(layered());
        assertEquals(Set.of("a.X#one()", "b.Y#two()", "b.Y#back()"), g.neighbourhood("a.X#one()"));
    }

    @Test
    void neighbourhoodOfAnIsolatedNodeIsItself() {
        assertEquals(Set.of("a.X#lone()"), CallGraph.build(layered()).neighbourhood("a.X#lone()"));
    }

    @Test
    void neighbourhoodOfAnUnknownNodeIsRefused() {
        CallGraph g = CallGraph.build(layered());
        assertThrows(IllegalArgumentException.class, () -> g.neighbourhood("nope#x()"));
    }

    @Test
    void inPackageMatchesTypesAndSubPackagesButNotNamePrefixes() {
        CallGraph g = CallGraph.build(layered());
        assertEquals(Set.of("b.Y#two()", "b.Y#back()", "b.sub.Z#three()"), g.inPackage("b"));
        assertEquals(Set.of("b.sub.Z#three()"), g.inPackage("b.sub"));
        assertTrue(g.inPackage("a.X").isEmpty());
        assertTrue(g.inPackage("").isEmpty() || !g.inPackage("").contains("zzz"));
        assertTrue(g.inPackage("c").isEmpty());
    }

    @Test
    void filteredDotKeepsOnlyArcsBetweenKeptNodes() {
        CallGraph g = CallGraph.build(layered());
        String dot = g.toDot(g.inPackage("b"));
        assertTrue(dot.startsWith("digraph calls {"));
        assertTrue(dot.contains("\"b.Y#two()\" -> \"b.sub.Z#three()\";"));
        assertFalse(dot.contains("a.X#one()"));
        assertFalse(dot.contains("\"b.Y#back()\" ->"));
    }

    @Test
    void filteredDotDeclaresIsolatedKeptNodes() {
        CallGraph g = CallGraph.build(layered());
        assertTrue(g.toDot(Set.of("a.X#lone()")).contains("  \"a.X#lone()\";"));
    }

    @Test
    void filteredDotIgnoresIdsThatAreNotNodes() {
        CallGraph g = CallGraph.build(layered());
        assertFalse(g.toDot(Set.of("ghost#x()")).contains("ghost"));
    }

    @Test
    void filteredDotShowsWeightsAboveOne() {
        TypeFact t = type("p.T", TypeKind.CLASS, List.of(), List.of(),
                method("p.T#a()", call("p.T#a()", "p.T#b()"), call("p.T#a()", "p.T#b()")), method("p.T#b()"));
        CallGraph g = CallGraph.build(facts(t));
        assertTrue(g.toDot(Set.of("p.T#a()", "p.T#b()")).contains("[label=\"2\"]"));
    }

    @Test
    void emptyFilterGivesAnEmptyGraph() {
        assertEquals("digraph calls {\n  rankdir=LR;\n  node [shape=box,fontsize=10];\n}\n",
                CallGraph.build(layered()).toDot(Set.of()));
    }

    @Test
    void unfilteredDotIsUnchanged() {
        CallGraph g = CallGraph.build(layered());
        assertTrue(g.toDot().contains("\"a.X#one()\" -> \"b.Y#two()\";"));
        assertFalse(g.toDot().contains("  \"a.X#lone()\";"));
    }
}