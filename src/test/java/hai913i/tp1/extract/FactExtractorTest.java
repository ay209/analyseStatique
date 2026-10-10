package hai913i.tp1.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.TestSupport;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.parse.JdtParser.ParsedFile;

/** B1 : assemblage des faits (totaux, determinisme, robustesse). */
class FactExtractorTest {

    @Test
    void validationTotals() throws IOException {
        ProjectFacts f = TestSupport.validationFacts();
        List<MethodFact> methods = TestSupport.allMethods(f);
        assertEquals(15, f.types().size());
        assertEquals(59, methods.size());
        assertEquals(9, methods.stream().filter(MethodFact::constructor).count());
        assertEquals(21, f.types().stream().mapToInt(t -> t.fields().size()).sum());
        assertEquals(4, f.types().stream().map(TypeFact::packageName).distinct().count());
        assertEquals(53, methods.stream().filter(m -> m.bodyLoc() > 0).count());
        assertEquals(246, methods.stream().mapToInt(MethodFact::bodyLoc).sum());
        assertEquals(493, f.totalLines());
        assertEquals(14, f.sourceFiles().size());
        assertEquals(94, TestSupport.allCalls(f).size());
    }

    @Test
    void totalLinesEqualsIndependentCount() throws IOException {
        long expected = 0;
        try (Stream<Path> s = Files.walk(TestSupport.validationRoot())) {
            for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                expected += Files.readAllLines(p).size();
            }
        }
        assertEquals(expected, TestSupport.validationFacts().totalLines());
    }

    @Test
    void sourceFilesMatchParsedFiles() throws IOException {
        List<Path> parsed = TestSupport.validationParsed().stream().map(ParsedFile::path).sorted().toList();
        assertEquals(parsed, TestSupport.validationFacts().sourceFiles());
    }

    @Test
    void extractingTwiceGivesEqualFacts() throws IOException {
        ProjectFacts a = TestSupport.extract(TestSupport.parse(TestSupport.validationRoot()));
        ProjectFacts b = TestSupport.extract(TestSupport.parse(TestSupport.validationRoot()));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.toString(), b.toString());
    }

    @Test
    void factsDoNotDependOnFileOrder() throws IOException {
        List<ParsedFile> files = new ArrayList<>(TestSupport.parse(TestSupport.validationRoot()));
        ProjectFacts base = TestSupport.extract(files);
        Collections.reverse(files);
        assertEquals(base, TestSupport.extract(files));
        Collections.shuffle(files, new java.util.Random(42));
        assertEquals(base, TestSupport.extract(files));
    }

    @Test
    void everyCallIsStoredUnderItsCaller() throws IOException {
        for (TypeFact t : TestSupport.validationFacts().types()) {
            for (MethodFact m : t.methods()) {
                for (CallFact c : m.calls()) {
                    assertEquals(m.id(), c.callerId());
                }
            }
        }
    }

    @Test
    void emptyInputGivesEmptyFacts() {
        ProjectFacts f = TestSupport.extract(List.of());
        assertTrue(f.types().isEmpty());
        assertTrue(f.sourceFiles().isEmpty());
        assertEquals(0, f.totalLines());
    }

    @Test
    void emptyDirectoryGivesEmptyFacts(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = TestSupport.facts(tmp);
        assertTrue(f.types().isEmpty());
        assertEquals(0, f.totalLines());
    }

    @Test
    void totalLinesCountsPhysicalLinesIncludingBlank(@TempDir Path tmp)throws IOException  {
        assertEquals(3, TestSupport.factsOf(tmp.resolve("b"), "package p;\nclass A {\n}\n").totalLines());
        assertEquals(3, TestSupport.factsOf(tmp.resolve("c"), "package p;\nclass A {\n}").totalLines(),
                "sans retour a la ligne final");
        assertEquals(4, TestSupport.factsOf(tmp.resolve("d"), "package p;\nclass A {\n\n}\n").totalLines(),
                "les lignes vides comptent");
    }

    @Test
    void totalLinesSumsAllFiles(@TempDir Path tmp) throws IOException {
        TestSupport.write(tmp, "p/A.java", "package p;\nclass A { }\n");
        TestSupport.write(tmp, "p/B.java", "package p;\nclass B {\n}\n");
        assertEquals(5, TestSupport.facts(tmp).totalLines());
    }

    @Test
    void sameFactsWhenProjectIsCopiedToAPathWithSpaces(@TempDir Path tmp) throws IOException {
        Path copy = Files.createDirectories(tmp.resolve("mon projet (v2)"));
        TestSupport.copyTree(TestSupport.validationRoot(), copy);
        ProjectFacts copied = TestSupport.facts(copy);
        assertEquals(TestSupport.validationFacts().types(), copied.types());
        assertEquals(TestSupport.validationFacts().totalLines(), copied.totalLines());
    }

    @Test
    void mavenLayoutIsSupported(@TempDir Path tmp)throws IOException  {
        ProjectFacts f = TestSupport.factsOf(tmp, "src/main/java/p/A.java",
                "package p;\nclass A { void f() { g(); } void g() { } }\n");
        assertEquals(1, f.types().size());
        assertEquals(1, TestSupport.allCalls(f).size());
        assertTrue(TestSupport.allCalls(f).get(0).resolved());
    }

    @Test
    void syntaxErrorInOneFileDoesNotPreventAnalysingTheOthers(@TempDir Path tmp) throws IOException {
        TestSupport.write(tmp, "p/Bad.java", "package p;\nclass Bad {\n  void f( {\n  }\n}\n");
        TestSupport.write(tmp, "p/Good.java", "package p;\nclass Good { void g() { h(); } void h() { } }\n");
        ProjectFacts f = TestSupport.facts(tmp);
        assertTrue(TestSupport.hasType(f, "p.Good"));
        assertEquals(1, TestSupport.calls(f, "p.Good", "g").size());
    }

    @Test
    void missingDependencyDoesNotBreakExtraction(@TempDir Path tmp) throws IOException {
        ProjectFacts f = TestSupport.factsOf(tmp, """
                package p;
                import foo.Missing;
                class A {
                    Missing m;
                    void f() { m.go(); ok(); }
                    void ok() { }
                }
                """);
        assertEquals(1, f.types().size());
        assertEquals(2, TestSupport.method(TestSupport.type(f, "p.A"), "f").calls().size());
    }
}
