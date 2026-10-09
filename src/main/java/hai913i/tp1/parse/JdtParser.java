package hai913i.tp1.parse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;

/**
 * Extracteur de modèle (CM 02) : fichiers sources -> AST JDT avec bindings.
 *
 * Chaque réglage est expliqué dans l'annexe « Eclipse JDT et Spoon »
 * (section « Configurer ASTParser »). En retirer un dégrade les bindings,
 * souvent sans erreur visible : vérifiez sur le projet de validation.
 */
public final class JdtParser {

    /** Un fichier source analysé. */
    public record ParsedFile(Path path, CompilationUnit unit) {
    }

    private JdtParser() {
    }

    public static List<ParsedFile> parse(ProjectSources sources,
            List<String> classpath) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());

        // Niveau de langage du code analysé : Java 17.
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);

        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);

        // 1. JAR externes du projet analysé (éventuellement aucun)
        // 2. racine des sources   3. encodage
        // 4. true : inclure le JDK qui exécute l'analyseur (java.lang...)
        parser.setEnvironment(
                classpath.toArray(String[]::new),
                new String[] {sources.sourceRoot().toString()},
                new String[] {"UTF-8"},
                true);

        String[] paths = sources.javaFiles().stream()
                .map(Path::toString)
                .toArray(String[]::new);
        String[] encodings = sources.javaFiles().stream()
                .map(p -> "UTF-8")
                .toArray(String[]::new);

        // Un seul lot : les bindings sont partagés entre les fichiers.
        List<ParsedFile> parsed = new ArrayList<>();
        parser.createASTs(paths, encodings, new String[0],
                new FileASTRequestor() {
                    @Override
                    public void acceptAST(String file, CompilationUnit ast) {
                        parsed.add(new ParsedFile(Path.of(file), ast));
                    }
                }, null);
        return parsed;
    }
}
