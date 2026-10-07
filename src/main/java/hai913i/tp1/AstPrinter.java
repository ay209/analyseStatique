package hai913i.tp1;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;

import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;

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
        public static void main(String[] args) throws IOException {
            Path projectDirectory = Path.of(args[0]); // ex: ../resources/validation
            ProjectSources sources = ProjectSources.of(projectDirectory);
            List<ParsedFile> files = JdtParser.parse(sources, List.of());

            for(ParsedFile f : files)
            {
                if(f.path().endsWith("Dvd.java"))
                {
                    f.unit().accept(new AstPrinter());
                }
                if(f.path().endsWith("Category.java"))
                {
                    f.unit().accept(new AstPrinter());
                }
            }
       
        }
    }
    
    

