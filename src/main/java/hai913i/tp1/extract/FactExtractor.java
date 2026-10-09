package hai913i.tp1.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.parse.JdtParser.ParsedFile;

/** Seule classe qui enchaîne les extracteurs : la frontière avec JDT s'arrête ici. */
public final class FactExtractor {

    private FactExtractor() {}

    public static ProjectFacts extract(List<ParsedFile> files) throws IOException {
        StructureVisitor structure = new StructureVisitor();
        CallVisitor calls = new CallVisitor();
        for (ParsedFile f : files) {
            f.unit().accept(structure); // passe 1 : types, attributs, méthodes
            f.unit().accept(calls);     // passe 2 : appels
        }
        Map<String, List<CallFact>> callsByMethod = calls.callsByMethod();

        // Les records sont immuables : on reconstruit chaque méthode avec ses appels.
        List<TypeFact> types = new ArrayList<>();
        for (TypeFact t : structure.types()) {
            List<MethodFact> methods = new ArrayList<>();
            for (MethodFact m : t.methods()) {
                methods.add(new MethodFact(m.id(), m.name(), m.parameterTypes(), m.constructor(),
                        m.bodyLoc(), callsByMethod.getOrDefault(m.id(), List.of())));
            }
            types.add(new TypeFact(t.qualifiedName(), t.packageName(), t.kind(),
                    t.superclasses(), t.interfaces(), t.fields(), methods));
        }

        List<Path> paths = files.stream().map(ParsedFile::path).toList();
        int totalLines = 0;
        for (Path p : paths) {
            totalLines += countLines(p); // §4.2 : lignes physiques
        }
        return new ProjectFacts(types, paths, totalLines);
    }

    /**
     * Nombre de lignes physiques, SANS décoder le fichier : readAllLines lève MalformedInputException sur un
     * fichier qui n'est pas de l'UTF-8 valide (ex. ISO-8859-1), alors qu'on n'a besoin que des fins de ligne.
     * Une ligne se termine par \n, \r ou \r\n ; une dernière ligne sans fin de ligne compte aussi.
     * Les octets 0x0A et 0x0D n'apparaissent jamais à l'intérieur d'un caractère UTF-8 multi-octets.
     */
    static int countLines(Path file) throws IOException {
        byte[] b = Files.readAllBytes(file);
        int lines = 0;
        boolean pending = false; // du texte sur la dernière ligne, pas encore terminée
        for (int i = 0; i < b.length; i++) {
            if (b[i] == '\n') {
                lines++;
                pending = false;
            } else if (b[i] == '\r') {
                lines++;
                pending = false;
                if (i + 1 < b.length && b[i + 1] == '\n') {
                    i++; // \r\n = une seule fin de ligne
                }
            } else {
                pending = true;
            }
        }
        return pending ? lines + 1 : lines;
    }
}