package hai913i.tp1;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import hai913i.tp1.extract.FactExtractor;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.model.TypeKind;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.ProjectSources;

public class FactsCheck {

    public static void main(String[] args) throws IOException {
        ProjectSources sources = ProjectSources.of(Path.of(args[0]));
        ProjectFacts facts = FactExtractor.extract(JdtParser.parse(sources, List.of()));
        ProjectFacts again = FactExtractor.extract(JdtParser.parse(sources, List.of()));

        List<MethodFact> methods = facts.types().stream().flatMap(t -> t.methods().stream()).toList();
        List<CallFact> calls = methods.stream().flatMap(m -> m.calls().stream()).toList();

        System.out.println("Deux extractions identiques : " + facts.equals(again));
        System.out.println("Types : " + facts.types().size()
                + " (classes " + facts.types().stream().filter(t -> t.kind() == TypeKind.CLASS).count()
                + ", interfaces " + facts.types().stream().filter(t -> t.kind() == TypeKind.INTERFACE).count()
                + ", enums " + facts.types().stream().filter(t -> t.kind() == TypeKind.ENUM).count() + ")");
        System.out.println("Méthodes : " + methods.size()
                + " (constructeurs " + methods.stream().filter(MethodFact::constructor).count() + ")");
        System.out.println("Attributs : " + facts.types().stream().mapToInt(t -> t.fields().size()).sum());
        System.out.println("Paquetages : " + facts.types().stream().map(TypeFact::packageName).distinct().count());
        System.out.println("Lignes de l'application : " + facts.totalLines());
        System.out.println("Méthodes avec corps : " + methods.stream().filter(m -> m.bodyLoc() > 0).count()
                + ", total de lignes : " + methods.stream().mapToInt(MethodFact::bodyLoc).sum());
        System.out.println("Appels : " + calls.size()
                + " (internes " + calls.stream().filter(c -> c.resolved() && c.internal()).count()
                + ", externes " + calls.stream().filter(c -> c.resolved() && !c.internal()).count()
                + ", non résolus " + calls.stream().filter(c -> !c.resolved()).count() + ")");

        if (args.length > 1 && args[1].equals("--dump")) { // pour comparer deux exécutions avec diff
            for (TypeFact t : facts.types()) {
                for (MethodFact m : t.methods()) {
                    System.out.println(m.id() + " loc=" + m.bodyLoc());
                    for (CallFact c : m.calls()) {
                        System.out.println("  l." + c.sourceLine() + " " + c.staticReceiverType()
                                + " -> " + c.targetId().orElse("NON RÉSOLU"));
                    }
                }
            }
        }
    }
}