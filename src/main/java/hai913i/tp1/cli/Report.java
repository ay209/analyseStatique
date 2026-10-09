package hai913i.tp1.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hai913i.tp1.graph.CallGraph;
import hai913i.tp1.graph.CallGraph.Arc;
import hai913i.tp1.metrics.ProjectMetrics;
import hai913i.tp1.metrics.ProjectMetrics.MaxParameters;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.FieldFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;

/** Affichage : transforme le modèle, les métriques et le graphe en lignes de texte (aucune E/S ici). */
public final class Report {

    private Report() {
    }

    public static List<String> structure(ProjectFacts facts) {
        List<String> lines = new ArrayList<>();
        for (TypeFact type : facts.types()) {
            StringBuilder head = new StringBuilder(type.qualifiedName()).append(" [").append(type.kind()).append(']');
            if (!type.superclasses().isEmpty()) {
                head.append(" extends ").append(String.join(" > ", type.superclasses()));
            }
            if (!type.interfaces().isEmpty()) {
                head.append(" implements ").append(String.join(", ", type.interfaces()));
            }
            lines.add(head.toString());
            for (FieldFact field : type.fields()) {
                lines.add("  champ   " + field.name() + " : " + field.declaredType() + " (" + field.visibility() + ")");
            }
            for (MethodFact method : type.methods()) {
                lines.add("  methode " + method.id() + (method.constructor() ? " [constructeur]" : "")
                        + "  " + method.bodyLoc() + " ligne(s)");
            }
        }
        lines.add("");
        lines.add(facts.sourceFiles().size() + " fichier(s), " + facts.types().size() + " type(s), "
                + ProjectMetrics.methodCount(facts) + " methode(s), " + facts.totalLines() + " ligne(s).");
        return lines;
    }

    public static List<String> calls(ProjectFacts facts) {
        List<String> lines = new ArrayList<>();
        int total = 0;
        for (TypeFact type : facts.types()) {
            for (MethodFact method : type.methods()) {
                if (method.calls().isEmpty()) {
                    continue;
                }
                lines.add(method.id());
                for (CallFact call : method.calls()) {
                    total++;
                    lines.add("  l." + call.sourceLine() + "  " + call.staticReceiverType() + " -> "
                            + call.targetId().orElse("?") + "  [" + kind(call) + "]");
                }
            }
        }
        lines.add("");
        lines.add(total + " appel(s).");
        return lines;
    }

    private static String kind(CallFact call) {
        if (!call.resolved()) {
            return "non resolu";
        }
        return call.internal() ? "interne" : "externe";
    }

    public static List<String> metrics(ProjectFacts facts, int x) {
        List<String> l = new ArrayList<>();
        l.add("Q1  classes                  : " + ProjectMetrics.classCount(facts));
        l.add("Q2  lignes de code           : " + ProjectMetrics.applicationLines(facts));
        l.add("Q3  methodes                 : " + ProjectMetrics.methodCount(facts));
        l.add("Q4  paquetages               : " + ProjectMetrics.packageCount(facts));
        l.add("Q5  methodes / classe        : " + decimal(ProjectMetrics.averageMethodsPerClass(facts)));
        l.add("Q6  lignes / methode (corps) : " + decimal(ProjectMetrics.averageLinesPerMethod(facts)));
        l.add("Q7  attributs / classe       : " + decimal(ProjectMetrics.averageFieldsPerClass(facts)));
        l.add("Q8  top 10 % par methodes    : " + described(ProjectMetrics.topTenPercentByMethods(facts), true));
        l.add("Q9  top 10 % par attributs   : " + described(ProjectMetrics.topTenPercentByFields(facts), false));
        l.add("Q10 dans les deux classements: "
                + ProjectMetrics.inBothTopTenPercent(facts).stream().map(TypeFact::qualifiedName).toList());
        l.add("Q11 plus de " + x + " methodes      : "
                + described(ProjectMetrics.classesWithMoreMethodsThan(facts, x), true));
        l.add("Q12 methodes les plus longues par classe :");
        for (Map.Entry<String, List<MethodFact>> e : ProjectMetrics.topTenPercentMethodsByLines(facts).entrySet()) {
            StringBuilder line = new StringBuilder("      " + e.getKey() + " : ");
            if (e.getValue().isEmpty()) {
                line.append("(aucune)");
            }
            for (MethodFact m : e.getValue()) {
                line.append(m.name()).append('(').append(m.bodyLoc()).append(") ");
            }
            l.add(line.toString().stripTrailing());
        }
        MaxParameters mp = ProjectMetrics.maxParameters(facts);
        l.add("Q13 max de parametres        : " + mp.max());
        for (MethodFact m : mp.methods()) {
            l.add("      " + m.id());
        }
        return l;
    }

    private static String decimal(double d) {
        return String.format(Locale.FRANCE, "%.2f", d);
    }

    private static String described(List<TypeFact> types, boolean methods) {
        return types.stream()
                .map(t -> t.qualifiedName() + "(" + (methods ? t.methods().size() : t.fields().size()) + ")")
                .toList().toString();
    }

    public static List<String> graph(CallGraph graph) {
        List<String> l = new ArrayList<>();
        l.add("Noeuds (methodes)       : " + graph.nodeCount());
        l.add("Arcs                    : " + graph.arcCount());
        l.add("Sites d'appel (total)   : " + graph.callSiteCount());
        l.add("  internes              : " + graph.internalCallSites());
        l.add("  externes              : " + graph.externalCallSites());
        l.add("  non resolus           : " + graph.unresolvedCallSites());
        l.add("  internes sans noeud   : " + graph.danglingCallSites());
        l.add("Arcs :");
        for (Arc a : graph.arcs()) {
            l.add("  " + a.caller() + " -> " + a.callee() + weight(a));
        }
        return l;
    }

    /** Les appelants (ou les appelés) de chaque méthode qui correspond à la requête ; vide si aucune. */
    public static List<String> neighbours(CallGraph graph, List<String> ids, boolean callers) {
        List<String> l = new ArrayList<>();
        for (String id : ids) {
            List<Arc> arcs = callers ? graph.callers(id) : graph.callees(id);
            l.add((callers ? "Appelants de " : "Appeles par ") + id + " : " + (arcs.isEmpty() ? "(aucun)" : ""));
            for (Arc a : arcs) {
                l.add("  " + (callers ? a.caller() : a.callee()) + weight(a));
            }
        }
        return l;
    }

    private static String weight(Arc a) {
        return a.weight() > 1 ? "  x" + a.weight() : "";
    }
}
