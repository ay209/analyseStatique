package hai913i.tp1.parse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Les sources Java du projet à analyser.
 *
 * Racine des sources : src/main/java si ce dossier existe, sinon src, sinon le dossier du projet.
 * Cette racine compte : c'est à partir d'elle que JDT résout les types des autres fichiers (bindings).
 */
public record ProjectSources(Path projectDirectory, Path sourceRoot, List<Path> javaFiles) {

    public static ProjectSources of(Path projectDirectory) throws IOException {
        if (!Files.isDirectory(projectDirectory)) {
            throw new IllegalArgumentException("Pas un repertoire : " + projectDirectory);
        }
        Path project = projectDirectory.toAbsolutePath().normalize();
        Path root = Stream.of(project.resolve("src/main/java"), project.resolve("src"))
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(project);
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> files = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
            return new ProjectSources(project, root, files);
        }
    }
}
