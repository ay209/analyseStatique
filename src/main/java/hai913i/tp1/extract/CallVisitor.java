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
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;

import hai913i.tp1.model.CallFact;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;

public class CallVisitor extends ASTVisitor {

    // TreeMap : ordre déterministe (B1)
    private final Map<String, List<CallFact>> callsByMethod = new TreeMap<>();
    // pile : l'id de la méthode comptée en cours (vide = hors méthode).
    // Sentinelle "" = méthode comptée mais non résolue par JDT : ses appels sont ignorés.
    private final Deque<String> currentMethod = new ArrayDeque<>();

    public Map<String, List<CallFact>> callsByMethod() {
        return callsByMethod;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        // ① qui appelle : on mémorise la méthode dans laquelle on entre
        if (isCounted(node)) {
            IMethodBinding binding = node.resolveBinding();
            currentMethod.push(binding == null ? "" : MethodIds.of(binding));
        }
        return true; // ici on DOIT descendre : les appels sont dans le corps
    }

    @Override
    public void endVisit(MethodDeclaration node) {
        if (isCounted(node))
            currentMethod.pop(); // même condition que le push : la pile reste symétrique
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

        // PROTECTION 1 : pile vide = appel hors de toute méthode (initialiseur de champ) ;
        // sentinelle "" = méthode englobante non résolue. Dans les deux cas : ignoré (§4.4).
        // On renvoie true pour continuer à descendre : le parcours n'est pas interrompu.
        if (currentMethod.isEmpty() || currentMethod.peek().isEmpty())
            return true;

        // Le binding de la méthode appelée, choisi par JDT d'après le type STATIQUE du receveur.
        // usable() le remplace par null s'il est inexploitable : voir PROTECTION 2.
        IMethodBinding binding = usable(node.resolveMethodBinding());

        // ① QUI APPELLE
        String caller = currentMethod.peek();

        // ③ TYPE STATIQUE DU RECEVEUR (§4.4) - PROTECTION 3 : trois cas selon l'écriture de l'appel
        ITypeBinding receiverType;
        if (node.getExpression() != null) {
            // a.m() : le type statique de l'expression a (ex : loanable -> Loanable, pas Item)
            receiverType = node.getExpression().resolveTypeBinding();
        } else if (binding != null && Modifier.isStatic(binding.getModifiers())) {
            // m() sans receveur, méthode statique : le type qui la déclare
            receiverType = binding.getDeclaringClass();
        } else {
            // m() sans receveur, méthode d'instance : this implicite = le type NOMMÉ englobant
            receiverType = enclosingType(node);
        }
        String receiver = receiverName(receiverType);

        // ④ LA LIGNE : celle du NOM de la méthode appelée (pas du début de l'expression)
        int line = ((CompilationUnit) node.getRoot())
                .getLineNumber(node.getName().getStartPosition());

        // PROTECTION 2 : binding null = appel non résolu.
        // On part des valeurs "non résolu" (cible vide, externe) et on ne les remplace que
        // si le binding est exploitable : l'appel est donc TOUJOURS conservé.
        Optional<String> target = Optional.empty();
        boolean internal = false;
        if (binding != null) {
            // ② CE QUI EST APPELÉ : la déclaration exacte visée, sous forme d'identifiant
            target = Optional.of(MethodIds.of(binding));

            // ⑤ PROJET OU EXTÉRIEUR : vrai si la classe déclarante vient de nos sources
            ITypeBinding owner = binding.getDeclaringClass();
            internal = owner != null && owner.isFromSource();
        }

        // resolved = binding exploitable (non null, non "récupéré", propriétaire nommé).
        callsByMethod.computeIfAbsent(caller, k -> new ArrayList<>())
                .add(new CallFact(caller, target, receiver, line, binding != null, internal));

        return true; // a.b().c() : un appel peut en contenir d'autres, on descend
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {

        // PROTECTION 1 : appel hors méthode, ou méthode englobante non résolue -> ignoré.
        if (currentMethod.isEmpty() || currentMethod.peek().isEmpty())
            return true;

        // PROTECTION 2 : binding exploitable ou null.
        IMethodBinding binding = usable(node.resolveMethodBinding());

        // ① QUI APPELLE
        String caller = currentMethod.peek();

        // ③ RECEVEUR : super.m() vise la superclasse DIRECTE du type qui contient l'appel
        // (ex : dans Book, super.describe() -> receveur library.model.Item).
        // Pour I.super.m() (interface), le receveur est l'interface qualifiante.
        ITypeBinding receiverType;
        ITypeBinding qualifier = node.getQualifier() == null ? null : node.getQualifier().resolveTypeBinding();
        if (qualifier != null && qualifier.isInterface()) {
            receiverType = qualifier;
        } else {
            ITypeBinding direct = directEnclosing(node);
            receiverType = direct == null ? null : direct.getSuperclass();
        }
        String receiver = receiverName(receiverType);

        // ④ LA LIGNE : celle du nom de la méthode appelée.
        int line = ((CompilationUnit) node.getRoot())
                .getLineNumber(node.getName().getStartPosition());

        Optional<String> target = Optional.empty();
        boolean internal = false;
        if (binding != null) {
            target = Optional.of(MethodIds.of(binding));
            ITypeBinding owner = binding.getDeclaringClass();
            internal = owner != null && owner.isFromSource();
        }

        callsByMethod.computeIfAbsent(caller, k -> new ArrayList<>())
                .add(new CallFact(caller, target, receiver, line, binding != null, internal));

        return true;
    }

    /**
     * Le binding s'il est exploitable, sinon null (l'appel sera conservé mais "non résolu") :
     * - null ou "récupéré" par JDT (dépendance manquante) : on ne peut pas s'y fier ;
     * - déclaré dans une classe anonyme ou locale (nom qualifié vide) : ce n'est pas un nœud du graphe
     *   (ces types ne sont pas des types comptés).
     */
    private static IMethodBinding usable(IMethodBinding binding) {
        if (binding == null || binding.isRecovered()) {
            return null;
        }
        ITypeBinding owner = binding.getMethodDeclaration().getDeclaringClass();
        if (owner == null || owner.getErasure().getQualifiedName().isEmpty()) {
            return null;
        }
        return binding;
    }

    /** Nom qualifié érasé ; pour un type anonyme (nom vide) : celui de son supertype nommé ; "?" si inconnu. */
    private static String receiverName(ITypeBinding type) {
        if (type == null) {
            return "?";
        }
        ITypeBinding erased = type.getErasure();
        String name = erased.getQualifiedName();
        if (!name.isEmpty()) {
            return name;
        }
        if (erased.getInterfaces().length > 0) {
            return receiverName(erased.getInterfaces()[0]);
        }
        return erased.getSuperclass() != null ? receiverName(erased.getSuperclass()) : "?";
    }

    /** Le type NOMMÉ englobant : on saute les types locaux (non comptés), dont le nom qualifié est vide. */
    private ITypeBinding enclosingType(ASTNode node) {
        for (ASTNode p = node.getParent(); p != null; p = p.getParent()) {
            if (p instanceof AbstractTypeDeclaration type && !(type.getParent() instanceof TypeDeclarationStatement)) {
                return type.resolveBinding();
            }
        }
        return null;
    }

    /** Le type le plus proche qui contient le noeud, anonyme ou local compris (pour résoudre "super"). */
    private ITypeBinding directEnclosing(ASTNode node) {
        for (ASTNode p = node.getParent(); p != null; p = p.getParent()) {
            if (p instanceof AnonymousClassDeclaration anonymous) {
                return anonymous.resolveBinding();
            }
            if (p instanceof AbstractTypeDeclaration type) {
                return type.resolveBinding();
            }
        }
        return null;
    }
}