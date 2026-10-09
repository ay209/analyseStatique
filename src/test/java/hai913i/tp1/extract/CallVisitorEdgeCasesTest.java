package hai913i.tp1.extract;

import static hai913i.tp1.TestSupport.calls;
import static hai913i.tp1.TestSupport.factsOf;
import static hai913i.tp1.TestSupport.method;
import static hai913i.tp1.TestSupport.type;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.TestSupport;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.ProjectFacts;

/** A3 : cas limites des appels (recepteur statique, cible, resolution, attribution). */
class CallVisitorEdgeCasesTest {

    @Test
    void simpleInternalCallWithImplicitReceiver(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class A {
                    void f() {
                        g();
                    }
                    void g() { }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(1, c.size());
        assertEquals("p.A#f()", c.get(0).callerId());
        assertEquals("p.A#g()", c.get(0).targetId().orElseThrow());
        assertEquals("p.A", c.get(0).staticReceiverType());
        assertEquals(4, c.get(0).sourceLine());
        assertTrue(c.get(0).resolved());
        assertTrue(c.get(0).internal());
        assertTrue(calls(f, "p.A", "g").isEmpty());
    }

    @Test
    void inheritedImplicitSuperAndThisReceivers(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class P { void m() { } }
                class C extends P {
                    void f() {
                        m();
                        super.m();
                        this.m();
                    }
                }
                """);
        List<CallFact> c = calls(f, "p.C", "f");
        assertEquals(3, c.size());
        assertTrue(c.stream().allMatch(x -> x.targetId().orElseThrow().equals("p.P#m()")), c.toString());
        assertEquals(List.of("p.C", "p.P", "p.C"), c.stream().map(CallFact::staticReceiverType).toList());
        assertEquals(List.of(5, 6, 7), c.stream().map(CallFact::sourceLine).toList());
        assertTrue(c.stream().allMatch(CallFact::internal));
    }

    @Test
    void externalCallsHaveJdkOwnersAndErasedTargets(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                import java.util.*;
                class A {
                    int f(String s, List<String> l, ArrayList<String> al) {
                        l.add(s);
                        al.add(s);
                        Math.max(1, 2);
                        return s.length();
                    }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(4, c.size());
        assertEquals("java.util.List", c.get(0).staticReceiverType());
        assertEquals("java.util.List#add(java.lang.Object)", c.get(0).targetId().orElseThrow());
        assertEquals("java.util.ArrayList", c.get(1).staticReceiverType());
        assertEquals("java.util.ArrayList#add(java.lang.Object)", c.get(1).targetId().orElseThrow());
        assertEquals("java.lang.Math", c.get(2).staticReceiverType());
        assertEquals("java.lang.Math#max(int,int)", c.get(2).targetId().orElseThrow());
        assertEquals("java.lang.String", c.get(3).staticReceiverType());
        assertEquals("java.lang.String#length()", c.get(3).targetId().orElseThrow());
        assertTrue(c.stream().noneMatch(CallFact::internal));
        assertTrue(c.stream().allMatch(CallFact::resolved));
    }

    @Test
    void staticCallWithoutQualifierUsesDeclaringClass(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { static void s() { }\n void f() { s(); } }\n");
        CallFact c = calls(f, "p.A", "f").get(0);
        assertEquals("p.A", c.staticReceiverType());
        assertEquals("p.A#s()", c.targetId().orElseThrow());
    }

    @Test
    void staticCallOnAnotherProjectClass(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass U { static int twice(int x) { return 2 * x; } }\nclass A { int f() { return U.twice(2); } }\n");
        CallFact c = calls(f, "p.A", "f").get(0);
        assertEquals("p.U", c.staticReceiverType());
        assertEquals("p.U#twice(int)", c.targetId().orElseThrow());
        assertTrue(c.internal());
    }

    @Test
    void recursionIsASelfCall(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { int r(int n) { return n == 0 ? 0 : r(n - 1); } }\n");
        CallFact c = calls(f, "p.A", "r").get(0);
        assertEquals("p.A#r(int)", c.callerId());
        assertEquals("p.A#r(int)", c.targetId().orElseThrow());
    }

    @Test
    void overloadResolutionPicksTheRightTarget(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class A {
                    void add(int i) { }
                    void add(String s) { }
                    void f() {
                        add("x");
                        add(1);
                    }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(List.of("p.A#add(java.lang.String)", "p.A#add(int)"),
                c.stream().map(x -> x.targetId().orElseThrow()).toList());
    }

    @Test
    void chainedCallsAreAllRecorded(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { String f(String s) { return s.trim().toUpperCase().intern(); } }\n");
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(3, c.size());
        assertTrue(c.stream().allMatch(x -> x.staticReceiverType().equals("java.lang.String")));
        assertEquals(List.of("java.lang.String#intern()", "java.lang.String#toUpperCase()", "java.lang.String#trim()"),
                c.stream().map(x -> x.targetId().orElseThrow()).sorted().toList());
    }

    @Test
    void lineIsTheLineOfTheMethodNameNotOfTheStatement(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = factsOf(tmp, """
                package p;
                import java.util.List;
                class A {
                    int f(List<String> l) {
                        return l
                            .stream()
                            .toList()
                            .size();
                    }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(List.of(6, 7, 8), c.stream().map(CallFact::sourceLine).toList());
    }

    @Test
    void constructorBodyCallsAreAttributedToTheConstructor(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { A() { g(); } void g() { } }\n");
        List<CallFact> c = type(f, "p.A").methods().stream().filter(m -> m.constructor()).findFirst().orElseThrow().calls();
        assertEquals(1, c.size());
        assertEquals("p.A#A()", c.get(0).callerId());
        assertEquals("p.A#g()", c.get(0).targetId().orElseThrow());
    }

    @Test
    void callsOutsideMethodsAreIgnored(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class A {
                    int x = compute();
                    static int y = compute();
                    static { compute(); }
                    { compute(); }
                    static int compute() { return 1; }
                }
                """);
        assertTrue(TestSupport.allCalls(f).isEmpty(), TestSupport.allCalls(f).toString());
    }

    @Test
    void lambdaCallsBelongToTheEnclosingMethod(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { void g() { }\n void f() { Runnable r = () -> g(); r.run(); } }\n");
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(2, c.size());
        assertTrue(c.stream().anyMatch(x -> x.targetId().orElse("").equals("p.A#g()")));
        assertTrue(c.stream().anyMatch(x -> x.targetId().orElse("").equals("java.lang.Runnable#run()")));
    }

    @Test
    void anonymousClassCallsBelongToTheEnclosingMethod(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class A {
                    void g() { }
                    void f() {
                        Runnable r = new Runnable() {
                            public void run() { g(); }
                        };
                        r.run();
                    }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(2, c.size(), c.toString());
        assertEquals(1, type(f, "p.A").methods().stream().filter(m -> m.name().equals("f")).count());
        assertTrue(c.stream().anyMatch(x -> x.targetId().orElse("").equals("p.A#g()") && x.staticReceiverType().equals("p.A")));
        assertTrue(c.stream().anyMatch(x -> x.targetId().orElse("").equals("java.lang.Runnable#run()")));
    }

    @Test
    void localClassCallsBelongToTheEnclosingMethod(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { void g() { }\n void f() { class L { void h() { g(); } } } }\n");
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(1, c.size());
        assertEquals("p.A#f()", c.get(0).callerId());
        assertTrue(c.get(0).resolved());
    }

    /** Regression trouvee par le fuzz : le nom qualifie d'une classe locale est "" (recepteur vide). */
    @Test
    void implicitReceiverInsideALocalClassIsTheEnclosingNamedType(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { void g() { }\n void f() { class L { void h() { g(); } } } }\n");
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(1, c.size());
        assertEquals("p.A", c.get(0).staticReceiverType());
        assertEquals("p.A#g()", c.get(0).targetId().orElseThrow());
    }

    @Test
    void receiverIsNeverBlankEvenForAnAnonymousTypeReceiver(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { void f() { new Runnable() { public void run() { } }.run(); } }\n");
        for (CallFact c : calls(f, "p.A", "f")) {
            assertTrue(!c.staticReceiverType().isBlank(), c.toString());
        }
    }

    @Test
    void innerClassCallingOuterMethod(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass O { void out() { }\n class In { void g() { out(); } } }\n");
        CallFact c = calls(f, "p.O.In", "g").get(0);
        assertEquals("p.O.In#g()", c.callerId());
        assertEquals("p.O#out()", c.targetId().orElseThrow());
        assertTrue(c.internal());
    }

    @Test
    void interfaceDefaultMethodCalls(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\ninterface I { void a();\n default void b() { a(); } }\n");
        CallFact c = calls(f, "p.I", "b").get(0);
        assertEquals("p.I#a()", c.targetId().orElseThrow());
        assertEquals("p.I", c.staticReceiverType());
        assertTrue(calls(f, "p.I", "a").isEmpty());
    }

    @Test
    void enumMethodCalls(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nenum E { X; void f() { g(); } void g() { } }\n");
        CallFact c = calls(f, "p.E", "f").get(0);
        assertEquals("p.E#g()", c.targetId().orElseThrow());
        assertTrue(c.internal());
    }

    @Test
    void genericMethodCallIsErased(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = factsOf(tmp, """
                package p;
                import java.util.*;
                class A {
                    <T> T id(T t) { return t; }
                    String f() { return id("x") + Collections.<String>emptyList(); }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "f");
        assertTrue(c.stream().anyMatch(x -> x.targetId().orElse("").equals("p.A#id(java.lang.Object)")), c.toString());
        assertTrue(c.stream().anyMatch(x -> x.targetId().orElse("").equals("java.util.Collections#emptyList()")), c.toString());
    }

    @Test
    void unresolvedMethodIsReportedNotDropped(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class A {
                    void f() { undefinedMethod(); }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(1, c.size());
        assertFalse(c.get(0).resolved());
        assertFalse(c.get(0).internal());
    }

    /** Attendu rouge tant que 'resolved' ne tient pas compte de isRecovered() (voir le correctif propose). */
    @Test
    void callOnMissingDependencyIsNotResolved(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                import foo.Missing;
                class A {
                    void g(Missing m) { m.go(); }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "g");
        assertEquals(1, c.size());
        assertFalse(c.get(0).resolved(), c.get(0).toString());
        assertFalse(c.get(0).internal(), c.get(0).toString());
    }

    @Test
    void receiverIsNeverNullOrBlank(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { void f() { undefinedMethod(); g(); } void g() { } }\n");
        for (CallFact c : calls(f, "p.A", "f")) {
            assertFalse(c.staticReceiverType() == null || c.staticReceiverType().isBlank());
        }
    }

    @Test
    void methodReferenceIsNotAnInvocation(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { void g() { }\n void f() { Runnable r = this::g; } }\n");
        assertTrue(calls(f, "p.A", "f").isEmpty());
    }

    @Test
    void methodWithoutCallsHasEmptyList(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { void f() { int x = 1; } }\n");
        assertTrue(method(type(f, "p.A"), "f").calls().isEmpty());
    }
}