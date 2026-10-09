package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.FieldFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;

/** Invariants qui doivent valoir pour TOUT projet analyse, y compris casse (faibles) ou valide (stricts). */
public final class FactsInvariants {

    private FactsInvariants() {
    }

    /** Vrai meme sur du code incorrect : formes, tri, coherence interne. */
    public static void checkWeak(ProjectFacts f) {
        assertNotNull(f);
        assertNotNull(f.types());
        assertNotNull(f.sourceFiles());
        assertTrue(f.totalLines() >= 0);
        assertEquals(f.sourceFiles().stream().sorted().toList(), f.sourceFiles(), "fichiers tries");
        assertEquals(f.types().stream().map(TypeFact::qualifiedName).sorted().toList(),
                f.types().stream().map(TypeFact::qualifiedName).toList(), "types tries");
        for (TypeFact t : f.types()) {
            assertNotNull(t.qualifiedName());
            assertFalse(t.qualifiedName().isBlank());
            assertNotNull(t.packageName());
            assertNotNull(t.kind());
            assertEquals(t.fields().stream().map(FieldFact::name).sorted().toList(),
                    t.fields().stream().map(FieldFact::name).toList(), "champs tries dans " + t.qualifiedName());
            assertEquals(t.methods().stream().map(MethodFact::id).sorted().toList(),
                    t.methods().stream().map(MethodFact::id).toList(), "methodes triees dans " + t.qualifiedName());
            for (FieldFact fld : t.fields()) {
                assertNotNull(fld.visibility());
                assertNotNull(fld.declaredType());
            }
            for (MethodFact m : t.methods()) {
                assertNotNull(m.id());
                assertNotNull(m.name());
                assertTrue(m.bodyLoc() >= 0, m.id());
                int previous = Integer.MIN_VALUE;
                for (CallFact c : m.calls()) {
                    assertEquals(m.id(), c.callerId(), "appel range sous le mauvais appelant");
                    assertTrue(c.sourceLine() >= 1, m.id());
                    assertTrue(c.sourceLine() >= previous, "appels tries par ligne dans " + m.id());
                    previous = c.sourceLine();
                    assertNotNull(c.staticReceiverType());
                    assertFalse(c.staticReceiverType().isBlank(), m.id());
                    if (c.internal()) {
                        assertTrue(c.resolved(), "un appel interne est resolu : " + c);
                        assertTrue(c.targetId().isPresent(), "un appel interne a une cible : " + c);
                    }
                }
            }
        }
    }

    /** Pour du code valide : identifiants uniques, coherents avec les types, internal <=> proprietaire dans le projet. */
    public static void checkStrict(ProjectFacts f) {
        checkWeak(f);
        Set<String> typeNames = new HashSet<>();
        Set<String> methodIds = new HashSet<>();
        for (TypeFact t : f.types()) {
            assertTrue(typeNames.add(t.qualifiedName()), "type en double : " + t.qualifiedName());
        }
        for (TypeFact t : f.types()) {
            for (MethodFact m : t.methods()) {
                assertTrue(methodIds.add(m.id()), "identifiant de methode en double : " + m.id());
                assertTrue(m.id().startsWith(t.qualifiedName() + "#"), m.id() + " vs " + t.qualifiedName());
            }
        }
        for (TypeFact t : f.types()) {
            for (MethodFact m : t.methods()) {
                for (CallFact c : m.calls()) {
                    if (c.resolved() && c.targetId().isPresent()) {
                        String target = c.targetId().get();
                        String owner = target.substring(0, target.indexOf('#'));
                        assertEquals(typeNames.contains(owner), c.internal(), "internal incoherent : " + c);
                    }
                }
            }
        }
    }

    public static List<CallFact> calls(ProjectFacts f) {
        return TestSupport.allCalls(f);
    }
}
