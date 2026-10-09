package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import hai913i.tp1.extract.FactExtractor;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;

/** Outils partages par tous les tests. */
public final class TestSupport {

    private static List<ParsedFile> validationParsed;
    private static ProjectFacts validationFacts;

    private TestSupport() {
    }

    // ---------- projet de validation ----------

    /**
     * Le projet de validation (le dossier qui CONTIENT src/). Ordre de recherche :
     * 1. -Dvalidation.dir=/chemin/vers/validation ;
     * 2. le classpath de test (copie Maven de src/test/resources/validation dans target/test-classes) :
     *    independant du repertoire courant, donc valable sous surefire, PIT ou un IDE ;
     * 3. le chemin relatif src/test/resources/validation.
     */
    public static Path validationRoot() {
        String p = System.getProperty("validation.dir");
        if (p != null) {
            return existing(Path.of(p));
        }
        URL onClasspath = TestSupport.class.getResource("/validation");
        if (onClasspath != null && "file".equals(onClasspath.getProtocol())) {
            try {
                return existing(Path.of(onClasspath.toURI()));
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }
        return existing(Path.of("src/test/resources/validation"));
    }

    private static Path existing(Path path) {
        assertTrue(Files.isDirectory(path),
                "Projet de validation introuvable : " + path.toAbsolutePath()
                        + " (copie le dossier 'validation' dans src/test/resources/ ou passe -Dvalidation.dir=...)");
        return path;
    }

    public static synchronized List<ParsedFile> validationParsed() throws IOException {
        if (validationParsed == null) {
            validationParsed = parse(validationRoot());
        }
        return validationParsed;
    }

    public static synchronized ProjectFacts validationFacts() throws IOException {
        if (validationFacts == null) {
            validationFacts = extract(validationParsed());
        }
        return validationFacts;
    }

    // ---------- parsing / extraction ----------

    public static List<ParsedFile> parse(Path projectDir) throws IOException {
        return JdtParser.parse(ProjectSources.of(projectDir), List.of());
    }

    public static ProjectFacts extract(List<ParsedFile> files) {
        try {
            return FactExtractor.extract(files);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ProjectFacts facts(Path projectDir) throws IOException {
        return extract(parse(projectDir));
    }

    public static void write(Path root, String relative, String content) {
        try {
            Path file = root.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Ecrit une source dans tmp/p/Src.java (elle doit declarer 'package p;') et extrait les faits. 
     * @throws IOException */
    public static ProjectFacts factsOf(Path tmp, String source) throws IOException {
        write(tmp, "p/Src.java", source);
        return facts(tmp);
    }

    public static ProjectFacts factsOf(Path tmp, String relative, String source) throws IOException {
        write(tmp, relative, source);
        return facts(tmp);
    }

    /** Parse une source isolee, avec les memes reglages que JdtParser (Java 17, bindings, recovery). */
    public static CompilationUnit parseString(String unitName, String source) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setUnitName(unitName);
        parser.setSource(source.toCharArray());
        return (CompilationUnit) parser.createAST(null);
    }

    public static CompilationUnit validationUnit(String fileName) throws IOException {
        List<ParsedFile> m = validationParsed().stream()
                .filter(f -> f.path().getFileName().toString().equals(fileName)).toList();
        assertEquals(1, m.size(), "fichier " + fileName);
        return m.get(0).unit();
    }

    public static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ---------- acces au modele ----------

    /** Le type dont le nom qualifie est 'name' ou se termine par '.name' (exactement un). */
    public static TypeFact type(ProjectFacts f, String name) {
        List<TypeFact> exact = f.types().stream().filter(t -> t.qualifiedName().equals(name)).toList();
        if (exact.size() == 1) {
            return exact.get(0);
        }
        List<TypeFact> m = f.types().stream().filter(t -> t.qualifiedName().endsWith("." + name)).toList();
        assertEquals(1, m.size(), "type '" + name + "' parmi "
                + f.types().stream().map(TypeFact::qualifiedName).toList());
        return m.get(0);
    }

    public static boolean hasType(ProjectFacts f, String name) {
        return f.types().stream().anyMatch(t -> t.qualifiedName().equals(name) || t.qualifiedName().endsWith("." + name));
    }

    /** La methode de ce nom (exactement une) dans ce type. */
    public static MethodFact method(TypeFact t, String name) {
        List<MethodFact> m = t.methods().stream().filter(x -> x.name().equals(name)).toList();
        assertEquals(1, m.size(), "methode '" + name + "' dans " + t.qualifiedName()
                + " parmi " + t.methods().stream().map(MethodFact::id).toList());
        return m.get(0);
    }

    public static List<MethodFact> allMethods(ProjectFacts f) {
        return f.types().stream().flatMap(t -> t.methods().stream()).toList();
    }

    public static List<CallFact> allCalls(ProjectFacts f) {
        return allMethods(f).stream().flatMap(m -> m.calls().stream()).toList();
    }

    public static List<CallFact> calls(ProjectFacts f, String type, String method) {
        return method(type(f, type), method).calls();
    }
}