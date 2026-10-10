package hai913i.tp1.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.FieldFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.model.TypeKind;
import hai913i.tp1.model.Visibility;

/** Bonus C1 : la comparaison JDT / Spoon sur des faits construits a la main (ni JDT ni Spoon ici). */
class ComparisonTest {

    private static final RawCounts RAW = new RawCounts(1, 2, 3, 4, 1, 5, 2, 3, 6, 2);

    private static CallFact call(String from, String to) {
        return new CallFact(from, Optional.of(to), "p.A", 1, true, true);
    }

    private static MethodFact method(String id, CallFact... calls) {
        return new MethodFact(id, "m", List.of(), false, 1, List.of(calls));
    }

    private static TypeFact type(String name, String pkg, List<String> superclasses, List<FieldFact> fields,
            MethodFact... methods) {
        return new TypeFact(name, pkg, TypeKind.CLASS, superclasses, List.of(), fields, List.of(methods));
    }

    private static ProjectFacts facts(TypeFact... types) {
        return new ProjectFacts(List.of(types), List.<Path>of(), 10);
    }

    private static FieldFact field(String name) {
        return new FieldFact(name, "int", Visibility.PRIVATE);
    }

    private static ProjectFacts sample() {
        return facts(
                type("p.A", "p", List.of(), List.of(field("x"), field("y")),
                        method("p.A#a()", call("p.A#a()", "p.B#b()")), method("p.A#c()")),
                type("q.B", "q", List.of("p.A"), List.of(), method("p.B#b()")));
    }

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }

    @Test
    void identicalFactsGiveIdenticalGraphs() {
        List<String> report = Comparison.report(sample(), new SpoonResult(RAW, sample().types()));
        assertTrue(joined(report).contains("Les deux graphes sont identiques"), joined(report));
    }

    @Test
    void theTableShowsJdtRawSpoonAndConvertedSpoon() {
        String text = joined(Comparison.report(sample(), new SpoonResult(RAW, sample().types())));
        assertTrue(text.contains("JDT (convention TP)"), text);
        assertTrue(text.contains("1 (1er niveau) / 2 (tous)"), text);
        assertTrue(text.contains("3 methodes + 4 constructeurs (1 implicites)"), text);
        assertTrue(text.contains("5 (dont 2 constantes d'enum)"), text);
        assertTrue(text.contains("6 (dont 2 appels de constructeur)"), text);
    }

    @Test
    void countsAreTakenFromTheFacts() {
        ProjectFacts f = sample();
        assertEquals(3, Comparison.methods(f));
        assertEquals(2, Comparison.fields(f));
        assertEquals(2, Comparison.packages(f));
        assertEquals(1, Comparison.calls(f));
        assertEquals(1, Comparison.superclasses(f));
        assertEquals(0, Comparison.interfaces(f));
    }

    @Test
    void anExtraArcOnTheSpoonSideIsReported() {
        ProjectFacts spoon = facts(
                type("p.A", "p", List.of(), List.of(field("x"), field("y")),
                        method("p.A#a()", call("p.A#a()", "p.B#b()")),
                        method("p.A#c()", call("p.A#c()", "p.A#a()"))),
                type("q.B", "q", List.of("p.A"), List.of(), method("p.B#b()")));
        String text = joined(Comparison.report(sample(), new SpoonResult(RAW, spoon.types())));
        assertTrue(text.contains("Arcs seulement chez Spoon (1)"), text);
        assertTrue(text.contains("p.A#c() -> p.A#a() x1"), text);
        assertTrue(!text.contains("Les deux graphes sont identiques"), text);
    }

    @Test
    void aNodeMissingOnOneSideIsReported() {
        ProjectFacts spoon = facts(type("p.A", "p", List.of(), List.of(field("x"), field("y")),
                method("p.A#a()"), method("p.A#c()")));
        String text = joined(Comparison.report(sample(), new SpoonResult(RAW, spoon.types())));
        assertTrue(text.contains("Noeuds seulement chez JDT (1)"), text);
        assertTrue(text.contains("p.B#b()"), text);
    }

    @Test
    void longDifferenceListsAreTruncated() {
        MethodFact[] many = new MethodFact[15];
        for (int i = 0; i < many.length; i++) {
            many[i] = method("p.A#m" + (i < 10 ? "0" : "") + i + "()");
        }
        ProjectFacts spoon = facts(type("p.A", "p", List.of(), List.of(), many));
        String text = joined(Comparison.report(facts(), new SpoonResult(RAW, spoon.types())));
        assertTrue(text.contains("Noeuds seulement chez Spoon (15)"), text);
        assertTrue(text.contains("... et 5 autre(s)."), text);
    }

    @Test
    void emptyProjectsCompareWithoutFailing() {
        String text = joined(Comparison.report(facts(), new SpoonResult(RAW, List.of())));
        assertTrue(text.contains("Les deux graphes sont identiques"), text);
    }
}
