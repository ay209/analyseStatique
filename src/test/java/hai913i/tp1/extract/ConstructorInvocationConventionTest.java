package hai913i.tp1.extract;

import static hai913i.tp1.TestSupport.calls;
import static hai913i.tp1.TestSupport.factsOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.TestSupport;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.ProjectFacts;

/**
 * CONVENTION ACTUELLE (a revoir en B3) : seules les invocations de METHODES sont des appels.
 * 'new A()', 'this(...)' et 'super(...)' n'en sont pas. Si on choisit d'ajouter les arcs de constructeurs
 * (52 arcs / 56 sites au lieu de 44 / 46), c'est CETTE classe, et elle seule, qu'il faudra reecrire.
 */
class ConstructorInvocationConventionTest {

    @Test
    void creationAndExplicitConstructorInvocationAreNotCalls(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp,
                "package p;\nclass A { A() { this(1); } A(int x) { super(); } void f() { new A(); } }\n");
        assertTrue(TestSupport.allCalls(f).isEmpty(), TestSupport.allCalls(f).toString());
    }

    @Test
    void superConstructorWithArgumentsIsNotACall(@TempDir Path tmp)throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class P { P(int x) { } }
                class C extends P { C() { super(1); } }
                """);
        assertTrue(TestSupport.allCalls(f).isEmpty());
    }

    @Test
    void methodCallsInsideCreationArgumentsAreStillCounted(@TempDir Path tmp) throws IOException{
        ProjectFacts f = factsOf(tmp, """
                package p;
                class A {
                    A(int x) { }
                    static int g() { return 1; }
                    void f() {
                        new A(g());
                    }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(1, c.size());
        assertEquals("p.A#g()", c.get(0).targetId().orElseThrow());
    }

    @Test
    void methodCallOnANewObjectIsCounted(@TempDir Path tmp) throws IOException{
        ProjectFacts f = factsOf(tmp, """
                package p;
                class A {
                    void m() { }
                    void f() {
                        new A().m();
                    }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(1, c.size());
        assertEquals("p.A#m()", c.get(0).targetId().orElseThrow());
        assertEquals("p.A", c.get(0).staticReceiverType());
        assertTrue(c.get(0).internal());
    }

    @Test
    void creationOfJdkObjectIsNotACallButItsMethodsAre(@TempDir Path tmp)throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class A {
                    int f() {
                        StringBuilder sb = new StringBuilder("x");
                        return sb.append("y").length();
                    }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(2, c.size(), c.toString());
        assertTrue(c.stream().allMatch(x -> x.targetId().orElse("").startsWith("java.lang.")), c.toString());
    }
}
