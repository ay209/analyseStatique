package hai913i.tp1.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;

/**
 * B3 : graphe d'appel pondéré, construit à partir du seul modèle de faits (aucune dépendance à JDT).
 *
 * - Nœuds : toutes les méthodes du modèle (constructeurs compris), même celles qui n'appellent
 *   rien et que personne n'appelle.
 * - Arcs : (appelant -> appelé), pondérés par le nombre de sites d'appel. Seuls les appels
 *   RÉSOLUS et INTERNES dont la cible est un nœud du graphe produisent un arc.
 * - Les appels vers l'extérieur (JDK, bibliothèques) et les appels non résolus ne sont pas des arcs,
 *   mais ils sont comptés à part pour que rien ne disparaisse silencieusement.
 * - Les boucles (récursion) sont des arcs comme les autres.
 * - Tout est trié : le graphe est identique d'une exécution à l'autre.
 */
public final class CallGraph {

    /** Un arc du graphe : l'appelant appelle l'appelé {@code weight} fois. */
    public record Arc(String caller, String callee, int weight) {
    }

    private final NavigableSet<String> nodes = new TreeSet<>();
    private final NavigableMap<String, NavigableMap<String, Integer>> out = new TreeMap<>();
    private final NavigableMap<String, NavigableMap<String, Integer>> in = new TreeMap<>();

    private int internalSites;
    private int externalSites;
    private int unresolvedSites;
    private int danglingSites;

    private CallGraph() {
    }

    public static CallGraph build(ProjectFacts facts) {
        CallGraph graph = new CallGraph();
        for (TypeFact type : facts.types()) {
            for (MethodFact method : type.methods()) {
                graph.nodes.add(method.id());
            }
        }
        for (TypeFact type : facts.types()) {
            for (MethodFact method : type.methods()) {
                for (CallFact call : method.calls()) {
                    graph.add(call);
                }
            }
        }
        return graph;
    }

    private void add(CallFact call) {
        if (!call.resolved() || call.targetId().isEmpty()) {
            unresolvedSites++;
            return;
        }
        if (!call.internal()) {
            externalSites++;
            return;
        }
        String target = call.targetId().get();
        if (!nodes.contains(call.callerId()) || !nodes.contains(target)) {
            // appel interne dont une extrémité n'est pas un nœud : conservé dans les statistiques, sans arc
            danglingSites++;
            internalSites++;
            return;
        }
        internalSites++;
        out.computeIfAbsent(call.callerId(), k -> new TreeMap<>()).merge(target, 1, Integer::sum);
        in.computeIfAbsent(target, k -> new TreeMap<>()).merge(call.callerId(), 1, Integer::sum);
    }

    // ---------- tailles ----------

    public int nodeCount() {
        return nodes.size();
    }

    public int arcCount() {
        int total = 0;
        for (Map<String, Integer> targets : out.values()) {
            total += targets.size();
        }
        return total;
    }

    /** Tous les sites d'appel du modèle : internes + externes + non résolus. */
    public int callSiteCount() {
        return internalSites + externalSites + unresolvedSites;
    }

    /** Sites d'appel internes (ceux qui deviennent des arcs, avant fusion des doublons). */
    public int internalCallSites() {
        return internalSites;
    }

    public int externalCallSites() {
        return externalSites;
    }

    public int unresolvedCallSites() {
        return unresolvedSites;
    }

    /** Sites internes dont une extrémité n'est pas un nœud (devrait valoir 0 sur un projet sain). */
    public int danglingCallSites() {
        return danglingSites;
    }

    // ---------- accès ----------

    public boolean contains(String methodId) {
        return nodes.contains(methodId);
    }

    /** Les nœuds, triés par identifiant. */
    public List<String> nodes() {
        return List.copyOf(nodes);
    }

    /** Les arcs, triés par appelant puis appelé. */
    public List<Arc> arcs() {
        List<Arc> result = new ArrayList<>();
        for (Map.Entry<String, NavigableMap<String, Integer>> from : out.entrySet()) {
            for (Map.Entry<String, Integer> to : from.getValue().entrySet()) {
                result.add(new Arc(from.getKey(), to.getKey(), to.getValue()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Ce que cette méthode appelle (arcs sortants), trié par appelé. */
    public List<Arc> callees(String methodId) {
        requireNode(methodId);
        List<Arc> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : out.getOrDefault(methodId, new TreeMap<>()).entrySet()) {
            result.add(new Arc(methodId, e.getKey(), e.getValue()));
        }
        return Collections.unmodifiableList(result);
    }

    /** Qui appelle cette méthode (arcs entrants), trié par appelant. */
    public List<Arc> callers(String methodId) {
        requireNode(methodId);
        List<Arc> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : in.getOrDefault(methodId, new TreeMap<>()).entrySet()) {
            result.add(new Arc(e.getKey(), methodId, e.getValue()));
        }
        return Collections.unmodifiableList(result);
    }

    private void requireNode(String methodId) {
        if (!nodes.contains(methodId)) {
            throw new IllegalArgumentException("Methode inconnue : " + methodId);
        }
    }

    /**
     * Retrouve des nœuds à partir d'un nom saisi à la main :
     * l'identifiant exact, sinon "Type#nom" (sans les paramètres), avec ou sans paquetage.
     * Plusieurs résultats possibles (surcharges) : c'est à l'appelant de lever l'ambiguïté.
     */
    public List<String> find(String query) {
        if (nodes.contains(query)) {
            return List.of(query);
        }
        List<String> result = new ArrayList<>();
        for (String id : nodes) {
            int paren = id.indexOf('(');
            String withoutParams = paren < 0 ? id : id.substring(0, paren);
            if (withoutParams.equals(query) || withoutParams.endsWith("." + query)) {
                result.add(id);
            }
        }
        return Collections.unmodifiableList(result);
    }

    // ---------- export ----------

    /** Format Graphviz DOT ; le poids n'est affiché que s'il dépasse 1. */
    public String toDot() {
        StringBuilder sb = new StringBuilder("digraph calls {\n  rankdir=LR;\n  node [shape=box,fontsize=10];\n");
        for (Arc a : arcs()) {
            sb.append("  \"").append(a.caller()).append("\" -> \"").append(a.callee()).append('"');
            if (a.weight() > 1) {
                sb.append(" [label=\"").append(a.weight()).append("\"]");
            }
            sb.append(";\n");
        }
        return sb.append("}\n").toString();
    }
}
