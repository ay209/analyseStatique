package hai913i.tp1.extract;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import hai913i.tp1.model.FieldFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.model.TypeKind;
import hai913i.tp1.model.Visibility;


public class StructureVisitor extends ASTVisitor {

    // Accumulateur partagé sur toute la durée de vie de ce visiteur :
    // une seule instance visite tous les fichiers du projet (voir main()).
    private final List<TypeFact> types = new ArrayList<>();

    public List<TypeFact> types() {
        return types;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        TypeKind kind = node.isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS;
        extractType(node, kind);
        return false;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        extractType(node, TypeKind.ENUM);
        return false;
    }

    @Override
    public boolean visit(RecordDeclaration node) {
        extractType(node, TypeKind.RECORD);
        return false;
    }

    private void extractType(AbstractTypeDeclaration node, TypeKind kind) {
        ITypeBinding binding = node.resolveBinding();
        if (binding == null) {
            // Déclaration que JDT n'a pas pu résoudre (doublon, code cassé) : on l'ignore plutôt que de planter.
            return;
        }
        String qualifiedName = binding.getQualifiedName();
        String packageName = binding.getPackage() == null ? "" : binding.getPackage().getName();

        List<String> superclasses = new ArrayList<>();
        ITypeBinding current = binding.getSuperclass();
        while (current != null && !current.getQualifiedName().equals("java.lang.Object")) {
            superclasses.add(current.getErasure().getQualifiedName());
            current = current.getSuperclass();
        }

        List<String> interfaces = new ArrayList<>();
        for (ITypeBinding itf : binding.getInterfaces()) {
            interfaces.add(itf.getErasure().getQualifiedName());
        }

        List<FieldFact> fields = new ArrayList<>();
        List<MethodFact> methods = new ArrayList<>();

        for (Object o : node.bodyDeclarations()) {
            if (o instanceof TypeDeclaration nestedType) {
                nestedType.accept(this);
            } else if (o instanceof EnumDeclaration nestedEnum) {
                nestedEnum.accept(this);
            } else if (o instanceof RecordDeclaration nestedRecord) {
                nestedRecord.accept(this);
            } else if (o instanceof MethodDeclaration method) {
                MethodFact fact = toMethodFact(method);
                if (fact != null) { // null = méthode non résolue (doublon, code cassé) : ignorée
                    methods.add(fact);
                }
            } else if (o instanceof FieldDeclaration field) {
                fields.addAll(toFieldFacts(field));
            }
        }

        types.add(new TypeFact(qualifiedName, packageName, kind,
                superclasses, interfaces, fields, methods));
    }

    /** Renvoie null si JDT n'a pas pu résoudre la déclaration. */
    private MethodFact toMethodFact(MethodDeclaration method) {
        IMethodBinding mb = method.resolveBinding();
        if (mb == null) {
            return null;
        }
        String name = method.getName().getIdentifier();
        List<String> parameterTypes = Arrays.stream(mb.getParameterTypes())
                .map(t -> t.getErasure().getQualifiedName()).toList();
        String id = MethodIds.of(mb);
        return new MethodFact(id, name, parameterTypes, method.isConstructor(),
                bodyLines(method), List.of()); // les appels sont ajoutés par FactExtractor
    }

    /** §4.2 : lignes physiques du corps, accolades comprises ; 0 sans corps. */
    private int bodyLines(MethodDeclaration method) {
        Block body = method.getBody();
        if (body == null)
            return 0; // méthode abstraite ou d'interface
        CompilationUnit unit = (CompilationUnit) method.getRoot();
        int start = body.getStartPosition();
        int first = unit.getLineNumber(start);
        int last = unit.getLineNumber(start + body.getLength() - 1); // le "- 1" évite de compter la ligne suivante
        if (first < 1 || last < first) {
            return 0; // position hors fichier (code cassé) : pas de corps exploitable
        }
        return last - first + 1;
    }

    private List<FieldFact> toFieldFacts(FieldDeclaration field) {
        Visibility visibility = visibilityOf(field.getModifiers());
        String declaredType = field.getType().toString();

        List<FieldFact> result = new ArrayList<>();
        for (Object f : field.fragments()) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) f;
            result.add(new FieldFact(fragment.getName().getIdentifier(), declaredType, visibility));
        }
        return result;
    }

    private Visibility visibilityOf(int modifiers) {
        if (Modifier.isPublic(modifiers))
            return Visibility.PUBLIC;
        if (Modifier.isProtected(modifiers))
            return Visibility.PROTECTED;
        if (Modifier.isPrivate(modifiers))
            return Visibility.PRIVATE;
        return Visibility.PACKAGE;
    }
}
