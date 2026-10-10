package hai913i.tp1.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
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
 *
 * Option CHA (bonus C3) : avec {@code build(facts, true)}, un appel interne vers la méthode m d'un type T
 * ajoute aussi un arc vers chaque redéfinition de m (même nom, mêmes paramètres) déclarée dans un sous-type
 * connu de T. Approximation de la « Class Hierarchy Analysis » : sans l'option, le graphe reste celui du sujet.
 * Un appel dont la redéfinition est l'appelant lui-même (typiquement super.m()) n'ajoute pas de boucle.
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
    private int chaSites;
    private boolean cha;

    private CallGraph() {
    }

    public static CallGraph build(ProjectFacts facts) {
        return build(facts, false);
    }

    /** @param cha true pour ajouter les arcs vers les redéfinitions connues (appels virtuels, bonus C3). */
    public static CallGraph build(ProjectFacts facts, boolean cha) {
        CallGraph graph = new CallGraph();
        graph.cha = cha;
        Hierarchy hierarchy = cha ? new Hierarchy(facts) : null;
        for (TypeFact type : facts.types()) {
            for (MethodFact method : type.methods()) {
                graph.nodes.add(method.id());
            }
        }
        for (TypeFact type : facts.types()) {
            for (MethodFact method : type.methods()) {
                for (CallFact call : method.calls()) {
                    graph.add(call, hierarchy);
                }
            }
        }
        return graph;
    }

    private void add(CallFact call, Hierarchy hierarchy) {
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
        link(call.callerId(), target);
        if (hierarchy != null) {
            for (String override : hierarchy.overriders(target)) {
                if (override.equals(call.callerId())) {
                    continue; // super.m() depuis la redéfinition : appel statique, pas une récursion
                }
                link(call.callerId(), override);
                chaSites++;
            }
        }
    }

    private void link(String caller, String callee) {
        out.computeIfAbsent(caller, k -> new TreeMap<>()).merge(callee, 1, Integer::sum);
        in.computeIfAbsent(callee, k -> new TreeMap<>()).merge(caller, 1, Integer::sum);
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

    /** Vrai si le graphe a été construit avec l'option CHA. */
    public boolean chaEnabled() {
        return cha;
    }

    /** Liens ajoutés par CHA (un par site d'appel et par redéfinition) ; 0 sans l'option. */
    public int chaCallSites() {
        return chaSites;
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

    /** Un nœud, ses appelants et ses appelés directs (pour un dessin centré sur une méthode). */
    public Set<String> neighbourhood(String methodId) {
        requireNode(methodId);
        Set<String> result = new TreeSet<>();
        result.add(methodId);
        callers(methodId).forEach(a -> result.add(a.caller()));
        callees(methodId).forEach(a -> result.add(a.callee()));
        return result;
    }

    /** Les nœuds dont le type est dans ce paquetage (sous-paquetages compris). */
    public Set<String> inPackage(String packageName) {
        Set<String> result = new TreeSet<>();
        for (String id : nodes) {
            int hash = id.indexOf('#');
            String type = hash < 0 ? id : id.substring(0, hash);
            if (type.startsWith(packageName + ".")) {
                result.add(id);
            }
        }
        return result;
    }

    /** DOT restreint à un ensemble de nœuds : seuls les arcs dont les deux extrémités y figurent sont dessinés. */
    public String toDot(Set<String> keep) {
        StringBuilder sb = new StringBuilder("digraph calls {\n  rankdir=LR;\n  node [shape=box,fontsize=10];\n");
        for (String id : new TreeSet<>(keep)) {
            if (nodes.contains(id)) {
                sb.append("  \"").append(id).append("\";\n");
            }
        }
        for (Arc a : arcs()) {
            if (keep.contains(a.caller()) && keep.contains(a.callee())) {
                appendArc(sb, a);
            }
        }
        return sb.append("}\n").toString();
    }

    private static void appendArc(StringBuilder sb, Arc a) {
        sb.append("  \"").append(a.caller()).append("\" -> \"").append(a.callee()).append('"');
        if (a.weight() > 1) {
            sb.append(" [label=\"").append(a.weight()).append("\"]");
        }
        sb.append(";\n");
    }

    /** Format Graphviz DOT ; le poids n'est affiché que s'il dépasse 1. */
    public String toDot() {
        StringBuilder sb = new StringBuilder("digraph calls {\n  rankdir=LR;\n  node [shape=box,fontsize=10];\n");
        for (Arc a : arcs()) {
            appendArc(sb, a);
        }
        return sb.append("}\n").toString();
    }

    /**
     * Hiérarchie des types internes, pour CHA : retrouve les redéfinitions d'une méthode.
     * Une redéfinition = même nom et mêmes paramètres (partie de l'identifiant après '#') dans un sous-type.
     * Limites : une méthode statique ou privée de même signature est prise pour une redéfinition ; des paramètres
     * génériques effacés différemment (T contre String) ne sont pas reconnus.
     */
    private static final class Hierarchy {
        private final Map<String, TypeFact> types = new LinkedHashMap<>();
        private final Set<String> methodIds = new HashSet<>();
        private final Map<String, Set<String>> closures = new HashMap<>();

        Hierarchy(ProjectFacts facts) {
            for (TypeFact type : facts.types()) {
                if (type.qualifiedName().isEmpty()) {
                    continue; // classe anonyme ou locale : jamais désignée par un autre type
                }
                types.putIfAbsent(type.qualifiedName(), type);
                for (MethodFact method : type.methods()) {
                    if (!method.constructor()) {
                        methodIds.add(method.id());
                    }
                }
            }
        }

        List<String> overriders(String targetId) {
            int hash = targetId.indexOf('#');
            if (hash < 0 || !methodIds.contains(targetId)) {
                return List.of();
            }
            String declaring = targetId.substring(0, hash);
            String signature = targetId.substring(hash);
            List<String> result = new ArrayList<>();
            for (String type : new TreeSet<>(types.keySet())) {
                String candidate = type + signature;
                if (!type.equals(declaring) && methodIds.contains(candidate)
                        && superTypes(type, new HashSet<>()).contains(declaring)) {
                    result.add(candidate);
                }
            }
            return result;
        }

        /** Tous les super-types (classes et interfaces, directs ou non) d'un type, jusqu'aux types internes. */
        private Set<String> superTypes(String type, Set<String> visiting) {
            Set<String> known = closures.get(type);
            if (known != null) {
                return known;
            }
            Set<String> result = new HashSet<>();
            TypeFact fact = types.get(type);
            if (fact != null && visiting.add(type)) {
                List<String> direct = new ArrayList<>(fact.superclasses());
                direct.addAll(fact.interfaces());
                for (String parent : direct) {
                    result.add(parent);
                    result.addAll(superTypes(parent, visiting));
                }
                visiting.remove(type);
                closures.put(type, result);
            }
            return result;
        }
    }
}
