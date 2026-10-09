package hai913i.tp1.extract;

import static hai913i.tp1.TestSupport.method;
import static hai913i.tp1.TestSupport.type;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import hai913i.tp1.TestSupport;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.parse.JdtParser.ParsedFile;

/** A3 : CallVisitor sur le projet de validation (94 appels : 46 internes, 48 externes, 0 non resolus). */
class CallVisitorValidationTest {

    private static ProjectFacts facts;
    private static Map<String, List<CallFact>> raw;
    private static Set<String> allMethodIds;

    @BeforeAll
    static void run() throws IOException {
        facts = TestSupport.validationFacts();
        StructureVisitor sv = new StructureVisitor();
        CallVisitor cv = new CallVisitor();
        for (ParsedFile f : TestSupport.validationParsed()) {
            f.unit().accept(sv);
            f.unit().accept(cv);
        }
        raw = cv.callsByMethod();
        allMethodIds = sv.types().stream().flatMap(t -> t.methods().stream()).map(MethodFact::id)
                .collect(Collectors.toSet());
    }

    private static CallFact only(List<CallFact> calls, int line, String targetPrefix) {
        List<CallFact> m = calls.stream()
                .filter(c -> c.sourceLine() == line && c.targetId().orElse("").startsWith(targetPrefix)).toList();
        assertEquals(1, m.size(), "ligne " + line + " cible " + targetPrefix + " parmi " + calls);
        return m.get(0);
    }

    @Test
    void ninetyFourCallsSplitInternalExternal() {
        List<CallFact> all = TestSupport.allCalls(facts);
        assertEquals(94, all.size());
        assertEquals(46, all.stream().filter(CallFact::internal).count());
        assertEquals(48, all.stream().filter(c -> !c.internal()).count());
        assertEquals(0, all.stream().filter(c -> !c.resolved()).count());
        assertTrue(all.stream().allMatch(c -> c.targetId().isPresent()));
    }

    @Test
    void visitorMapAndModelAgreeAndNothingIsOrphaned() {
        assertEquals(94, raw.values().stream().mapToInt(List::size).sum());
        assertTrue(allMethodIds.containsAll(raw.keySet()),
                "des appels sont attribues a un id qui n'est celui d'aucune methode : "
                        + raw.keySet().stream().filter(k -> !allMethodIds.contains(k)).toList());
    }

    @Test
    void visitorMapKeysAreSorted() {
        List<String> keys = new ArrayList<>(raw.keySet());
        assertEquals(keys.stream().sorted().toList(), keys);
    }

    @Test
    void everyCallerIdMatchesTheMethodItIsStoredIn() {
        for (TypeFact t : facts.types()) {
            for (MethodFact m : t.methods()) {
                for (CallFact c : m.calls()) {
                    assertEquals(m.id(), c.callerId());
                }
            }
        }
    }

    @Test
    void callsAreSortedByLine() {
        for (MethodFact m : TestSupport.allMethods(facts)) {
            List<Integer> lines = m.calls().stream().map(CallFact::sourceLine).toList();
            assertEquals(lines.stream().sorted().toList(), lines, m.id());
        }
    }

    @Test
    void sourceLinesArePositive() {
        assertTrue(TestSupport.allCalls(facts).stream().allMatch(c -> c.sourceLine() >= 1));
    }

    /** internal <=> le type proprietaire de la methode appelee fait partie du projet. */
    @Test
    void internalMeansOwnerIsAProjectType() {
        Set<String> projectTypes = facts.types().stream().map(TypeFact::qualifiedName).collect(Collectors.toSet());
        for (CallFact c : TestSupport.allCalls(facts)) {
            String target = c.targetId().orElseThrow();
            String owner = target.substring(0, target.indexOf('#'));
            assertEquals(projectTypes.contains(owner), c.internal(), target);
        }
    }

    @Test
    void targetIdsFollowTheIdFormat() {
        for (CallFact c : TestSupport.allCalls(facts)) {
            String t = c.targetId().orElseThrow();
            assertTrue(t.matches("[^#]+#[^(]+\\([^)]*\\)"), t);
            assertFalse(t.contains("<"), "types erases : " + t);
            assertFalse(c.staticReceiverType().isBlank());
            assertFalse(c.staticReceiverType().contains("<"), c.staticReceiverType());
        }
    }

    @Test
    void borrowCallsCheckOutOnTheInterface() {
        List<CallFact> calls = TestSupport.calls(facts, "LoanService", "borrow");
        CallFact c = only(calls, 36, "library.model.Loanable#checkOut");
        assertEquals("library.model.Loanable", c.staticReceiverType());
        assertEquals("library.model.Loanable#checkOut(library.model.Member)", c.targetId().orElseThrow());
        assertTrue(c.internal());
        assertTrue(c.resolved());
        assertEquals(method(type(facts, "LoanService"), "borrow").id(), c.callerId());
    }

    @Test
    void borrowOrderIsFindByIdThenOrElseThrow() {
        List<CallFact> calls = TestSupport.calls(facts, "LoanService", "borrow");
        CallFact find = only(calls, 33, "library.service.Catalog#findById");
        CallFact orElse = only(calls, 34, "java.util.Optional#orElseThrow");
        assertTrue(calls.indexOf(find) < calls.indexOf(orElse), "tri par ligne");
        assertFalse(orElse.internal());
    }

    @Test
    void mainCallsGetTitleOnBookResolvedToItem() {
        List<CallFact> calls = TestSupport.calls(facts, "LibraryApp", "main");
        CallFact c = only(calls, 32, "library.model.Item#getTitle");
        assertEquals("library.model.Book", c.staticReceiverType());
        assertEquals("library.model.Item#getTitle()", c.targetId().orElseThrow());
        assertTrue(c.internal());
    }

    @Test
    void mainCallsTheTwoCatalogAddOverloads() {
        List<CallFact> calls = TestSupport.calls(facts, "LibraryApp", "main");
        assertEquals("library.service.Catalog#add(library.model.Item,library.model.Item)",
                only(calls, 24, "library.service.Catalog#add").targetId().orElseThrow());
        assertEquals("library.service.Catalog#add(library.model.Item)",
                only(calls, 25, "library.service.Catalog#add").targetId().orElseThrow());
    }

    @Test
    void superCallResolvesToTheParentWithParentAsReceiver() {
        List<CallFact> calls = TestSupport.calls(facts, "Book", "describe");
        CallFact c = only(calls, 32, "library.model.Item#describe");
        assertEquals("library.model.Item", c.staticReceiverType());
        assertEquals("library.model.Item#describe()", c.targetId().orElseThrow());
        assertTrue(c.internal());
    }

    @Test
    void lambdaBodyIsAttributedToTheEnclosingMethod() {
        List<CallFact> calls = TestSupport.calls(facts, "Catalog", "available");
        CallFact forEach = only(calls, 36, "java.lang.Iterable#forEach");
        assertEquals("java.util.List", forEach.staticReceiverType());
        assertEquals("java.lang.Iterable#forEach(java.util.function.Consumer)", forEach.targetId().orElseThrow());
        assertFalse(forEach.internal());
        CallFact avail = only(calls, 37, "library.model.Item#isAvailable");
        assertEquals("library.model.Item", avail.staticReceiverType());
        assertTrue(avail.internal());
        CallFact add = only(calls, 38, "java.util.List#add");
        assertEquals("java.util.List#add(java.lang.Object)", add.targetId().orElseThrow());
        assertEquals("java.util.List", add.staticReceiverType());
        assertFalse(add.internal());
    }

    @Test
    void recursionIsASelfCall() {
        MethodFact repeat = method(type(facts, "TextUtils"), "repeat");
        assertTrue(repeat.calls().stream().anyMatch(c -> c.targetId().orElse("").equals(repeat.id())),
                "repeat() s'appelle elle-meme : " + repeat.calls());
    }

    @Test
    void checkOutOnItemIsNeverCalledDirectly() {
        String itemCheckOut = method(type(facts, "Item"), "checkOut").id();
        assertTrue(TestSupport.allCalls(facts).stream().noneMatch(c -> c.targetId().orElse("").equals(itemCheckOut)));
    }

    @Test
    void onlyBorrowCallsLoanableCheckOut() {
        List<String> callers = TestSupport.allCalls(facts).stream()
                .filter(c -> c.targetId().orElse("").startsWith("library.model.Loanable#checkOut"))
                .map(CallFact::callerId).distinct().toList();
        assertEquals(1, callers.size());
        assertTrue(callers.get(0).startsWith("library.service.LoanService#borrow("), callers.toString());
    }

    @Test
    void abstractMethodsHaveNoCalls() {
        for (MethodFact m : TestSupport.allMethods(facts)) {
            if (m.bodyLoc() == 0) {
                assertTrue(m.calls().isEmpty(), m.id());
            }
        }
    }

    @Test
    void callsAreComparableWithoutFailureEvenWithEmptyTargets() {
        CallFact a = new CallFact("c", Optional.empty(), "r", 1, false, false);
        CallFact b = new CallFact("c", Optional.of("t"), "r", 1, true, true);
        List<CallFact> s = List.of(b, a).stream()
                .sorted(Comparator.comparingInt(CallFact::sourceLine).thenComparing(x -> x.targetId().orElse("")))
                .toList();
        assertEquals(List.of(a, b), s);
    }
}
