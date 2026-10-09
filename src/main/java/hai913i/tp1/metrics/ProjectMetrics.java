package hai913i.tp1.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;

/**
 * B2 : les 13 métriques du sujet, calculées uniquement à partir du modèle de faits
 * (aucune dépendance à JDT : ce paquetage se teste avec des modèles construits à la main).
 *
 * Conventions (voir le rapport) :
 * - une "classe" est un type du modèle (classe, interface, enum, record, types imbriqués compris) ;
 * - les constructeurs comptent comme des méthodes ;
 * - la moyenne de lignes par méthode ne porte que sur les méthodes AYANT un corps ;
 * - aucune division par zéro : une moyenne sans éléments vaut 0.0.
 */
public final class ProjectMetrics {

    private ProjectMetrics() {
    }

    /** Résultat de la question 13 : le maximum et toutes les méthodes qui l'atteignent. */
    public record MaxParameters(int max, List<MethodFact> methods) {
        public MaxParameters {
            methods = List.copyOf(methods);
        }
    }

    // ---------- Q1 à Q4 ----------

    /** Q1 : nombre de classes (types du modèle). */
    public static int classCount(ProjectFacts facts) {
        return facts.types().size();
    }

    /** Q2 : nombre de lignes de code de l'application (somme des lignes physiques des fichiers). */
    public static int applicationLines(ProjectFacts facts) {
        return facts.totalLines();
    }

    /** Q3 : nombre de méthodes (constructeurs compris). */
    public static int methodCount(ProjectFacts facts) {
        int total = 0;
        for (TypeFact type : facts.types()) {
            total += type.methods().size();
        }
        return total;
    }

    /** Q4 : nombre de paquetages distincts (le paquetage par défaut compte pour un). */
    public static int packageCount(ProjectFacts facts) {
        return (int) facts.types().stream().map(TypeFact::packageName).distinct().count();
    }

    // ---------- Q5 à Q7 ----------

    /** Q5 : nombre moyen de méthodes par classe. */
    public static double averageMethodsPerClass(ProjectFacts facts) {
        return divide(methodCount(facts), classCount(facts));
    }

    /** Q6 : nombre moyen de lignes de code par méthode, sur les méthodes qui ont un corps. */
    public static double averageLinesPerMethod(ProjectFacts facts) {
        long lines = 0;
        long withBody = 0;
        for (TypeFact type : facts.types()) {
            for (MethodFact method : type.methods()) {
                if (method.bodyLoc() > 0) {
                    lines += method.bodyLoc();
                    withBody++;
                }
            }
        }
        return divide(lines, withBody);
    }

    /** Q7 : nombre moyen d'attributs par classe. */
    public static double averageFieldsPerClass(ProjectFacts facts) {
        long fields = 0;
        for (TypeFact type : facts.types()) {
            fields += type.fields().size();
        }
        return divide(fields, classCount(facts));
    }

    private static double divide(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    // ---------- règle des 10 % ----------

    /**
     * Les 10 % meilleurs éléments selon une clé entière.
     * - k = ceil(n / 10), calculé en entiers : (n + 9) / 10 ;
     * - on garde TOUS les éléments dont la clé est au moins celle du k-ième (égalités à la frontière) ;
     * - une clé nulle n'est jamais retenue ;
     * - tri décroissant STABLE : à clé égale, l'ordre d'entrée est conservé ;
     * - la liste d'entrée n'est pas modifiée.
     */
    public static <T> List<T> topTenPercent(List<T> items, ToIntFunction<T> key) {
        if (items.isEmpty()) {
            return List.of();
        }
        int k = (items.size() + 9) / 10;
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(key).reversed()); // List.sort est stable
        int threshold = key.applyAsInt(sorted.get(k - 1));
        List<T> result = new ArrayList<>();
        for (T item : sorted) {
            int value = key.applyAsInt(item);
            if (value >= threshold && value > 0) {
                result.add(item);
            }
        }
        return result;
    }

    // ---------- Q8 à Q11 ----------

    /** Q8 : les 10 % de classes qui ont le plus de méthodes. */
    public static List<TypeFact> topTenPercentByMethods(ProjectFacts facts) {
        return topTenPercent(facts.types(), type -> type.methods().size());
    }

    /** Q9 : les 10 % de classes qui ont le plus d'attributs. */
    public static List<TypeFact> topTenPercentByFields(ProjectFacts facts) {
        return topTenPercent(facts.types(), type -> type.fields().size());
    }

    /** Q10 : classes présentes dans les deux classements (ordre du classement par méthodes). */
    public static List<TypeFact> inBothTopTenPercent(ProjectFacts facts) {
        List<TypeFact> byFields = topTenPercentByFields(facts);
        List<TypeFact> result = new ArrayList<>();
        for (TypeFact type : topTenPercentByMethods(facts)) {
            if (byFields.contains(type)) {
                result.add(type);
            }
        }
        return result;
    }

    /** Q11 : classes ayant STRICTEMENT plus de x méthodes. */
    public static List<TypeFact> classesWithMoreMethodsThan(ProjectFacts facts, int x) {
        if (x < 0) {
            throw new IllegalArgumentException("X doit etre positif ou nul : " + x);
        }
        return facts.types().stream().filter(type -> type.methods().size() > x).toList();
    }

    // ---------- Q12, Q13 ----------

    /**
     * Q12 : pour chaque classe, les 10 % de méthodes les plus longues (en lignes de corps).
     * Le n du calcul de k est le nombre de méthodes de la classe, abstraites comprises.
     * Chaque classe a une entrée (liste vide possible) ; les clés sont triées.
     */
    public static Map<String, List<MethodFact>> topTenPercentMethodsByLines(ProjectFacts facts) {
        Map<String, List<MethodFact>> result = new TreeMap<>();
        for (TypeFact type : facts.types()) {
            result.put(type.qualifiedName(), topTenPercent(type.methods(), MethodFact::bodyLoc));
        }
        return result;
    }

    /** Q13 : le plus grand nombre de paramètres et toutes les méthodes qui l'atteignent. */
    public static MaxParameters maxParameters(ProjectFacts facts) {
        int max = 0;
        List<MethodFact> methods = new ArrayList<>();
        for (TypeFact type : facts.types()) {
            for (MethodFact method : type.methods()) {
                int count = method.parameterTypes().size();
                if (count > max) {
                    max = count;
                    methods.clear();
                }
                if (count == max) {
                    methods.add(method);
                }
            }
        }
        return new MaxParameters(max, methods);
    }
}
