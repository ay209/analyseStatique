package hai913i.tp1.parse;

import java.nio.file.Path;

/** Une erreur de compilation signalée par JDT (sans type JDT : utilisable par le reste de l'outil). */
public record Diagnostic(Path file, int line, String message) {
}
