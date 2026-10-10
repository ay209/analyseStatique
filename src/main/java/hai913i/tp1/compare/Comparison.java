package hai913i.tp1.compare;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import hai913i.tp1.graph.CallGraph;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;

/**
 * Bonus C1 : compare ce que JDT (nos extracteurs) et Spoon trouvent sur le même projet.
 * Aucune dépendance à JDT ni à Spoon : on compare des faits déjà extraits (testable seule).
 *
 * Trois colonnes : JDT avec les conventions du TP ; Spoon brut (ce que l'outil compte spontanément) ;
 * Spoon une fois nos conventions appliquées. Une différence entre la 1re et la 3e colonne est un écart à expliquer.
 */
public final class Comparison {

    private static final int MAX_LISTED = 10;

    private Comparison() {
    }

    public static List<String> report(ProjectFacts jdt, SpoonResult spoon) {
        ProjectFacts converted = new ProjectFacts(spoon.types(), jdt.sourceFiles(), jdt.totalLines());
        RawCounts raw = spoon.raw();
        List<String> l = new ArrayList<>();
        l.add("Comparaison JDT / Spoon (Spoon en mode noClasspath)");
        l.add("");
        l.add(row("Mesure", "JDT (convention TP)", "Spoon brut", "Spoon + conventions"));
        l.add(row("Types", n(jdt.types().size()),
                raw.topLevelTypes() + " (1er niveau) / " + raw.allTypes() + " (tous)", n(converted.types().size())));
        l.add(row("Methodes (constructeurs compris)", n(methods(jdt)),
                raw.methods() + " methodes + " + raw.constructors() + " constructeurs (" + raw.implicitConstructors()
                        + " implicites)",
                n(methods(converted))));
        l.add(row("Attributs", n(fields(jdt)), raw.fields() + " (dont " + raw.enumConstants() + " constantes d'enum)",
                n(fields(converted))));
        l.add(row("Paquetages", n(packages(jdt)), n(raw.packages()), n(packages(converted))));
        l.add(row("Appels", n(calls(jdt)),
                raw.invocations() + " (dont " + raw.constructorInvocations() + " appels de constructeur)",
                n(calls(converted))));
        l.add(row("Superclasses (entrees)", n(superclasses(jdt)), "-", n(superclasses(converted))));
        l.add(row("Interfaces (entrees)", n(interfaces(jdt)), "-", n(interfaces(converted))));
        l.add("");
        graph(l, jdt, converted);
        return l;
    }

    private static void graph(List<String> l, ProjectFacts jdt, ProjectFacts spoon) {
        CallGraph a = CallGraph.build(jdt);
        CallGraph b = CallGraph.build(spoon);
        l.add("Graphe d'appel (Spoon + conventions)");
        l.add(row("Mesure", "JDT", "Spoon", ""));
        l.add(row("Noeuds", n(a.nodeCount()), n(b.nodeCount()), ""));
        l.add(row("Arcs", n(a.arcCount()), n(b.arcCount()), ""));
        l.add(row("Sites internes", n(a.internalCallSites()), n(b.internalCallSites()), ""));
        l.add(row("Sites externes", n(a.externalCallSites()), n(b.externalCallSites()), ""));
        l.add(row("Sites non resolus", n(a.unresolvedCallSites()), n(b.unresolvedCallSites()), ""));
        diff(l, "Noeuds seulement chez JDT", minus(new TreeSet<>(a.nodes()), new TreeSet<>(b.nodes())));
        diff(l, "Noeuds seulement chez Spoon", minus(new TreeSet<>(b.nodes()), new TreeSet<>(a.nodes())));
        Set<String> arcsA = arcs(a);
        Set<String> arcsB = arcs(b);
        diff(l, "Arcs seulement chez JDT", minus(arcsA, arcsB));
        diff(l, "Arcs seulement chez Spoon", minus(arcsB, arcsA));
        if (arcsA.equals(arcsB) && a.nodes().equals(b.nodes())) {
            l.add("Les deux graphes sont identiques (memes noeuds, memes arcs).");
        }
    }

    private static void diff(List<String> l, String title, Set<String> items) {
        if (items.isEmpty()) {
            return;
        }
        l.add(title + " (" + items.size() + ") :");
        items.stream().limit(MAX_LISTED).forEach(i -> l.add("  " + i));
        if (items.size() > MAX_LISTED) {
            l.add("  ... et " + (items.size() - MAX_LISTED) + " autre(s).");
        }
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> result = new TreeSet<>(a);
        result.removeAll(b);
        return result;
    }

    private static Set<String> arcs(CallGraph g) {
        Set<String> result = new TreeSet<>();
        g.arcs().forEach(arc -> result.add(arc.caller() + " -> " + arc.callee() + " x" + arc.weight()));
        return result;
    }

    // ---------- comptages sur les faits ----------

    static int methods(ProjectFacts f) {
        return f.types().stream().mapToInt(t -> t.methods().size()).sum();
    }

    static int fields(ProjectFacts f) {
        return f.types().stream().mapToInt(t -> t.fields().size()).sum();
    }

    static int packages(ProjectFacts f) {
        return (int) f.types().stream().map(TypeFact::packageName).distinct().count();
    }

    static int calls(ProjectFacts f) {
        int total = 0;
        for (TypeFact t : f.types()) {
            for (MethodFact m : t.methods()) {
                for (CallFact ignored : m.calls()) {
                    total++;
                }
            }
        }
        return total;
    }

    static int superclasses(ProjectFacts f) {
        return f.types().stream().mapToInt(t -> t.superclasses().size()).sum();
    }

    static int interfaces(ProjectFacts f) {
        return f.types().stream().mapToInt(t -> t.interfaces().size()).sum();
    }

    private static String n(int value) {
        return Integer.toString(value);
    }

    private static String row(String a, String b, String c, String d) {
        String line = String.format("%-34s %-22s %-48s %s", a, b, c, d);
        return line.stripTrailing();
    }
}
