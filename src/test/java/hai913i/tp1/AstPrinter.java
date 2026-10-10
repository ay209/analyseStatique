package hai913i.tp1;


import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;


public class AstPrinter extends ASTVisitor{

        int depth;
        int shown;

        @Override
        public boolean preVisit2(ASTNode node) {
          
            System.out.println("   " + "  ".repeat(depth) + "entre "
                + node.getClass().getSimpleName());
                
            depth++;                
            return true;
            }

        @Override
        public void postVisit(ASTNode node) {
                depth--;
        }
    }