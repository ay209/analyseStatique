package hai913i.tp1.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import hai913i.tp1.compare.Comparison;
import hai913i.tp1.compare.SpoonResult;
import hai913i.tp1.graph.CallGraph;
import hai913i.tp1.parse.Diagnostic;
import hai913i.tp1.spoon.SpoonExtractor;

/**
 * Orchestration de la ligne de commande : valider les arguments, analyser, afficher.
 * Toute la logique est dans des composants séparés (CommandLine, Analysis, Report, CallGraph...) ;
 * cette classe ne fait que les enchaîner et traduire les erreurs en messages et en codes de sortie.
 */
public final class Cli {

    public static final int SUCCESS = 0;
    public static final int ANALYSIS_IMPOSSIBLE = 1;
    public static final int USAGE_ERROR = 2;
    public static final int INTERNAL_ERROR = 3;

    private static final int MAX_DIAGNOSTICS_SHOWN = 20;

    private final PrintStream out;
    private final PrintStream err;

    public Cli(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /** Exécute la commande et renvoie le code de sortie. Ne lance jamais d'exception. */
    public int run(String... args) {
        try {
            return execute(List.of(args));
        } catch (CliException e) {
            err.println(e.getMessage());
            if (e.exitCode() == USAGE_ERROR) {
                err.println();
                err.print(CommandLine.USAGE);
            }
            return e.exitCode();
        } catch (IOException | UncheckedIOException e) {
            err.println("Erreur de lecture des fichiers : " + e.getMessage());
            return ANALYSIS_IMPOSSIBLE;
        } catch (RuntimeException | StackOverflowError e) {
            err.println("Erreur interne : " + e);
            return INTERNAL_ERROR;
        }
    }

    private int execute(List<String> args) throws IOException {
        if (!args.isEmpty() && List.of("-h", "--help", "help").contains(args.get(0))) {
            out.print(CommandLine.USAGE);
            return SUCCESS;
        }
        Command command = CommandLine.parse(args);
        Path directory = existingDirectory(command.project());
        Analysis analysis = Analysis.run(directory);
        reportCompilationErrors(analysis);
        print(command, analysis);
        return SUCCESS;
    }

    private static Path existingDirectory(String text) {
        Path path;
        try {
            path = Path.of(text);
        } catch (InvalidPathException e) {
            throw new CliException(ANALYSIS_IMPOSSIBLE, "Chemin invalide : " + text);
        }
        if (!Files.exists(path)) {
            throw new CliException(ANALYSIS_IMPOSSIBLE, "Le dossier n'existe pas : " + text);
        }
        if (!Files.isDirectory(path)) {
            throw new CliException(ANALYSIS_IMPOSSIBLE, "Ce n'est pas un dossier : " + text);
        }
        return path;
    }

    /** Les erreurs de compilation ne sont pas fatales : on les signale et l'analyse continue. */
    private void reportCompilationErrors(Analysis analysis) {
        List<Diagnostic> errors = analysis.errors();
        if (errors.isEmpty()) {
            return;
        }
        long files = errors.stream().map(Diagnostic::file).distinct().count();
        err.println("Avertissement : " + errors.size() + " erreur(s) de compilation dans " + files
                + " fichier(s). L'analyse continue avec ce que JDT a pu comprendre.");
        for (Diagnostic d : errors.stream().limit(MAX_DIAGNOSTICS_SHOWN).toList()) {
            err.println("  " + display(analysis, d) + ":" + d.line() + ": " + d.message());
        }
        if (errors.size() > MAX_DIAGNOSTICS_SHOWN) {
            err.println("  ... et " + (errors.size() - MAX_DIAGNOSTICS_SHOWN) + " autre(s).");
        }
    }

    private static String display(Analysis analysis, Diagnostic d) {
        Path root = analysis.sources().sourceRoot();
        return d.file().startsWith(root) ? root.relativize(d.file()).toString() : d.file().toString();
    }

    private void print(Command command, Analysis analysis) {
        switch (command.action()) {
            case STRUCTURE -> lines(Report.structure(analysis.facts()));
            case CALLS -> lines(Report.calls(analysis.facts()));
            case METRICS -> lines(Report.metrics(analysis.facts(), command.threshold()));
            case GRAPH -> lines(Report.graph(CallGraph.build(analysis.facts(), command.cha())));
            case DOT -> dot(command, analysis);
            case CALLERS -> neighbours(analysis, command, true);
            case CALLEES -> neighbours(analysis, command, false);
            case SPOON -> lines(Comparison.report(analysis.facts(), spoon(analysis)));
        }
    }

    /** Bonus C1 : refait l'extraction avec Spoon. Un échec de Spoon est une analyse impossible, pas un plantage. */
    private static SpoonResult spoon(Analysis analysis) {
        try {
            return SpoonExtractor.extract(analysis.sources().sourceRoot());
        } catch (RuntimeException | LinkageError e) {
            throw new CliException(ANALYSIS_IMPOSSIBLE, "Spoon n'a pas pu analyser le projet : " + e);
        }
    }

    /** Le graphe en DOT, éventuellement restreint à un paquetage et/ou au voisinage d'une méthode. */
    private void dot(Command command, Analysis analysis) {
        CallGraph graph = CallGraph.build(analysis.facts(), command.cha());
        if (command.focus() == null && command.packageFilter() == null) {
            out.print(graph.toDot());
            return;
        }
        Set<String> keep = new TreeSet<>(graph.nodes());
        if (command.focus() != null) {
            keep = new TreeSet<>();
            for (String id : resolve(graph, command.focus())) {
                keep.addAll(graph.neighbourhood(id));
            }
        }
        if (command.packageFilter() != null) {
            Set<String> inPackage = graph.inPackage(command.packageFilter());
            if (inPackage.isEmpty()) {
                err.println("Avertissement : aucune methode dans le paquetage '" + command.packageFilter() + "'.");
            }
            keep.retainAll(inPackage);
        }
        out.print(graph.toDot(keep));
    }

    private static List<String> resolve(CallGraph graph, String query) {
        List<String> ids = graph.find(query);
        if (ids.isEmpty()) {
            throw new CliException(ANALYSIS_IMPOSSIBLE, "Methode inconnue : " + query
                    + " (utiliser un identifiant complet ou Type#methode ; voir l'action 'structure').");
        }
        return ids;
    }

    private void neighbours(Analysis analysis, Command command, boolean callers) {
        String query = command.method();
        CallGraph graph = CallGraph.build(analysis.facts(), command.cha());
        lines(Report.neighbours(graph, resolve(graph, query), callers));
    }

    private void lines(List<String> lines) {
        lines.forEach(out::println);
    }
}
