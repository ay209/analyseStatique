package hai913i.tp1.extract;

import static hai913i.tp1.TestSupport.type;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import hai913i.tp1.TestSupport;
import hai913i.tp1.model.FieldFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.ProjectFacts;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.model.TypeKind;
import hai913i.tp1.model.Visibility;
import hai913i.tp1.parse.JdtParser.ParsedFile;

/** A2 : StructureVisitor sur le projet de validation (points de controle du sujet). */
class StructureVisitorValidationTest {

    private static List<TypeFact> types;
    private static ProjectFacts facts;

    @BeforeAll
    static void run()throws IOException  {
        StructureVisitor sv = new StructureVisitor();
        for (ParsedFile f : TestSupport.validationParsed()) {
            f.unit().accept(sv);
        }
        types = sv.types();
        facts = TestSupport.validationFacts();
    }

    @Test
    void fifteenTypes() {
        assertEquals(15, types.size());
    }

    @Test
    void kindsBreakdown() {
        assertEquals(12, types.stream().filter(t -> t.kind() == TypeKind.CLASS).count());
        assertEquals(2, types.stream().filter(t -> t.kind() == TypeKind.INTERFACE).count());
        assertEquals(1, types.stream().filter(t -> t.kind() == TypeKind.ENUM).count());
        assertEquals(0, types.stream().filter(t -> t.kind() == TypeKind.RECORD).count());
    }

    @Test
    void expectedTypesArePresentByName() {
        Set<String> simple = types.stream()
                .map(t -> t.qualifiedName().substring(t.qualifiedName().lastIndexOf('.') + 1))
                .collect(Collectors.toSet());
        assertEquals(Set.of("LibraryApp", "Book", "Category", "Dvd", "Item", "Loanable", "Member", "Catalog",
                "ConsoleNotifier", "LoanException", "LoanService", "Loan", "Notifier", "Statistics", "TextUtils"),
                simple);
    }

    @Test
    void interfacesAndEnumAreIdentified() {
        assertEquals(TypeKind.INTERFACE, type(facts, "Loanable").kind());
        assertEquals(TypeKind.INTERFACE, type(facts, "Notifier").kind());
        assertEquals(TypeKind.ENUM, type(facts, "Category").kind());
        assertEquals(TypeKind.CLASS, type(facts, "Item").kind());
        assertEquals(TypeKind.CLASS, type(facts, "Loan").kind());
    }

    @Test
    void nestedTypeIsQualifiedByItsOuterType() {
        assertEquals("library.service.LoanService.Loan", type(facts, "Loan").qualifiedName());
        assertEquals("library.service", type(facts, "Loan").packageName());
    }

    @Test
    void fourPackages() {
        Set<String> packages = types.stream().map(TypeFact::packageName).collect(Collectors.toSet());
        assertEquals(Set.of("library.app", "library.model", "library.service", "library.util"), packages);
    }

    @Test
    void knownPackages() {
        assertEquals("library.app", type(facts, "LibraryApp").packageName());
        assertEquals("library.model", type(facts, "Item").packageName());
        assertEquals("library.model", type(facts, "Loanable").packageName());
        assertEquals("library.service", type(facts, "Catalog").packageName());
        assertEquals("library.util", type(facts, "TextUtils").packageName());
    }

    @Test
    void qualifiedNameIsPackagePlusName() {
        for (TypeFact t : types) {
            assertTrue(t.qualifiedName().startsWith(t.packageName() + "."), t.qualifiedName());
        }
    }

    @Test
    void totals59Methods9Constructors21Fields() {
        assertEquals(59, types.stream().mapToInt(t -> t.methods().size()).sum());
        assertEquals(9, types.stream().flatMap(t -> t.methods().stream()).filter(MethodFact::constructor).count());
        assertEquals(21, types.stream().mapToInt(t -> t.fields().size()).sum());
    }

    @Test
    void perTypeCountsFromTheCheckpoint() {
        assertEquals(10, type(facts, "Item").methods().size());
        assertEquals(5, type(facts, "Item").fields().size());
        assertEquals(6, type(facts, "Member").methods().size());
        assertEquals(6, type(facts, "LoanService").methods().size());
        assertEquals(5, type(facts, "LoanService").fields().size());
    }

    /** Les membres de Loan ne doivent pas etre comptes dans LoanService, et inversement. */
    @Test
    void nestedMembersDoNotLeakIntoTheOuterType() {
        TypeFact outer = type(facts, "LoanService");
        TypeFact loan = type(facts, "Loan");
        Set<String> outerIds = outer.methods().stream().map(MethodFact::id).collect(Collectors.toSet());
        Set<String> loanIds = loan.methods().stream().map(MethodFact::id).collect(Collectors.toSet());
        assertTrue(Set.copyOf(outerIds).stream().noneMatch(loanIds::contains));
        assertTrue(outer.methods().stream().noneMatch(m -> m.id().startsWith("library.service.LoanService.Loan#")));
        assertTrue(loan.methods().stream().allMatch(m -> m.id().startsWith("library.service.LoanService.Loan#")));
        assertEquals(4, loan.methods().size(), "Loan : methodes");
        assertEquals(3, loan.fields().size(), "Loan : attributs");
    }

    @Test
    void inheritanceInfo() {
        assertEquals(List.of("library.model.Item"), type(facts, "Book").superclasses());
        assertEquals(List.of("library.model.Item"), type(facts, "Dvd").superclasses());
        assertEquals(List.of("java.lang.Exception", "java.lang.Throwable"), type(facts, "LoanException").superclasses());
        assertTrue(type(facts, "Item").superclasses().isEmpty(), "java.lang.Object est exclu");
        assertTrue(type(facts, "TextUtils").superclasses().isEmpty());
        for (TypeFact t : types) {
            assertFalse(t.superclasses().contains("java.lang.Object"), t.qualifiedName());
        }
    }

    @Test
    void interfacesAreDirectOnes() {
        assertEquals(List.of("library.model.Loanable"), type(facts, "Item").interfaces());
        assertTrue(type(facts, "Book").interfaces().isEmpty(), "Book n'implemente pas directement Loanable");
        assertTrue(type(facts, "ConsoleNotifier").interfaces().stream().anyMatch(i -> i.endsWith(".Notifier")));
    }

    @Test
    void itemFieldVisibilities() {
        Set<Visibility> seen = type(facts, "Item").fields().stream().map(FieldFact::visibility)
                .collect(Collectors.toSet());
        assertTrue(seen.contains(Visibility.PACKAGE), "Item a un attribut sans modificateur : " + seen);
        assertTrue(seen.size() >= 2, seen.toString());
    }

    @Test
    void abstractAndInterfaceMethodsHaveNoBody() {
        TypeFact loanable = type(facts, "Loanable");
        assertFalse(loanable.methods().isEmpty());
        assertTrue(loanable.methods().stream().allMatch(m -> m.bodyLoc() == 0));
        assertTrue(type(facts, "Notifier").methods().stream().allMatch(m -> m.bodyLoc() == 0));
    }

    @Test
    void fiftyThreeBodiesTotalling246Lines() {
        List<MethodFact> all = TestSupport.allMethods(facts);
        assertEquals(53, all.stream().filter(m -> m.bodyLoc() > 0).count());
        assertEquals(246, all.stream().mapToInt(MethodFact::bodyLoc).sum());
        assertTrue(all.stream().allMatch(m -> m.bodyLoc() >= 0));
    }

    @Test
    void knownBodyLengths() {
        assertEquals(7, TestSupport.method(type(facts, "Item"), "checkOut").bodyLoc());
        assertEquals(14, TestSupport.method(type(facts, "LoanService"), "borrow").bodyLoc());
    }

    @Test
    void methodIdsAreUniqueAndWellFormed() {
        List<MethodFact> all = TestSupport.allMethods(facts);
        assertEquals(59, all.stream().map(MethodFact::id).distinct().count());
        for (TypeFact t : types) {
            for (MethodFact m : t.methods()) {
                assertTrue(m.id().startsWith(t.qualifiedName() + "#" + m.name() + "("), m.id());
                assertTrue(m.id().endsWith(")"), m.id());
                assertEquals(m.parameterTypes().size(), m.parameterTypes().stream().filter(p -> !p.isBlank()).count());
                String inside = m.id().substring(m.id().indexOf('(') + 1, m.id().length() - 1);
                assertEquals(String.join(",", m.parameterTypes()), inside, m.id());
            }
        }
    }

    @Test
    void catalogOverloadsAreDistinguished() {
        Set<String> ids = type(facts, "Catalog").methods().stream().map(MethodFact::id).collect(Collectors.toSet());
        assertTrue(ids.contains("library.service.Catalog#add(library.model.Item)"), ids.toString());
        assertTrue(ids.contains("library.service.Catalog#add(library.model.Item,library.model.Item)"), ids.toString());
        assertTrue(ids.contains("library.service.Catalog#available()"));
        assertTrue(ids.contains("library.service.Catalog#findById(java.lang.String)"));
        assertTrue(ids.contains("library.service.Catalog#size()"));
    }

    @Test
    void statisticsReportHasFiveParameters() {
        MethodFact report = TestSupport.method(type(facts, "Statistics"), "report");
        assertEquals(5, report.parameterTypes().size());
    }

    @Test
    void constructorsAreFlaggedAndNamedLikeTheirClass() {
        for (TypeFact t : types) {
            String simple = t.qualifiedName().substring(t.qualifiedName().lastIndexOf('.') + 1);
            for (MethodFact m : t.methods()) {
                assertEquals(m.name().equals(simple), m.constructor(), m.id());
            }
        }
    }

    @Test
    void structureVisitorAloneProducesNoCalls() {
        assertTrue(types.stream().flatMap(t -> t.methods().stream()).allMatch(m -> m.calls().isEmpty()));
    }

    @Test
    void listsAreSortedDeterministically() {
        for (TypeFact t : types) {
            assertEquals(t.fields().stream().sorted(Comparator.comparing(FieldFact::name)).toList(), t.fields());
            assertEquals(t.methods().stream().sorted(Comparator.comparing(MethodFact::id)).toList(), t.methods());
        }
        assertEquals(facts.types().stream().sorted(Comparator.comparing(TypeFact::qualifiedName)).toList(),
                facts.types());
    }

    @Test
    void noDuplicateTypes() {
        assertEquals(15, new HashSet<>(types.stream().map(TypeFact::qualifiedName).toList()).size());
    }
}
