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
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.model.TypeKind;

/**
 * Tests ecrits pour tuer les mutants survivants du premier rapport PIT.
 * Chaque test dit quel mutant il vise (classe, ligne, mutation).
 */
class MutationSurvivorsTest {

    // ---- CallVisitor.receiverName, ligne 194 (conditionals boundary + negate) et 197 ----

    /** Recepteur de type anonyme qui implemente une interface : le recepteur est l'interface. 
     * @throws IOException */
    @Test
    void anonymousTypeImplementingAnInterfaceHasTheInterfaceAsReceiver(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class A {
                    void f() {
                        new Runnable() { public void run() { } }.run();
                    }
                }
                """);
        List<CallFact> c = calls(f, "p.A", "f");
        assertEquals(1, c.size(), c.toString());
        assertEquals("java.lang.Runnable", c.get(0).staticReceiverType());
        assertFalse(c.get(0).resolved(), "methode declaree dans une classe anonyme : non resolue par convention");
        assertTrue(c.get(0).targetId().isEmpty());
    }

    /** Recepteur de type anonyme qui etend une classe : le recepteur est cette classe (pas "" ni "?"). */
    @Test
    void anonymousTypeExtendingAClassHasTheSuperclassAsReceiver(@TempDir Path tmp) throws IOException{
        ProjectFacts f = factsOf(tmp, """
                package p;
                class B { }
                class A {
                    void f() {
                        new B() { void hi() { } }.hi();
                    }
                    void g() {
                        new Object() { void hi() { } }.hi();
                    }
                }
                """);
        assertEquals("p.B", calls(f, "p.A", "f").get(0).staticReceiverType());
        assertEquals("java.lang.Object", calls(f, "p.A", "g").get(0).staticReceiverType());
    }

    // ---- CallVisitor.visit(SuperMethodInvocation), ligne 164 : "return true" ----

    /** Les appels imbriques dans les arguments de super.m(...) doivent etre comptes. */
    @Test
    void callsInsideSuperCallArgumentsAreCounted(@TempDir Path tmp) throws IOException{
        ProjectFacts f = factsOf(tmp, """
                package p;
                class P {
                    void m(int x) { }
                    int h() { return 1; }
                }
                class C extends P {
                    void f() {
                        super.m(h());
                    }
                }
                """);
        List<CallFact> c = calls(f, "p.C", "f");
        assertEquals(2, c.size(), c.toString());
        Set<String> targets = c.stream().map(x -> x.targetId().orElse("")).collect(Collectors.toSet());
        assertEquals(Set.of("p.P#m(int)", "p.P#h()"), targets);
    }

    // ---- StructureVisitor.bodyLines, ligne 129 : "first < 1" ----

    /** Une classe ecrite sur UNE seule ligne : la methode commence a la ligne 1 et son corps compte 1 ligne. */
    @Test
    void methodOnLineOneHasOneLineBody(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p; class A { void f() { } int g() { return 1; } }");
        TypeFact a = type(f, "p.A");
        assertEquals(1, method(a, "f").bodyLoc());
        assertEquals(1, method(a, "g").bodyLoc());
    }

    // ---- StructureVisitor.visit(EnumDeclaration) ligne 52 et visit(RecordDeclaration) ligne 58 ----

    /** Un type imbrique dans un enum est extrait UNE seule fois (pas de descente en plus de extractType). */
    @Test
    void typeNestedInAnEnumIsExtractedOnce(@TempDir Path tmp) throws IOException{
        ProjectFacts f = factsOf(tmp, """
                package p;
                enum E {
                    X;
                    static class N { void n() { } }
                }
                """);
        assertEquals(2, f.types().size(), f.types().stream().map(TypeFact::qualifiedName).toList().toString());
        assertEquals(TypeKind.ENUM, type(f, "p.E").kind());
        assertEquals(TypeKind.CLASS, type(f, "p.E.N").kind());
    }

    /** Idem pour un record. */
    @Test
    void typeNestedInARecordIsExtractedOnce(@TempDir Path tmp) throws IOException{
        ProjectFacts f = factsOf(tmp, """
                package p;
                record R(int a) {
                    static class N { void n() { } }
                }
                """);
        assertEquals(2, f.types().size(), f.types().stream().map(TypeFact::qualifiedName).toList().toString());
        assertEquals(TypeKind.RECORD, type(f, "p.R").kind());
        assertEquals(TypeKind.CLASS, type(f, "p.R.N").kind());
    }
}
