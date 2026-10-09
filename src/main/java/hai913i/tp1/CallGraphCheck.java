package hai913i.tp1;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import hai913i.tp1.extract.FactExtractor;
import hai913i.tp1.graph.CallGraph;
import hai913i.tp1.graph.CallGraph.Arc;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.ProjectSources;

/**
 * Vérification manuelle de B3 : statistiques, arcs, et requêtes appelants / appelés.
 * Usage : CallGraphCheck CHEMIN_DU_PROJET [--callers M | --callees M | --dot]
 * où M est un identifiant complet ou "Type#methode".
 */
public final class CallGraphCheck {

    private CallGraphCheck() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage : CallGraphCheck CHEMIN_DU_PROJET [--callers M | --callees M | --dot]");
            System.exit(2);
        }
        ProjectSources sources = ProjectSources.of(Path.of(args[0]));
        ProjectFacts facts = FactExtractor.extract(JdtParser.parse(sources, List.of()));
        CallGraph graph = CallGraph.build(facts);

        if (args.length >= 2 && args[1].equals("--dot")) {
            System.out.print(graph.toDot());
            return;
        }
        if (args.length >= 3 && (args[1].equals("--callers") || args[1].equals("--callees"))) {
            query(graph, args[1].equals("--callers"), args[2]);
            return;
        }

        System.out.println("Noeuds (methodes)       : " + graph.nodeCount());
        System.out.println("Arcs                    : " + graph.arcCount());
        System.out.println("Sites d'appel (total)   : " + graph.callSiteCount());
        System.out.println("  internes              : " + graph.internalCallSites());
        System.out.println("  externes              : " + graph.externalCallSites());
        System.out.println("  non resolus           : " + graph.unresolvedCallSites());
        System.out.println("  internes sans noeud   : " + graph.danglingCallSites());
        System.out.println("Arcs :");
        for (Arc a : graph.arcs()) {
            System.out.println("  " + a.caller() + " -> " + a.callee() + (a.weight() > 1 ? "  x" + a.weight() : ""));
        }
    }

    private static void query(CallGraph graph, boolean callers, String name) {
        List<String> matches = graph.find(name);
        if (matches.isEmpty()) {
            System.err.println("Methode inconnue : " + name);
            System.exit(1);
        }
        for (String id : matches) {
            List<Arc> arcs = callers ? graph.callers(id) : graph.callees(id);
            System.out.println((callers ? "Appelants de " : "Appeles par ") + id + " : "
                    + (arcs.isEmpty() ? "(aucun)" : ""));
            for (Arc a : arcs) {
                System.out.println("  " + (callers ? a.caller() : a.callee()) + (a.weight() > 1 ? "  x" + a.weight() : ""));
            }
        }
    }
}
