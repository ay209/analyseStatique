package hai913i.tp1.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import hai913i.tp1.TestSupport;
import hai913i.tp1.graph.CallGraph.Arc;
import hai913i.tp1.model.ProjectFacts;

/** B3 : points de controle du sujet sur le projet de validation. */
class CallGraphValidationTest {

    private static  ProjectFacts F;
    private static  CallGraph G;
     @BeforeAll
    static void load() throws IOException {
        F = TestSupport.validationFacts();
        G = CallGraph.build(F);
    }
    @Test
    void totals() {
        assertEquals(59, G.nodeCount());
        assertEquals(94, G.callSiteCount());
        assertEquals(46, G.internalCallSites());
        assertEquals(48, G.externalCallSites());
        assertEquals(0, G.unresolvedCallSites());
        assertEquals(0, G.danglingCallSites());
        assertEquals(44, G.arcCount(), "46 sites internes, fusionnes en 44 arcs ponderes");
        assertEquals(46, G.arcs().stream().mapToInt(Arc::weight).sum(), "la somme des poids = les sites internes");
    }

    @Test
    void overloadedAddIsAWeightedArc() {
        Arc arc = G.callees("library.service.Catalog#add(library.model.Item,library.model.Item)").stream()
                .filter(a -> a.callee().equals("library.service.Catalog#add(library.model.Item)"))
                .findFirst().orElseThrow();
        assertEquals(2, arc.weight());
    }

    @Test
    void recursionIsASelfLoop() {
        String repeat = G.find("TextUtils#repeat").get(0);
        assertTrue(G.callees(repeat).stream().anyMatch(a -> a.callee().equals(repeat)));
    }

    @Test
    void onlyBorrowCallsLoanableCheckOut() {
        List<Arc> callers = G.callers(G.find("Loanable#checkOut").get(0));
        assertEquals(1, callers.size());
        assertTrue(callers.get(0).caller().contains("LoanService#borrow("));
    }

    @Test
    void itemCheckOutHasNoCaller() {
        assertTrue(G.callers(G.find("Item#checkOut").get(0)).isEmpty());
    }

    @Test
    void graphIsRepeatable() {
        assertEquals(G.arcs(), CallGraph.build(F).arcs());
        assertEquals(G.toDot(), CallGraph.build(F).toDot());
    }
}
