package hai913i.tp1;

import org.eclipse.jdt.core.dom.*;

public class ReturnFalseDemo {

    static final String SOURCE = """
        class Shop {
            Runnable task() {
                return new Runnable() {
                    public void run() {
                        if (true) {
                            System.out.println("inside anonymous");
                        }
                    }
                };
            }
        }
        """;

    public static void main(String[] args) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(SOURCE.toCharArray());
        parser.setUnitName("Shop.java"); 
        // pourquoi l'annexe le recommande ? 
        // Sinon, JDT signale des erreurs de compilo supplémentaires

        CompilationUnit unit = (CompilationUnit) parser.createAST(null);

        System.out.println("=== avec return true partout (comme AstPrinter) ===");
        unit.accept(new AstPrinter());

        System.out.println("=== avec return false sur AnonymousClassDeclaration ===");
        unit.accept(new ASTVisitor() {
            int depth;

            @Override
            public boolean preVisit2(ASTNode node) {
                System.out.println("  ".repeat(depth) + node.getClass().getSimpleName());
                depth++;
                if(node instanceof AnonymousClassDeclaration)
                {
                    return false;
                }
                return true;
            }

            @Override
            public void postVisit(ASTNode node) {
                depth--;
            }
        });
    }
}