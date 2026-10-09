package hai913i.tp1.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.TestSupport;

/** Tue le mutant ProjectSources ligne 28 : un DOSSIER dont le nom finit par .java n'est pas un fichier source. */
class ProjectSourcesDirectoryTest {

    @Test
    void aDirectoryNamedLikeAJavaFileIsNotASourceFile(@TempDir Path tmp) throws IOException {
        TestSupport.write(tmp, "src/p/A.java", "package p; class A {}");
        Files.createDirectories(tmp.resolve("src/p/Weird.java"));
        List<Path> files = ProjectSources.of(tmp).javaFiles();
        assertEquals(1, files.size(), files.toString());
        assertTrue(files.get(0).endsWith("A.java"));
    }
}
