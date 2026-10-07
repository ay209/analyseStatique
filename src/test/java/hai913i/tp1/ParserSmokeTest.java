package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.Test;

import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;

/**
 * Point de contrôle A0 : l'environnement analyse un projet de deux fichiers et résout un binding
 * d'un fichier à l'autre.
 */
class ParserSmokeTest {

    @Test
    void parsesTheSampleAndResolvesBindingsAcrossFiles() throws Exception {
        ProjectSources sources = ProjectSources.of(Path.of("src/test/resources/sample"));
        List<ParsedFile> files = JdtParser.parse(sources, List.of());

        assertEquals(2, files.size());
        for (ParsedFile file : files) {
            long errors = Arrays.stream(file.unit().getProblems()).filter(p -> p.isError()).count();
            assertEquals(0, errors, file.path() + " doit compiler");
        }

        List<IMethodBinding> bindings = new ArrayList<>();
        for (ParsedFile file : files) {
            file.unit().accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation node) {
                    if (node.getName().getIdentifier().equals("greet")) {
                        bindings.add(node.resolveMethodBinding());
                    }
                    return true;
                }
            });
        }
        assertEquals(1, bindings.size());
        assertNotNull(bindings.get(0), "binding non resolu : verifier la racine des sources et setEnvironment");
        assertTrue(bindings.get(0).getDeclaringClass().getQualifiedName().equals("demo.Greeter"));
    }
}
