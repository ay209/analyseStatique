package hai913i.tp1.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.FactsInvariants;
import hai913i.tp1.TestSupport;
import hai913i.tp1.model.ProjectFacts;

/**
 * Jeux de robustesse du sujet (no-java-files, syntax-error, missing-dep) : reconstitues ici a la main tant qu'on n'a pas
 * les officiels. Pour lancer aussi les officiels : mvn test -Drobustness.dir=/chemin/robustness
 * (chaque sous-dossier est un projet ; on exige seulement : aucun plantage + invariants faibles).
 */
class RobustnessSamplesTest {

    @Test
    void noJavaFilesGivesEmptyFacts(@TempDir Path tmp) throws IOException {
        TestSupport.write(tmp, "README.txt", "rien a analyser");
        TestSupport.write(tmp, "docs/notes.md", "# notes");
        TestSupport.write(tmp, "src/.gitkeep", "");
        ProjectFacts f = TestSupport.facts(tmp);
        assertTrue(f.types().isEmpty());
        assertTrue(f.sourceFiles().isEmpty());
        assertEquals(0, f.totalLines());
        FactsInvariants.checkStrict(f);
    }

    @Test
    void syntaxErrorsDoNotHideTheValidFiles(@TempDir Path tmp) throws IOException{
        TestSupport.write(tmp, "p/Bad.java", "package p;\nclass Bad {\n  void f( {\n    int x = ;\n  }\n}\n");
        TestSupport.write(tmp, "p/Good.java", "package p;\nclass Good { void g() { h(); }\n void h() { } }\n");
        TestSupport.write(tmp, "p/Garbage.java", "}}}{{{ ;;; @@@ \n");
        TestSupport.write(tmp, "p/Unclosed.java", "package p;\nclass Unclosed {\n  void f() {\n");
        ProjectFacts f = TestSupport.facts(tmp);
        assertTrue(TestSupport.hasType(f, "p.Good"));
        assertEquals(1, TestSupport.calls(f, "p.Good", "g").size());
        assertEquals(4, f.sourceFiles().size());
        FactsInvariants.checkWeak(f);
    }

    @Test
    void emptyAndCommentOnlyFilesAreTolerated(@TempDir Path tmp) throws IOException{
        TestSupport.write(tmp, "p/Empty.java", "");
        TestSupport.write(tmp, "p/OnlyPackage.java", "package p;\n");
        TestSupport.write(tmp, "p/Comment.java", "/* licence */\n");
        TestSupport.write(tmp, "p/A.java", "package p;\nclass A { }\n");
        ProjectFacts f = TestSupport.facts(tmp);
        assertEquals(1, f.types().size());
        FactsInvariants.checkStrict(f);
    }

    @Test
    void missingDependencyKeepsTheProjectAnalysable(@TempDir Path tmp)throws IOException {
        TestSupport.write(tmp, "p/Uses.java", """
                package p;
                import org.absent.Library;
                class Uses {
                    Library lib;
                    void f() { lib.run(); helper(); }
                    void helper() { }
                }
                """);
        TestSupport.write(tmp, "p/Other.java", "package p;\nclass Other { void g() { new Uses().helper(); } }\n");
        ProjectFacts f = TestSupport.facts(tmp);
        assertEquals(2, f.types().size());
        assertEquals(2, TestSupport.calls(f, "p.Uses", "f").size());
        assertEquals(1, TestSupport.calls(f, "p.Other", "g").size());
        FactsInvariants.checkWeak(f);
    }

    @Test
    void duplicateClassNamesInDifferentFilesDoNotCrash(@TempDir Path tmp) throws IOException{
        TestSupport.write(tmp, "a/A.java", "package p;\nclass A { void f() { } }\n");
        TestSupport.write(tmp, "b/A.java", "package p;\nclass A { void g() { } }\n");
        ProjectFacts f = TestSupport.facts(tmp);
        assertTrue(f.types().size() >= 1);
        FactsInvariants.checkWeak(f);
    }

    @Test
    void deeplyNestedExpressionsDoNotOverflowTheStack(@TempDir Path tmp) throws IOException{
        StringBuilder expr = new StringBuilder("0");
        for (int i = 0; i < 400; i++) {
            expr.append(" + id(").append(i).append(")");
        }
        TestSupport.write(tmp, "p/Deep.java",
                "package p;\nclass Deep { static int id(int x) { return x; }\n int f() { return " + expr + "; } }\n");
        ProjectFacts f = TestSupport.facts(tmp);
        assertEquals(400, TestSupport.calls(f, "p.Deep", "f").size());
    }

    @TestFactory
    Stream<DynamicTest> officialSamplesIfProvided() throws IOException {
        String dir = System.getProperty("robustness.dir");
        Assumptions.assumeTrue(dir != null, "pas de -Drobustness.dir : echantillons officiels non fournis");
        List<Path> samples;
        try (Stream<Path> s = Files.list(Path.of(dir))) {
            samples = s.filter(Files::isDirectory).sorted().toList();
        }
        return samples.stream().map(sample -> DynamicTest.dynamicTest(sample.getFileName().toString(), () -> {
            ProjectFacts f = TestSupport.facts(sample);
            FactsInvariants.checkWeak(f);
            assertEquals(f, TestSupport.facts(sample), "determinisme");
        }));
    }
}
