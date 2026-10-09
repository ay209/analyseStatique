package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.FieldFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;

/** B1/B2 : le modele et les metriques ne dependent pas de JDT (separation extraction / traitement). */
class ArchitectureTest {

    private static final Path MAIN = Path.of("src/main/java/hai913i/tp1");

    private static void assertNoJdt(String pkg) throws IOException {
        Path dir = MAIN.resolve(pkg);
        assertTrue(Files.isDirectory(dir), "dossier introuvable : " + dir.toAbsolutePath());
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                assertFalse(Files.readString(p).contains("org.eclipse"), p + " ne doit pas importer JDT");
            }
        }
    }

    @Test
    void modelDoesNotImportJdt() throws IOException {
        assertNoJdt("model");
    }

    

    @Test
    void recordComponentsAreNotJdtTypes() {
        for (Class<?> c : List.of(CallFact.class, FieldFact.class, MethodFact.class, TypeFact.class, ProjectFacts.class)) {
            for (RecordComponent rc : c.getRecordComponents()) {
                assertFalse(rc.getGenericType().getTypeName().contains("org.eclipse"), c + "." + rc.getName());
            }
        }
    }
}
