package hai913i.tp1.spoon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import hai913i.tp1.TestSupport;
import hai913i.tp1.compare.Comparison;
import hai913i.tp1.compare.RawCounts;
import hai913i.tp1.compare.SpoonResult;
import hai913i.tp1.graph.CallGraph;
import hai913i.tp1.model.ProjectFacts;

/**
 * Bonus C1 sur le projet de validation : les chiffres BRUTS de Spoon 11.5.0 donnes par l'annexe JDT/Spoon (§2.4),
 * puis les memes chiffres une fois nos conventions appliquees, qui doivent retrouver ceux de JDT.
 */
class SpoonValidationTest {

    private static SpoonResult spoon;
    private static ProjectFacts jdt;

    @BeforeAll
    static void load() throws Exception {
        jdt = TestSupport.validationFacts();
        spoon = SpoonExtractor.extract(TestSupport.validationRoot());
    }

    @Test
    void rawCountsMatchTheAnnex() {
        RawCounts raw = spoon.raw();
        assertEquals(14, raw.topLevelTypes());
        assertEquals(15, raw.allTypes());
        assertEquals(50, raw.methods());
        assertEquals(13, raw.constructors());
        assertEquals(4, raw.implicitConstructors());
        assertEquals(24, raw.fields());
        assertEquals(3, raw.enumConstants());
        assertEquals(6, raw.packages());
        assertEquals(107, raw.invocations());
        assertEquals(13, raw.constructorInvocations());
    }

    @Test
    void afterTheConventionsSpoonFindsTheSameStructureAsJdt() {
        ProjectFacts converted = new ProjectFacts(spoon.types(), jdt.sourceFiles(), jdt.totalLines());
        assertEquals(15, converted.types().size());
        assertEquals(59, converted.types().stream().mapToInt(t -> t.methods().size()).sum());
        assertEquals(21, converted.types().stream().mapToInt(t -> t.fields().size()).sum());
        assertEquals(4, converted.types().stream().map(t -> t.packageName()).distinct().count());
    }

    @Test
    void afterTheConventionsTheCallGraphIsTheSameAsJdt() {
        ProjectFacts converted = new ProjectFacts(spoon.types(), jdt.sourceFiles(), jdt.totalLines());
        CallGraph a = CallGraph.build(jdt);
        CallGraph b = CallGraph.build(converted);
        assertEquals(59, b.nodeCount());
        assertEquals(44, b.arcCount());
        assertEquals(46, b.internalCallSites());
        assertEquals(a.nodes(), b.nodes());
        assertEquals(a.arcs(), b.arcs());
    }

    @Test
    void theReportListsTheDifferences() {
        List<String> report = Comparison.report(jdt, spoon);
        assertTrue(report.stream().anyMatch(l -> l.startsWith("Types")), report.toString());
        assertTrue(report.stream().anyMatch(l -> l.contains("107")), report.toString());
    }
}
