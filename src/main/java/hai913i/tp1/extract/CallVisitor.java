package hai913i.tp1.extract;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;

import hai913i.tp1.model.CallFact;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;

public class CallVisitor extends ASTVisitor {

    // TreeMap : ordre déterministe (B1)
    private final Map<String, List<CallFact>> callsByMethod = new TreeMap<>();
    // pile : l'id de la méthode comptée en cours (vide = hors méthode)
    private final Deque<String> currentMethod = new ArrayDeque<>();

    public Map<String, List<CallFact>> callsByMethod() {
        return callsByMethod;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        // ① qui appelle : on mémorise la méthode dans laquelle on entre
        if (isCounted(node))
            currentMethod.push(MethodIds.of(node.resolveBinding()));
        return true; // ici on DOIT descendre : les appels sont dans le corps
    }

    @Override
    public void endVisit(MethodDeclaration node) {
        if (isCounted(node))
            currentMethod.pop();
    }

    private boolean isCounted(MethodDeclaration node) {
        if (!(node.getParent() instanceof AbstractTypeDeclaration type)) {
            return false;
        }
        return !(type.getParent() instanceof TypeDeclarationStatement);
    }

    // ---------- appels ----------

    @Override
    public boolean visit(MethodInvocation node) {

        // PROTECTION 1 : pile vide = appel hors de toute méthode (initialiseur de
        // champ).
        // §4.4 : on l'ignore. Sans ce test, peek() renverrait null et le TreeMap
        // lèverait une NullPointerException (il refuse les clés null).
        // On renvoie true pour continuer à descendre : le parcours n'est pas
        // interrompu.
        if (currentMethod.isEmpty())
            return true;

        // Le binding de la méthode appelée, choisi par JDT d'après le type STATIQUE du
        // receveur.
        // Il peut être null (type introuvable, dépendance manquante...) : voir
        // PROTECTION 2.
        IMethodBinding binding = node.resolveMethodBinding();

        // ① QUI APPELLE : l'id de la méthode comptée en cours de visite,
        // empilé à l'entrée de visit(MethodDeclaration).
        String caller = currentMethod.peek();

        // ③ TYPE STATIQUE DU RECEVEUR (§4.4) - PROTECTION 3 : trois cas selon
        // l'écriture de l'appel
        ITypeBinding receiverType;
        if (node.getExpression() != null) {
            // a.m() : le type statique de l'expression a (ex : loanable -> Loanable, pas
            // Item)
            receiverType = node.getExpression().resolveTypeBinding();
        } else if (binding != null && Modifier.isStatic(binding.getModifiers())) {
            // m() sans receveur, méthode statique : le type qui la déclare
            // (le "binding != null &&" évite de planter si l'appel n'est pas résolu)
            receiverType = binding.getDeclaringClass();
        } else {
            // m() sans receveur, méthode d'instance : this implicite = le type englobant
            receiverType = enclosingType(node);
        }
        // Effacement des génériques (List<Item> -> java.util.List) ;
        // "?" si le type est inconnu, pour ne jamais planter.
        String receiver = receiverType == null
                ? "?"
                : receiverType.getErasure().getQualifiedName();

        // ④ LA LIGNE : celle du NOM de la méthode appelée (pas du début de
        // l'expression),
        // ce qui distingue les appels d'une chaîne écrite sur plusieurs lignes.
        int line = ((CompilationUnit) node.getRoot())
                .getLineNumber(node.getName().getStartPosition());

        // PROTECTION 2 : binding null = appel non résolu.
        // On part des valeurs "non résolu" (cible vide, externe) et on ne les
        // remplace que si le binding existe : l'appel est donc TOUJOURS conservé.
        Optional<String> target = Optional.empty();
        boolean internal = false;
        if (binding != null) {
            // ② CE QUI EST APPELÉ : la déclaration exacte visée, sous forme d'identifiant
            // (même format que les MethodFact de StructureVisitor grâce à MethodIds)
            target = Optional.of(MethodIds.of(binding));

            // ⑤ PROJET OU EXTÉRIEUR : vrai si la classe déclarante vient de nos sources
            ITypeBinding owner = binding.getDeclaringClass();
            internal = owner != null && owner.isFromSource();
        }

        // On range le fait sous la méthode appelante.
        // resolved = binding != null (voir plus bas pour la variante avec isRecovered).
        callsByMethod.computeIfAbsent(caller, k -> new ArrayList<>())
                .add(new CallFact(caller, target, receiver, line, binding != null, internal));

        return true; // a.b().c() : un appel peut en contenir d'autres, on descend
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {

        // PROTECTION 1 : pile vide = appel hors de toute méthode (initialiseur de
        // champ) -> ignoré.
        if (currentMethod.isEmpty())
            return true;

        // Binding de la méthode appelée (null si non résolu : PROTECTION 2).
        IMethodBinding binding = node.resolveMethodBinding();

        // ① QUI APPELLE : la méthode comptée en cours de visite.
        String caller = currentMethod.peek();

        // ③ RECEVEUR : super.m() vise la superclasse DIRECTE du type englobant
        // (ex : dans Book, super.describe() -> receveur library.model.Item).
        ITypeBinding enclosing = enclosingType(node);
        ITypeBinding receiverType = enclosing == null ? null : enclosing.getSuperclass();
        String receiver = receiverType == null
                ? "?"
                : receiverType.getErasure().getQualifiedName();

        // ④ LA LIGNE : celle du nom de la méthode appelée.
        int line = ((CompilationUnit) node.getRoot())
                .getLineNumber(node.getName().getStartPosition());

        // PROTECTION 2 : valeurs "non résolu" par défaut, remplacées seulement si le
        // binding existe.
        Optional<String> target = Optional.empty();
        boolean internal = false;
        if (binding != null) {
            // ② CE QUI EST APPELÉ : l'id de la déclaration visée.
            target = Optional.of(MethodIds.of(binding));

            // ⑤ PROJET OU EXTÉRIEUR.
            ITypeBinding owner = binding.getDeclaringClass();
            internal = owner != null && owner.isFromSource();
        }

        callsByMethod.computeIfAbsent(caller, k -> new ArrayList<>())
                .add(new CallFact(caller, target, receiver, line, binding != null, internal));

        return true;
    }

    private ITypeBinding enclosingType(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null && !(current instanceof AbstractTypeDeclaration)) {
            current = current.getParent(); // on remonte jusqu'au type qui contient l'appel
        }
        return current == null ? null : ((AbstractTypeDeclaration) current).resolveBinding();
    }

    // ---------- vérification A3 ----------

    public static void main(String[] args) throws IOException {
        Path projectDirectory = Path.of(args[0]);
        ProjectSources sources = ProjectSources.of(projectDirectory);
        List<ParsedFile> files = JdtParser.parse(sources, List.of());

        CallVisitor visitor = new CallVisitor(); // une seule instance pour tout le projet
        for (ParsedFile f : files) {
            f.unit().accept(visitor);
        }

        int total = 0, internal = 0, external = 0, unresolved = 0;
        for (List<CallFact> calls : visitor.callsByMethod().values()) {
            for (CallFact c : calls) {
                total++;
                if (!c.resolved())
                    unresolved++;
                else if (c.internal())
                    internal++;
                else
                    external++;
            }
        }
        System.out.println("Appels : " + total + " (internes " + internal
                + ", externes " + external + ", non résolus " + unresolved + ")");

        for (Map.Entry<String, List<CallFact>> e : visitor.callsByMethod().entrySet()) {
            String caller = e.getKey();
            if (caller.contains("LoanService#borrow") || caller.contains("LibraryApp#main")
                    || caller.contains("Book#describe") || caller.contains("Catalog#available")) {
                System.out.println(caller);
                for (CallFact c : e.getValue()) {
                    System.out.println("  l." + c.sourceLine() + "  " + c.staticReceiverType()
                            + "  ->  " + c.targetId().orElse("NON RÉSOLU"));
                }
            }
        }
    }
}
