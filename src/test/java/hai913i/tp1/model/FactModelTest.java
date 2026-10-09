package hai913i.tp1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** B1 : le modele de faits (aucun JDT) : immutabilite, tri, egalite par valeur. */
class FactModelTest {

    private static CallFact call(String caller, String target, String recv, int line) {
        return new CallFact(caller, Optional.ofNullable(target), recv, line, target != null, false);
    }

    private static MethodFact method(String id, String name, CallFact... calls) {
        return new MethodFact(id, name, List.of(), false, 1, List.of(calls));
    }

    private static TypeFact type(String qn, List<FieldFact> fields, List<MethodFact> methods) {
        return new TypeFact(qn, "p", TypeKind.CLASS, List.of(), List.of(), fields, methods);
    }

    // ---------- enums ----------

    @Test
    void enumsHaveExpectedConstants() {
        assertEquals(Set.of("CLASS", "INTERFACE", "ENUM", "RECORD"),
                Set.of(TypeKind.values()).stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("PUBLIC", "PRIVATE", "PROTECTED", "PACKAGE"),
                Set.of(Visibility.values()).stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
    }

    // ---------- TypeFact ----------

    @Test
    void typeFactSortsFieldsByNameAndMethodsById() {
        TypeFact t = type("p.A",
                List.of(new FieldFact("z", "int", Visibility.PUBLIC), new FieldFact("a", "int", Visibility.PRIVATE),
                        new FieldFact("m", "int", Visibility.PACKAGE)),
                List.of(method("p.A#z()", "z"), method("p.A#a()", "a"), method("p.A#m()", "m")));
        assertEquals(List.of("a", "m", "z"), t.fields().stream().map(FieldFact::name).toList());
        assertEquals(List.of("p.A#a()", "p.A#m()", "p.A#z()"), t.methods().stream().map(MethodFact::id).toList());
    }

    @Test
    void typeFactEqualityIgnoresInputOrder() {
        List<MethodFact> ms = List.of(method("p.A#b()", "b"), method("p.A#a()", "a"));
        List<MethodFact> reversed = List.of(ms.get(1), ms.get(0));
        assertEquals(type("p.A", List.of(), ms), type("p.A", List.of(), reversed));
        assertEquals(type("p.A", List.of(), ms).hashCode(), type("p.A", List.of(), reversed).hashCode());
    }

    @Test
    void typeFactKeepsSuperclassOrderNearestFirst() {
        TypeFact t = new TypeFact("p.C", "p", TypeKind.CLASS, List.of("p.B", "p.A"), List.of("p.J", "p.I"),
                List.of(), List.of());
        assertEquals(List.of("p.B", "p.A"), t.superclasses(), "l'ordre de la chaine d'heritage est significatif");
        assertEquals(List.of("p.J", "p.I"), t.interfaces());
    }

    @Test
    void typeFactListsAreDefensivelyCopied() {
        List<String> supers = new ArrayList<>(List.of("p.B"));
        List<FieldFact> fields = new ArrayList<>(List.of(new FieldFact("a", "int", Visibility.PUBLIC)));
        List<MethodFact> methods = new ArrayList<>(List.of(method("p.A#a()", "a")));
        TypeFact t = new TypeFact("p.A", "p", TypeKind.CLASS, supers, List.of(), fields, methods);
        supers.add("p.X");
        fields.clear();
        methods.clear();
        assertEquals(List.of("p.B"), t.superclasses());
        assertEquals(1, t.fields().size());
        assertEquals(1, t.methods().size());
    }

    @Test
    void typeFactListsAreImmutable() {
        TypeFact t = type("p.A", List.of(new FieldFact("a", "int", Visibility.PUBLIC)), List.of(method("p.A#a()", "a")));
        assertThrows(UnsupportedOperationException.class, () -> t.fields().add(new FieldFact("b", "int", Visibility.PUBLIC)));
        assertThrows(UnsupportedOperationException.class, () -> t.methods().clear());
        assertThrows(UnsupportedOperationException.class, () -> t.superclasses().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> t.interfaces().add("x"));
    }

    @Test
    void typeFactRejectsNullLists() {
        assertThrows(NullPointerException.class,
                () -> new TypeFact("p.A", "p", TypeKind.CLASS, null, List.of(), List.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> new TypeFact("p.A", "p", TypeKind.CLASS, List.of(), null, List.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> new TypeFact("p.A", "p", TypeKind.CLASS, List.of(), List.of(), null, List.of()));
        assertThrows(NullPointerException.class,
                () -> new TypeFact("p.A", "p", TypeKind.CLASS, List.of(), List.of(), List.of(), null));
    }

    @Test
    void typeFactAccessorsReturnConstructorValues() {
        TypeFact t = new TypeFact("p.A", "p", TypeKind.ENUM, List.of(), List.of("p.I"), List.of(), List.of());
        assertEquals("p.A", t.qualifiedName());
        assertEquals("p", t.packageName());
        assertEquals(TypeKind.ENUM, t.kind());
    }

    // ---------- MethodFact ----------

    @Test
    void methodFactSortsCallsByLineThenTargetThenReceiver() {
        CallFact l9 = call("m", "t", "r", 9);
        CallFact l3b = call("m", "b", "r", 3);
        CallFact l3a = call("m", "a", "r", 3);
        CallFact l3aZ = call("m", "a", "z", 3);
        MethodFact m = new MethodFact("m", "m", List.of(), false, 5, List.of(l9, l3b, l3aZ, l3a));
        assertEquals(List.of(l3a, l3aZ, l3b, l9), m.calls());
    }

    @Test
    void methodFactSortsUnresolvedCallsFirstOnTheSameLine() {
        CallFact unresolved = call("m", null, "r", 4);
        CallFact resolved = call("m", "a", "r", 4);
        MethodFact m = new MethodFact("m", "m", List.of(), false, 5, List.of(resolved, unresolved));
        assertEquals(List.of(unresolved, resolved), m.calls());
    }

    @Test
    void methodFactCopiesAndFreezesLists() {
        List<String> params = new ArrayList<>(List.of("int"));
        List<CallFact> calls = new ArrayList<>(List.of(call("m", "t", "r", 1)));
        MethodFact m = new MethodFact("m(int)", "m", params, false, 3, calls);
        params.add("long");
        calls.clear();
        assertEquals(List.of("int"), m.parameterTypes());
        assertEquals(1, m.calls().size());
        assertThrows(UnsupportedOperationException.class, () -> m.parameterTypes().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> m.calls().clear());
    }

    @Test
    void methodFactKeepsParameterOrder() {
        MethodFact m = new MethodFact("m", "m", List.of("z", "a"), false, 0, List.of());
        assertEquals(List.of("z", "a"), m.parameterTypes(), "l'ordre des parametres est significatif");
    }

    @Test
    void methodFactRejectsNullLists() {
        assertThrows(NullPointerException.class, () -> new MethodFact("m", "m", null, false, 0, List.of()));
        assertThrows(NullPointerException.class, () -> new MethodFact("m", "m", List.of(), false, 0, null));
    }

    // ---------- CallFact / FieldFact ----------

    @Test
    void callFactHasValueSemantics() {
        CallFact a = new CallFact("c", Optional.of("t"), "r", 3, true, true);
        CallFact b = new CallFact("c", Optional.of("t"), "r", 3, true, true);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new CallFact("c", Optional.of("t"), "r", 4, true, true));
        assertNotEquals(a, new CallFact("c", Optional.empty(), "r", 3, true, true));
        assertNotEquals(a, new CallFact("c", Optional.of("t"), "r", 3, false, true));
        assertNotEquals(a, new CallFact("c", Optional.of("t"), "r", 3, true, false));
    }

    @Test
    void fieldFactHasValueSemantics() {
        assertEquals(new FieldFact("a", "int", Visibility.PUBLIC), new FieldFact("a", "int", Visibility.PUBLIC));
        assertNotEquals(new FieldFact("a", "int", Visibility.PUBLIC), new FieldFact("a", "int", Visibility.PRIVATE));
    }

    // ---------- ProjectFacts ----------

    @Test
    void projectFactsSortsTypesAndFiles() {
        TypeFact b = type("p.B", List.of(), List.of());
        TypeFact a = type("p.A", List.of(), List.of());
        ProjectFacts f = new ProjectFacts(List.of(b, a), List.of(Path.of("z/B.java"), Path.of("a/A.java")), 10);
        assertEquals(List.of(a, b), f.types());
        assertEquals(List.of(Path.of("a/A.java"), Path.of("z/B.java")), f.sourceFiles());
        assertEquals(10, f.totalLines());
    }

    @Test
    void projectFactsAreImmutableAndCopied() {
        List<TypeFact> types = new ArrayList<>(List.of(type("p.A", List.of(), List.of())));
        List<Path> files = new ArrayList<>(List.of(Path.of("A.java")));
        ProjectFacts f = new ProjectFacts(types, files, 1);
        types.clear();
        files.clear();
        assertEquals(1, f.types().size());
        assertEquals(1, f.sourceFiles().size());
        assertThrows(UnsupportedOperationException.class, () -> f.types().clear());
        assertThrows(UnsupportedOperationException.class, () -> f.sourceFiles().clear());
    }

    @Test
    void projectFactsEqualityIgnoresInputOrder() {
        TypeFact a = type("p.A", List.of(), List.of());
        TypeFact b = type("p.B", List.of(), List.of());
        assertEquals(new ProjectFacts(List.of(a, b), List.of(), 3), new ProjectFacts(List.of(b, a), List.of(), 3));
        assertNotEquals(new ProjectFacts(List.of(a, b), List.of(), 3), new ProjectFacts(List.of(a, b), List.of(), 4));
    }

    @Test
    void emptyProjectFactsAreValid() {
        ProjectFacts f = new ProjectFacts(List.of(), List.of(), 0);
        assertTrue(f.types().isEmpty());
        assertTrue(f.sourceFiles().isEmpty());
        assertEquals(0, f.totalLines());
    }

    @Test
    void projectFactsRejectNullLists() {
        assertThrows(NullPointerException.class, () -> new ProjectFacts(null, List.of(), 0));
        assertThrows(NullPointerException.class, () -> new ProjectFacts(List.of(), null, 0));
    }
}
