package hai913i.tp1.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import hai913i.tp1.extract.FactExtractor;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.parse.Diagnostic;
import hai913i.tp1.parse.Diagnostics;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;

/** Le résultat de l'analyse d'un projet : ses faits et les erreurs de compilation rencontrées. */
public record Analysis(ProjectSources sources, ProjectFacts facts, List<Diagnostic> errors) {

    /** Analyse le projet ; refuse un dossier qui ne contient aucun fichier Java. */
    public static Analysis run(Path directory) throws IOException {
        ProjectSources sources = ProjectSources.of(directory);
        if (sources.javaFiles().isEmpty()) {
            throw new CliException(Cli.ANALYSIS_IMPOSSIBLE, "Aucun fichier Java dans " + directory + ".");
        }
        List<ParsedFile> parsed = JdtParser.parse(sources, List.of());
        ProjectFacts facts = FactExtractor.extract(parsed);
        return new Analysis(sources, facts, Diagnostics.errors(parsed));
    }
}
