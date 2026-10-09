package hai913i.tp1;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hai913i.tp1.extract.FactExtractor;
import hai913i.tp1.metrics.ProjectMetrics;
import hai913i.tp1.metrics.ProjectMetrics.MaxParameters;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.ProjectSources;

/**
 * Vérification manuelle de B2 : affiche les 13 métriques d'un projet.
 * Usage : java -cp target/classes:... hai913i.tp1.MetricsCheck CHEMIN_DU_PROJET [X]
 */
public final class MetricsCheck {

    private MetricsCheck() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage : MetricsCheck CHEMIN_DU_PROJET [X]");
            System.exit(2);
        }
        int x = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        ProjectSources sources = ProjectSources.of(Path.of(args[0]));
        ProjectFacts facts = FactExtractor.extract(JdtParser.parse(sources, List.of()));

        System.out.println("Q1  classes                  : " + ProjectMetrics.classCount(facts));
        System.out.println("Q2  lignes de code           : " + ProjectMetrics.applicationLines(facts));
        System.out.println("Q3  methodes                 : " + ProjectMetrics.methodCount(facts));
        System.out.println("Q4  paquetages               : " + ProjectMetrics.packageCount(facts));
        System.out.println("Q5  methodes / classe        : " + fmt(ProjectMetrics.averageMethodsPerClass(facts)));
        System.out.println("Q6  lignes / methode (corps) : " + fmt(ProjectMetrics.averageLinesPerMethod(facts)));
        System.out.println("Q7  attributs / classe       : " + fmt(ProjectMetrics.averageFieldsPerClass(facts)));
        System.out.println("Q8  top 10 % par methodes    : " + describe(ProjectMetrics.topTenPercentByMethods(facts), true));
        System.out.println("Q9  top 10 % par attributs   : " + describe(ProjectMetrics.topTenPercentByFields(facts), false));
        System.out.println("Q10 dans les deux classements: " + names(ProjectMetrics.inBothTopTenPercent(facts)));
        System.out.println("Q11 plus de " + x + " methodes      : "
                + describe(ProjectMetrics.classesWithMoreMethodsThan(facts, x), true));

        System.out.println("Q12 methodes les plus longues par classe :");
        for (Map.Entry<String, List<MethodFact>> e : ProjectMetrics.topTenPercentMethodsByLines(facts).entrySet()) {
            StringBuilder line = new StringBuilder("      " + e.getKey() + " : ");
            if (e.getValue().isEmpty()) {
                line.append("(aucune)");
            }
            for (MethodFact m : e.getValue()) {
                line.append(m.name()).append('(').append(m.bodyLoc()).append(") ");
            }
            System.out.println(line.toString().stripTrailing());
        }

        MaxParameters mp = ProjectMetrics.maxParameters(facts);
        System.out.println("Q13 max de parametres        : " + mp.max());
        for (MethodFact m : mp.methods()) {
            System.out.println("      " + m.id());
        }
    }

    private static String fmt(double d) {
        return String.format(Locale.FRANCE, "%.2f", d);
    }

    private static String names(List<TypeFact> types) {
        return types.stream().map(TypeFact::qualifiedName).toList().toString();
    }

    private static String describe(List<TypeFact> types, boolean methods) {
        return types.stream()
                .map(t -> t.qualifiedName() + "(" + (methods ? t.methods().size() : t.fields().size()) + ")")
                .toList().toString();
    }
}
