package hai913i.tp1.extract;

import static hai913i.tp1.TestSupport.factsOf;
import static hai913i.tp1.TestSupport.method;
import static hai913i.tp1.TestSupport.type;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.TestSupport;
import hai913i.tp1.model.FieldFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.model.TypeKind;
import hai913i.tp1.model.Visibility;

/** A2 : cas limites sur de petites sources (conventions du sujet, section 4). */
class StructureVisitorEdgeCasesTest {

    @Test
    void oneAttributePerVariableAndVisibilities(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class A {
                    int a, b;
                    private static final String X = "x", Y = "y";
                    protected long z;
                    public int w;
                }
                """);
        TypeFact a = type(f, "p.A");
        assertEquals(List.of("X", "Y", "a", "b", "w", "z"), a.fields().stream().map(FieldFact::name).toList());
        Map<String, FieldFact> by = a.fields().stream().collect(Collectors.toMap(FieldFact::name, x -> x));
        assertEquals(Visibility.PACKAGE, by.get("a").visibility());
        assertEquals(Visibility.PACKAGE, by.get("b").visibility());
        assertEquals(Visibility.PRIVATE, by.get("X").visibility());
        assertEquals(Visibility.PRIVATE, by.get("Y").visibility());
        assertEquals(Visibility.PROTECTED, by.get("z").visibility());
        assertEquals(Visibility.PUBLIC, by.get("w").visibility());
        assertEquals("int", by.get("a").declaredType());
        assertEquals("String", by.get("X").declaredType());
        assertEquals("long", by.get("z").declaredType());
    }

    @Test
    void enumConstantsAreNotAttributes(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                enum E {
                    A, B;
                    private int v;
                    E() { }
                    int get() { return v; }
                }
                """);
        TypeFact e = type(f, "p.E");
        assertEquals(TypeKind.ENUM, e.kind());
        assertEquals(List.of("v"), e.fields().stream().map(FieldFact::name).toList());
        assertEquals(2, e.methods().size());
        assertTrue(method(e, "E").constructor());
        assertFalse(method(e, "get").constructor());
    }

    @Test
    void enumWithoutMembersHasNoImplicitMethods(@TempDir Path tmp) throws IOException {
        TypeFact e = type(factsOf(tmp, "package p;\nenum E { A, B, C }\n"), "p.E");
        assertTrue(e.methods().isEmpty(), "values()/valueOf() implicites ne sont pas comptes");
        assertTrue(e.fields().isEmpty());
    }

    @Test
    void noImplicitDefaultConstructor(@TempDir Path tmp) throws IOException {
        TypeFact a = type(factsOf(tmp, "package p;\nclass A { }\n"), "p.A");
        assertTrue(a.methods().isEmpty());
        assertTrue(a.fields().isEmpty());
    }

    @Test
    void explicitConstructorsAreCounted(@TempDir Path tmp) throws IOException {
        TypeFact a = type(factsOf(tmp, "package p;\nclass A { A() { }\n A(int x) { } }\n"), "p.A");
        assertEquals(2, a.methods().size());
        assertTrue(a.methods().stream().allMatch(MethodFact::constructor));
        assertEquals(Set.of("p.A#A()", "p.A#A(int)"), a.methods().stream().map(MethodFact::id).collect(Collectors.toSet()));
    }

    @Test
    void interfaceMembers(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = factsOf(tmp, """
                package p;
                interface I extends J, K {
                    int CONST = 1;
                    void a();
                    default void b() { }
                    static void c() { }
                }
                interface J { }
                interface K { }
                """);
        TypeFact i = type(f, "p.I");
        assertEquals(TypeKind.INTERFACE, i.kind());
        assertEquals(List.of("p.J", "p.K"), i.interfaces());
        assertTrue(i.superclasses().isEmpty());
        assertEquals(3, i.methods().size());
        assertEquals(0, method(i, "a").bodyLoc());
        assertEquals(1, method(i, "b").bodyLoc());
        assertEquals(1, method(i, "c").bodyLoc());
        assertEquals(1, i.fields().size());
    }

    @Test
    void inheritanceChainSuperclassesNearestFirstAndDirectInterfaces(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                interface I { }
                interface J { }
                class A { }
                class B extends A { }
                class C extends B implements I, J { }
                """);
        assertTrue(type(f, "p.A").superclasses().isEmpty());
        assertEquals(List.of("p.A"), type(f, "p.B").superclasses());
        assertEquals(List.of("p.B", "p.A"), type(f, "p.C").superclasses());
        assertEquals(List.of("p.I", "p.J"), type(f, "p.C").interfaces());
        assertTrue(type(f, "p.B").interfaces().isEmpty());
    }

    /** Les supertypes parametres doivent etre exprimes sous forme erasee (sinon "java.util.ArrayList<java.lang.String>"). */
    @Test
    void genericSupertypesAreErased(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class D extends java.util.ArrayList<String> implements Comparable<D> {
                    public int compareTo(D o) { return 0; }
                }
                """);
        TypeFact d = type(f, "p.D");
        assertEquals(List.of("java.util.ArrayList", "java.util.AbstractList", "java.util.AbstractCollection"),
                d.superclasses());
        assertEquals(List.of("java.lang.Comparable"), d.interfaces());
    }

    @Test
    void multipleTopLevelTypesInOneFile(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { void a() { } }\nclass B { void b() { } }\ninterface C { }\n");
        assertEquals(3, f.types().size());
        assertEquals(1, type(f, "p.A").methods().size());
        assertEquals(1, type(f, "p.B").methods().size());
    }

    @Test
    void nestedMemberTypesAreCountedAndIsolated(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class O {
                    int x;
                    void a() { }
                    static class I {
                        int y;
                        void b() { }
                        void c() { }
                    }
                    class Inner { }
                    interface Face { }
                    enum En { ONE }
                }
                """);
        assertEquals(5, f.types().size(), f.types().stream().map(TypeFact::qualifiedName).toList().toString());
        TypeFact o = type(f, "p.O");
        assertEquals(1, o.fields().size());
        assertEquals(List.of("p.O#a()"), o.methods().stream().map(MethodFact::id).toList());
        TypeFact i = type(f, "p.O.I");
        assertEquals(1, i.fields().size());
        assertEquals(2, i.methods().size());
        assertEquals("p", i.packageName());
        assertEquals(TypeKind.CLASS, type(f, "p.O.Inner").kind());
        assertEquals(TypeKind.INTERFACE, type(f, "p.O.Face").kind());
        assertEquals(TypeKind.ENUM, type(f, "p.O.En").kind());
    }

    @Test
    void deeplyNestedTypes(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\nclass A { class B { class C { void m() { } } } }\n");
        assertEquals(3, f.types().size());
        assertEquals("p.A.B.C#m()", type(f, "p.A.B.C").methods().get(0).id());
    }

    @Test
    void anonymousLocalAndAnnotationTypesAreNotCounted(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                @interface Ann { int v(); }
                class A {
                    Runnable field = new Runnable() { public void run() { } };
                    Runnable r() { return new Runnable() { public void run() { } }; }
                    void local() { class L { void g() { } } }
                }
                """);
        assertEquals(1, f.types().size(), f.types().stream().map(TypeFact::qualifiedName).toList().toString());
        TypeFact a = type(f, "p.A");
        assertEquals(Set.of("p.A#r()", "p.A#local()"),
                a.methods().stream().map(MethodFact::id).collect(Collectors.toSet()));
        assertEquals(1, a.fields().size());
    }

    @Test
    void initializerBlocksAreNotMethods(@TempDir Path tmp) throws IOException {
        TypeFact a = type(factsOf(tmp, "package p;\nclass A { static { } { } int f; }\n"), "p.A");
        assertTrue(a.methods().isEmpty());
        assertEquals(1, a.fields().size());
    }

    @Test
    void bodyLocCountsPhysicalLinesFromBraceToBrace(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = factsOf(tmp, """
                package p;
                abstract class A {
                    A() { }
                    abstract void abs();
                    void one() { }
                    void three() {
                        int x = 1;
                    }
                    void brace()
                    {
                        // commentaire

                    }
                }
                """);
        TypeFact a = type(f, "p.A");
        assertEquals(0, method(a, "abs").bodyLoc());
        assertEquals(1, method(a, "one").bodyLoc());
        assertEquals(3, method(a, "three").bodyLoc());
        assertEquals(4, method(a, "brace").bodyLoc(), "accolade ouvrante seule sur sa ligne, commentaire et ligne vide inclus");
        assertEquals(1, method(a, "A").bodyLoc());
    }

    @Test
    void bodyLocIsTheSameWithWindowsLineEndings(@TempDir Path tmp) throws IOException {
        String src = "package p;\nclass A {\n    void m() {\n        int x;\n        int y;\n    }\n}\n";
        TypeFact unix = type(factsOf(tmp.resolve("u"), src), "p.A");
        TypeFact win = type(factsOf(tmp.resolve("w"), src.replace("\n", "\r\n")), "p.A");
        assertEquals(4, method(unix, "m").bodyLoc());
        assertEquals(4, method(win, "m").bodyLoc());
    }

    @Test
    void genericsAreErasedInIdsButKeptInFieldTypes(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = factsOf(tmp, """
                package p;
                import java.util.List;
                class Box<T extends Comparable<T>> {
                    T value;
                    List<String> names;
                    void put(T t) { }
                    <U> U map(List<U> in, int[] arr, String... rest) { return null; }
                }
                """);
        TypeFact box = f.types().get(0);
        assertEquals("p.Box", box.qualifiedName(), "le nom qualifie d'un type generique ne doit pas contenir <T>");
        assertEquals("T", box.fields().stream().filter(x -> x.name().equals("value")).findFirst().orElseThrow().declaredType());
        assertEquals("List<String>", box.fields().stream().filter(x -> x.name().equals("names")).findFirst().orElseThrow().declaredType());
        assertEquals("p.Box#put(java.lang.Comparable)", method(box, "put").id());
        MethodFact map = method(box, "map");
        assertEquals("p.Box#map(java.util.List,int[],java.lang.String[])", map.id());
        assertEquals(List.of("java.util.List", "int[]", "java.lang.String[]"), map.parameterTypes());
    }

    @Test
    void methodIdOwnerMatchesTypeQualifiedNameEverywhere(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                class Box<T> { void put(T t) { } class In { void x() { } } }
                enum E { A; void e() { } }
                interface I { void i(); }
                """);
        for (TypeFact t : f.types()) {
            for (MethodFact m : t.methods()) {
                assertTrue(m.id().startsWith(t.qualifiedName() + "#"), m.id() + " vs " + t.qualifiedName());
            }
        }
    }

    @Test
    void overloadsHaveDistinctIds(@TempDir Path tmp) throws IOException {
        TypeFact a = type(factsOf(tmp, "package p;\nclass A { void add(int i) { } void add(String s) { } void add() { } }\n"), "p.A");
        assertEquals(Set.of("p.A#add(int)", "p.A#add(java.lang.String)", "p.A#add()"),
                a.methods().stream().map(MethodFact::id).collect(Collectors.toSet()));
        assertEquals(3, a.methods().stream().map(MethodFact::id).distinct().count());
    }

    @Test
    void defaultPackageDoesNotCrash(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "Z.java", "class Z { void f() { } }\n");
        assertEquals(1, f.types().size());
        assertNotNull(f.types().get(0).packageName());
        assertEquals("Z", f.types().get(0).qualifiedName());
    }

    /** Attendu rouge tant que StructureVisitor ne gere pas RecordDeclaration (voir le correctif propose). */
    @Test
    void recordsAreTypesOfKindRecord(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, """
                package p;
                record R(int x, String y) {
                    int sum() { return x; }
                    static R of() { return new R(1, "a"); }
                }
                class Holder { record N(int a) { } }
                """);
        assertEquals(3, f.types().size(), f.types().stream().map(TypeFact::qualifiedName).toList().toString());
        assertEquals(TypeKind.RECORD, type(f, "p.R").kind());
        assertEquals(TypeKind.RECORD, type(f, "p.Holder.N").kind());
        method(type(f, "p.R"), "sum");
        method(type(f, "p.R"), "of");
    }

    @Test
    void emptyFileProducesNoType(@TempDir Path tmp) throws IOException {
        ProjectFacts f = factsOf(tmp, "package p;\n");
        assertTrue(f.types().isEmpty());
    }

    @Test
    void resultIsIndependentFromSourceFileOrder(@TempDir Path tmp)throws IOException  {
        TestSupport.write(tmp, "p/B.java", "package p;\nclass B { void b() { } }\n");
        TestSupport.write(tmp, "p/A.java", "package p;\nclass A { void a() { } }\n");
        ProjectFacts f = TestSupport.facts(tmp);
        assertEquals(List.of("p.A", "p.B"), f.types().stream().map(TypeFact::qualifiedName).toList());
    }
}
