package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

/** A1 : comportement du visiteur JDT (parcours, profondeur, elagage) et AstPrinter. */
class AstVisitorTest {

    private static final String SHOP = """
            class Shop {
                Runnable task() {
                    return new Runnable() {
                        public void run() {
                            if (true) { System.out.println("inside anonymous"); }
                        }
                    };
                }
            }
            """;

    /** Compte les noeuds, suit la profondeur comme AstPrinter, et verifie l'appariement pre/post. */
    private static final class Tracker extends ASTVisitor {
        final List<String> names = new ArrayList<>();
        final List<Integer> depths = new ArrayList<>();
        final Deque<ASTNode> stack = new ArrayDeque<>();
        int depth;
        int pre;
        int post;
        boolean balanced = true;

        @Override
        public boolean preVisit2(ASTNode node) {
            pre++;
            names.add(node.getClass().getSimpleName());
            depths.add(depth++);
            stack.push(node);
            return true;
        }

        @Override
        public void postVisit(ASTNode node) {
            post++;
            depth--;
            if (stack.isEmpty() || stack.pop() != node) {
                balanced = false;
            }
        }
    }

    @Test
    void rootIsCompilationUnitAtDepthZero() throws IOException {
        Tracker t = new Tracker();
        TestSupport.validationUnit("Dvd.java").accept(t);
        assertEquals("CompilationUnit", t.names.get(0));
        assertEquals(0, t.depths.get(0));
        assertTrue(t.depths.stream().skip(1).allMatch(d -> d >= 1), "seule la racine est a la profondeur 0");
    }

    @Test
    void depthReturnsToZeroAndPrePostArePaired() throws IOException {
        for (var f : TestSupport.validationParsed()) {
            Tracker t = new Tracker();
            f.unit().accept(t);
            assertEquals(0, t.depth, f.path().toString());
            assertEquals(t.pre, t.post, f.path().toString());
            assertTrue(t.balanced, "postVisit doit fermer le dernier noeud ouvert : " + f.path());
            assertTrue(t.stack.isEmpty());
        }
    }

    @Test
    void childrenAreExactlyOneLevelDeeperThanTheirParent() throws IOException {
        Tracker t = new Tracker();
        TestSupport.validationUnit("Book.java").accept(t);
        for (int i = 1; i < t.depths.size(); i++) {
            assertTrue(t.depths.get(i) <= t.depths.get(i - 1) + 1,
                    "on ne peut descendre que d'un niveau a la fois (noeud " + i + ")");
        }
        assertTrue(t.depths.stream().mapToInt(Integer::intValue).max().orElse(0) >= 4);
    }

    @Test
    void enumIsAnEnumDeclarationNotATypeDeclaration() throws IOException {
        AtomicInteger enums = new AtomicInteger();
        AtomicInteger types = new AtomicInteger();
        TestSupport.validationUnit("Category.java").accept(new ASTVisitor() {
            @Override
            public boolean visit(EnumDeclaration node) {
                enums.incrementAndGet();
                return true;
            }

            @Override
            public boolean visit(TypeDeclaration node) {
                types.incrementAndGet();
                return true;
            }
        });
        assertEquals(1, enums.get());
        assertEquals(0, types.get(), "un enum n'est PAS un TypeDeclaration");
    }

    @Test
    void classAndInterfaceAreBothTypeDeclarations() throws IOException {
        AtomicInteger interfaces = new AtomicInteger();
        AtomicInteger classes = new AtomicInteger();
        for (String file : List.of("Dvd.java", "Loanable.java")) {
            TestSupport.validationUnit(file).accept(new ASTVisitor() {
                @Override
                public boolean visit(TypeDeclaration node) {
                    (node.isInterface() ? interfaces : classes).incrementAndGet();
                    return true;
                }
            });
        }
        assertEquals(1, classes.get());
        assertEquals(1, interfaces.get());
    }

    @Test
    void returningTrueDescendsIntoAnonymousClass() {
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger ifs = new AtomicInteger();
        TestSupport.parseString("Shop.java", SHOP).accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.getName().getIdentifier().equals("run")) {
                    runs.incrementAndGet();
                }
                return true;
            }

            @Override
            public boolean visit(IfStatement node) {
                ifs.incrementAndGet();
                return true;
            }
        });
        assertEquals(1, runs.get());
        assertEquals(1, ifs.get());
    }

    @Test
    void returningFalsePrunesTheSubtreeButStillCallsEndVisit() {
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger ifs = new AtomicInteger();
        AtomicInteger anonVisits = new AtomicInteger();
        AtomicInteger anonEnds = new AtomicInteger();
        Tracker tracker = new Tracker();
        TestSupport.parseString("Shop.java", SHOP).accept(new ASTVisitor() {
            @Override
            public boolean preVisit2(ASTNode node) {
                return tracker.preVisit2(node);
            }

            @Override
            public void postVisit(ASTNode node) {
                tracker.postVisit(node);
            }

            @Override
            public boolean visit(AnonymousClassDeclaration node) {
                anonVisits.incrementAndGet();
                return false;
            }

            @Override
            public void endVisit(AnonymousClassDeclaration node) {
                anonEnds.incrementAndGet();
            }

            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.getName().getIdentifier().equals("run")) {
                    runs.incrementAndGet();
                }
                return true;
            }

            @Override
            public boolean visit(IfStatement node) {
                ifs.incrementAndGet();
                return true;
            }
        });
        assertEquals(1, anonVisits.get());
        assertEquals(1, anonEnds.get(), "endVisit est appele meme quand visit renvoie false");
        assertEquals(0, runs.get(), "run() est sous le noeud elague");
        assertEquals(0, ifs.get());
        assertEquals(0, tracker.depth);
        assertTrue(tracker.balanced, "postVisit reste apparie meme avec elagage");
    }

    @Test
    void pruningReducesTheNumberOfVisitedNodes() {
        Tracker full = new Tracker();
        TestSupport.parseString("Shop.java", SHOP).accept(full);
        AtomicInteger pruned = new AtomicInteger();
        TestSupport.parseString("Shop.java", SHOP).accept(new ASTVisitor() {
            @Override
            public boolean preVisit2(ASTNode node) {
                pruned.incrementAndGet();
                return true;
            }

            @Override
            public boolean visit(AnonymousClassDeclaration node) {
                return false;
            }
        });
        assertTrue(pruned.get() < full.pre, pruned + " < " + full.pre);
    }

    @Test
    void preVisit2FalseSkipsVisitEntirely() throws IOException {
        AtomicInteger visits = new AtomicInteger();
        TestSupport.validationUnit("Dvd.java").accept(new ASTVisitor() {
            @Override
            public boolean preVisit2(ASTNode node) {
                return false;
            }

            @Override
            public boolean visit(TypeDeclaration node) {
                visits.incrementAndGet();
                return true;
            }
        });
        assertEquals(0, visits.get());
    }

    @Test
    void parentChainsUp() throws IOException {
        CompilationUnit u = TestSupport.validationUnit("Dvd.java");
        assertNull(u.getParent());
        u.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                assertTrue(node.getParent() instanceof TypeDeclaration);
                return true;
            }
        });
    }

    // ---------- AstPrinter ----------

    private static String print(CompilationUnit u) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            u.accept(new AstPrinter());
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static int indent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    @Test
    void printerShowsEnumForCategoryAndTypeForDvd() throws IOException {
        String category = print(TestSupport.validationUnit("Category.java"));
        String dvd = print(TestSupport.validationUnit("Dvd.java"));
        assertTrue(category.contains("EnumDeclaration"), category);
        assertFalse(category.contains("TypeDeclaration"));
        assertTrue(dvd.contains("TypeDeclaration"), dvd);
        assertTrue(dvd.contains("MethodDeclaration"));
        assertTrue(dvd.contains("CompilationUnit"));
    }

    @Test
    void printerIndentsByDepth() throws IOException {
        String out = print(TestSupport.validationUnit("Dvd.java"));
        List<String> lines = out.lines().filter(l -> !l.isBlank()).toList();
        assertTrue(lines.size() > 10);
        int first = indent(lines.get(0));
        assertTrue(lines.get(0).contains("CompilationUnit"), lines.get(0));
        assertTrue(lines.stream().allMatch(l -> indent(l) >= first));
        assertTrue(lines.stream().anyMatch(l -> indent(l) > first + 4), "des noeuds profonds sont plus indentes");
    }

    @Test
    void printerHasAtLeastOneLinePerNode() throws IOException {
        CompilationUnit u = TestSupport.validationUnit("Dvd.java");
        Tracker t = new Tracker();
        u.accept(t);
        long lines = print(u).lines().filter(l -> !l.isBlank()).count();
        assertTrue(lines >= t.pre, lines + " lignes pour " + t.pre + " noeuds");
    }

    @Test
    void printerIsRepeatableOnTheSameUnit() throws IOException {
        CompilationUnit u = TestSupport.validationUnit("Category.java");
        assertEquals(print(u), print(u), "un AstPrinter neuf doit donner le meme affichage (pas d'etat residuel)");
    }
}
