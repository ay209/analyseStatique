package hai913i.tp1.parse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.core.compiler.IProblem;

import hai913i.tp1.parse.JdtParser.ParsedFile;

/**
 * Extrait les erreurs de compilation des unités parsées. C'est le seul endroit (avec le parseur)
 * qui touche aux types de problèmes JDT : le CLI ne voit que des {@link Diagnostic}.
 */
public final class Diagnostics {

    private Diagnostics() {
    }

    /** Les erreurs (pas les avertissements), triées par fichier, ligne puis message. */
    public static List<Diagnostic> errors(List<ParsedFile> files) {
        List<Diagnostic> result = new ArrayList<>();
        for (ParsedFile file : files) {
            for (IProblem problem : file.unit().getProblems()) {
                if (problem.isError()) {
                    result.add(new Diagnostic(file.path(), problem.getSourceLineNumber(), problem.getMessage()));
                }
            }
        }
        result.sort(Comparator.comparing((Diagnostic d) -> d.file().toString())
                .thenComparingInt(Diagnostic::line)
                .thenComparing(Diagnostic::message));
        return result;
    }
}
