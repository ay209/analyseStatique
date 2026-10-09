package hai913i.tp1.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.TestSupport;

/** A0 : decouverte des sources (racine des sources, liste des .java). */
class ProjectSourcesTest {

    private static Path norm(Path p) {
        return p.toAbsolutePath().normalize();
    }

    @Test
    void notADirectoryIsRejected(@TempDir Path tmp) throws IOException {
        Path file = Files.writeString(tmp.resolve("x.txt"), "x");
        assertThrows(IllegalArgumentException.class, () -> ProjectSources.of(file));
        assertThrows(IllegalArgumentException.class, () -> ProjectSources.of(tmp.resolve("n-existe-pas")));
    }

    @Test
    void errorMessageNamesThePath(@TempDir Path tmp) {
        Path missing = tmp.resolve("n-existe-pas");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ProjectSources.of(missing));
        assertTrue(e.getMessage().contains("n-existe-pas"), e.getMessage());
    }

    @Test
    void sourceRootPrefersSrcMainJava(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("src/main/java"));
        Files.createDirectories(tmp.resolve("src/autre"));
        assertEquals(norm(tmp.resolve("src/main/java")), norm(ProjectSources.of(tmp).sourceRoot()));
    }

    @Test
    void sourceRootFallsBackToSrc(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("src/library"));
        assertEquals(norm(tmp.resolve("src")), norm(ProjectSources.of(tmp).sourceRoot()));
    }

    @Test
    void sourceRootFallsBackToProjectDirectory(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("p"));
        assertEquals(norm(tmp), norm(ProjectSources.of(tmp).sourceRoot()));
    }

    @Test
    void validationUsesSrcAsRoot() throws IOException {
        ProjectSources s = ProjectSources.of(TestSupport.validationRoot());
        assertEquals(norm(TestSupport.validationRoot().resolve("src")), norm(s.sourceRoot()));
        assertEquals(norm(TestSupport.validationRoot()), norm(s.projectDirectory()));
    }

    @Test
    void validationHas14JavaFilesSortedAndOnlyJava() throws IOException {
        List<Path> files = ProjectSources.of(TestSupport.validationRoot()).javaFiles();
        assertEquals(14, files.size());
        assertTrue(files.stream().allMatch(f -> f.getFileName().toString().endsWith(".java")));
        List<Path> sorted = new ArrayList<>(files);
        Collections.sort(sorted);
        assertEquals(sorted, files, "la liste doit etre triee (determinisme)");
        assertEquals(14, files.stream().distinct().count());
    }

    @Test
    void nonJavaFilesAreIgnoredAndSubdirectoriesWalked(@TempDir Path tmp) throws IOException {
        TestSupport.write(tmp, "src/p/A.java", "package p; class A {}");
        TestSupport.write(tmp, "src/p/q/B.java", "package p.q; class B {}");
        TestSupport.write(tmp, "src/p/notes.txt", "pas du java");
        TestSupport.write(tmp, "src/p/README.md", "# x");
        List<Path> files = ProjectSources.of(tmp).javaFiles();
        assertEquals(2, files.size(), files.toString());
        assertTrue(files.stream().anyMatch(f -> f.endsWith("A.java")));
        assertTrue(files.stream().anyMatch(f -> f.endsWith("B.java")));
    }

    @Test
    void emptyProjectHasNoFiles(@TempDir Path tmp) throws IOException {
        assertTrue(ProjectSources.of(tmp).javaFiles().isEmpty());
    }

    @Test
    void pathWithSpacesIsSupported(@TempDir Path tmp) throws IOException {
        Path copy = Files.createDirectories(tmp.resolve("mon projet (copie)"));
        TestSupport.copyTree(TestSupport.validationRoot(), copy);
        assertEquals(14, ProjectSources.of(copy).javaFiles().size());
        assertFalse(norm(ProjectSources.of(copy).sourceRoot()).toString().isEmpty());
    }
}
