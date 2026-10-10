package hai913i.tp1.spoon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.compare.SpoonResult;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.model.TypeKind;

/** Bonus C1 : l'extracteur Spoon sur un petit projet ecrit a la volee (chaque convention du TP est testee). */
class SpoonExtractorTest {

    @TempDir
    Path dir;

    private SpoonResult extract(String source) throws IOException {
        Path file = dir.resolve("p").resolve("A.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return SpoonExtractor.extract(dir);
    }

    private static TypeFact type(SpoonResult r, String name) {
        return r.types().stream().filter(t -> t.qualifiedName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError(name + " absent de " + r.types()));
    }

    private static MethodFact method(TypeFact t, String name) {
        return t.methods().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }

    private static final String SOURCE = """
            package p;
            import java.util.List;
            public class A {
                private int x;
                static int util() { return 1; }
                public A() { }
                int f() { return util() + g(); }
                int g() {
                    Runnable r = () -> util();
                    Object o = new Object() { public String toString() { return "" + util(); } };
                    return 2;
                }
                int h(List<String> l, String... more) { return l.size(); }
                public enum Color { RED, GREEN; int code() { return util(); } }
                public static class Nested { void n() { util(); } }
                private final int field = util();
            }
            class B extends A { B() { super(); } }
            """;

    @Test
    void rawCountsKeepWhatSpoonCountsBeforeConventions() throws IOException {
        SpoonResult r = extract(SOURCE);
        assertTrue(r.raw().enumConstants() >= 2);
        assertTrue(r.raw().constructorInvocations() >= 1, "super() est un CtInvocation brut");
        assertTrue(r.raw().invocations() > r.raw().constructorInvocations());
    }

    @Test
    void enumConstantsAreNotFields() throws IOException {
        SpoonResult r = extract(SOURCE);
        TypeFact color = type(r, "p.A.Color");
        assertEquals(TypeKind.ENUM, color.kind());
        assertTrue(color.fields().isEmpty(), "RED et GREEN ne sont pas des attributs du TP");
        assertEquals(1, type(r, "p.A").fields().stream().filter(f -> f.name().equals("x")).count());
    }

    @Test
    void nestedTypesUseDotsAndAreExtractedOnce() throws IOException {
        SpoonResult r = extract(SOURCE);
        assertEquals(1, r.types().stream().filter(t -> t.qualifiedName().equals("p.A.Nested")).count());
        assertTrue(r.types().stream().noneMatch(t -> t.qualifiedName().contains("$")));
    }

    @Test
    void anonymousTypesAreNotCounted() throws IOException {
        SpoonResult r = extract(SOURCE);
        assertEquals(4, r.types().size(), r.types().stream().map(TypeFact::qualifiedName).toList().toString());
    }

    @Test
    void implicitConstructorsAreNotMethods() throws IOException {
        SpoonResult r = extract("package p; public class A { void f() { } }");
        assertEquals(1, type(r, "p.A").methods().size(), "pas de constructeur implicite");
    }

    @Test
    void explicitConstructorsAreMethodsNamedLikeTheirType() throws IOException {
        SpoonResult r = extract(SOURCE);
        MethodFact ctor = method(type(r, "p.A"), "A");
        assertTrue(ctor.constructor());
        assertEquals("p.A#A()", ctor.id());
    }

    @Test
    void parameterTypesAreErasedAndVarargsAreArrays() throws IOException {
        SpoonResult r = extract(SOURCE);
        assertEquals("p.A#h(java.util.List,java.lang.String[])", method(type(r, "p.A"), "h").id());
    }

    @Test
    void superCtorCallsAreNotCalls() throws IOException {
        SpoonResult r = extract(SOURCE);
        assertTrue(type(r, "p.B").methods().get(0).calls().isEmpty());
    }

    @Test
    void callsInLambdasAndAnonymousClassesBelongToTheEnclosingCountedMethod() throws IOException {
        SpoonResult r = extract(SOURCE);
        List<CallFact> calls = method(type(r, "p.A"), "g").calls();
        assertEquals(2, calls.stream().filter(c -> c.targetId().orElse("").equals("p.A#util()")).count(),
                calls.toString());
        assertFalse(calls.isEmpty());
    }

    @Test
    void callsOutsideMethodsAreIgnored() throws IOException {
        SpoonResult r = extract(SOURCE);
        // ligne 16 : private final int field = util(); l'appel n'est dans aucune methode
        long onFieldLine = r.types().stream().flatMap(t -> t.methods().stream()).flatMap(m -> m.calls().stream())
                .filter(c -> c.sourceLine() == 16).count();
        assertEquals(0, onFieldLine);
    }

    @Test
    void internalAndExternalCallsAreTold() throws IOException {
        SpoonResult r = extract(SOURCE);
        CallFact size = method(type(r, "p.A"), "h").calls().get(0);
        assertTrue(size.resolved());
        assertFalse(size.internal());
        assertEquals("java.util.List", size.staticReceiverType());
        CallFact util = method(type(r, "p.A"), "f").calls().get(0);
        assertTrue(util.internal());
        assertEquals("p.A", util.staticReceiverType());
    }

    @Test
    void anUnknownLibraryGivesAnUnresolvedCallInsteadOfAnException() throws IOException {
        SpoonResult r = extract("""
                package p;
                import org.apache.commons.lang3.StringUtils;
                public class A { String f(String s) { return StringUtils.capitalize(s); } }
                """);
        CallFact call = method(type(r, "p.A"), "f").calls().get(0);
        assertFalse(call.internal());
    }
}
