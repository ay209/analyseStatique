package hai913i.tp1.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.TestSupport;
import hai913i.tp1.parse.JdtParser.ParsedFile;

/** A0 : configuration de l'ASTParser (niveau Java, bindings, recovery, environnement). */
class JdtParserTest {

    private static long errors(CompilationUnit u) {
        return Arrays.stream(u.getProblems()).filter(p -> p.isError()).count();
    }

    @Test
    void validationParses14UnitsWithoutErrors() throws IOException {
        List<ParsedFile> files = TestSupport.validationParsed();
        assertEquals(14, files.size());
        for (ParsedFile f : files) {
            assertNotNull(f.unit(), f.path().toString());
            assertEquals(0, errors(f.unit()), "erreurs de compilation dans " + f.path());
        }
    }

    @Test
    void filesAreReturnedInSortedPathOrder() throws IOException {
        List<Path> paths = TestSupport.validationParsed().stream().map(ParsedFile::path).toList();
        assertEquals(paths.stream().sorted().toList(), paths);
    }

    @Test
    void everyUnitHasItsPackageAndAType() throws IOException {
        for (ParsedFile f : TestSupport.validationParsed()) {
            assertNotNull(f.unit().getPackage(), f.path() + " : pas de package");
            assertFalse(f.unit().types().isEmpty(), f.path() + " : aucun type");
        }
    }

    /** Tous les appels du projet de validation doivent etre resolus : 93 MethodInvocation + 1 super.xxx(). */
    @Test
    void allInvocationsHaveBindings()throws IOException  {
        AtomicInteger invocations = new AtomicInteger();
        AtomicInteger supers = new AtomicInteger();
        AtomicInteger unresolved = new AtomicInteger();
        for (ParsedFile f : TestSupport.validationParsed()) {
            f.unit().accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation node) {
                    invocations.incrementAndGet();
                    if (node.resolveMethodBinding() == null) {
                        unresolved.incrementAndGet();
                    }
                    return true;
                }

                @Override
                public boolean visit(SuperMethodInvocation node) {
                    supers.incrementAndGet();
                    if (node.resolveMethodBinding() == null) {
                        unresolved.incrementAndGet();
                    }
                    return true;
                }
            });
        }
        assertEquals(93, invocations.get());
        assertEquals(1, supers.get());
        assertEquals(0, unresolved.get());
    }

    /** Les bindings traversent les fichiers : Book (un fichier) etend Item (un autre fichier). */
    @Test
    void bindingsResolveAcrossFiles() throws IOException {
        CompilationUnit book = TestSupport.validationUnit("Book.java");
        TypeDeclaration decl = (TypeDeclaration) book.types().get(0);
        ITypeBinding sup = decl.resolveBinding().getSuperclass();
        assertNotNull(sup);
        assertEquals("library.model.Item", sup.getQualifiedName());
        assertTrue(sup.isFromSource(), "Item vient des sources du projet");
    }

    @Test
    void jdkTypesAreResolvedButNotFromSource()throws IOException  {
        CompilationUnit ex = TestSupport.validationUnit("LoanException.java");
        TypeDeclaration decl = (TypeDeclaration) ex.types().get(0);
        ITypeBinding sup = decl.resolveBinding().getSuperclass();
        assertEquals("java.lang.Exception", sup.getQualifiedName());
        assertFalse(sup.isFromSource());
    }

    @Test
    void methodBindingsCarryTheDeclaringClass() throws IOException {
        CompilationUnit app = TestSupport.validationUnit("LibraryApp.java");
        AtomicInteger checked = new AtomicInteger();
        app.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                IMethodBinding b = node.resolveMethodBinding();
                assertNotNull(b.getDeclaringClass());
                checked.incrementAndGet();
                return true;
            }
        });
        assertTrue(checked.get() > 0);
    }

    @Test
    void java17FeaturesParseWithoutError() {
        CompilationUnit u = TestSupport.parseString("F.java", """
                package p;
                record R(int a) { }
                class F {
                    String text = \"\"\"
                        bloc
                        \"\"\";
                    int f(Object o) {
                        var x = 1;
                        if (o instanceof String s) { return s.length(); }
                        return switch (x) { case 1 -> 10; default -> 0; };
                    }
                }
                """);
        assertEquals(0, errors(u), Arrays.toString(u.getProblems()));
        AtomicInteger records = new AtomicInteger();
        u.accept(new ASTVisitor() {
            @Override
            public boolean visit(RecordDeclaration node) {
                records.incrementAndGet();
                return true;
            }
        });
        assertEquals(1, records.get());
    }

    /** Le niveau de langage est bien 17 : une construction Java 21 doit etre signalee. */
    @Test
    void java21PatternSwitchIsRejectedAtLevel17() {
        CompilationUnit u = TestSupport.parseString("G.java", """
                package p;
                class G {
                    String f(Object o) {
                        return switch (o) { case Integer i -> "int"; default -> "autre"; };
                    }
                }
                """);
        assertTrue(errors(u) > 0, "le niveau de conformite devrait etre 17");
    }

    @Test
    void syntaxErrorStillYieldsATreeAndAProblem(@TempDir Path tmp)throws IOException  {
        TestSupport.write(tmp, "p/Bad.java", "package p;\nclass Bad {\n  void f( {\n  }\n}\n");
        TestSupport.write(tmp, "p/Good.java", "package p;\nclass Good { void g() { } }\n");
        List<ParsedFile> files = TestSupport.parse(tmp);
        assertEquals(2, files.size());
        long withErrors = files.stream().filter(f -> errors(f.unit()) > 0).count();
        assertEquals(1, withErrors);
        for (ParsedFile f : files) {
            assertFalse(f.unit().types().isEmpty(), f.path() + " doit avoir un arbre (recovery)");
        }
    }

    @Test
    void missingDependencyDoesNotCrashAndKeepsOwnBindings(@TempDir Path tmp)throws IOException  {
        TestSupport.write(tmp, "p/A.java", """
                package p;
                import foo.Missing;
                class A {
                    Missing m;
                    void f() { m.go(); }
                }
                """);
        List<ParsedFile> files = TestSupport.parse(tmp);
        assertEquals(1, files.size());
        CompilationUnit u = files.get(0).unit();
        assertTrue(errors(u) > 0);
        TypeDeclaration a = (TypeDeclaration) u.types().get(0);
        assertEquals("p.A", a.resolveBinding().getQualifiedName());
    }

    @Test
    void emptyProjectParsesToNothing(@TempDir Path tmp)throws IOException  {
        assertTrue(TestSupport.parse(tmp).isEmpty());
    }

    @Test
    void sourceRootMattersForCrossFileResolution(@TempDir Path tmp) throws IOException {
        TestSupport.write(tmp, "src/main/java/p/A.java", "package p; class A extends B { }");
        TestSupport.write(tmp, "src/main/java/p/B.java", "package p; class B { }");
        List<ParsedFile> files = TestSupport.parse(tmp);
        assertEquals(2, files.size());
        for (ParsedFile f : files) {
            if (f.path().endsWith("A.java")) {
                TypeDeclaration a = (TypeDeclaration) f.unit().types().get(0);
                assertEquals("p.B", a.resolveBinding().getSuperclass().getQualifiedName());
            }
        }
    }

    @Test
    void utf8AndAccentsAreReadCorrectly(@TempDir Path tmp)throws IOException  {
        TestSupport.write(tmp, "p/A.java", "package p;\n// é à ü — commentaire\nclass A { String s = \"été\"; }\n");
        List<ParsedFile> files = TestSupport.parse(tmp);
        assertEquals(0, errors(files.get(0).unit()));
    }
}
