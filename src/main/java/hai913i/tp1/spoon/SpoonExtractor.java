package hai913i.tp1.spoon;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import hai913i.tp1.compare.RawCounts;
import hai913i.tp1.compare.SpoonResult;
import hai913i.tp1.model.CallFact;
import hai913i.tp1.model.FieldFact;
import hai913i.tp1.model.MethodFact;
import hai913i.tp1.model.TypeFact;
import hai913i.tp1.model.TypeKind;
import hai913i.tp1.model.Visibility;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtRecord;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Bonus C1 : les mêmes faits que nos extracteurs JDT (A2, A3), obtenus avec Spoon 11.5.0.
 *
 * Seule classe qui connaît Spoon. Elle renvoie deux choses : les chiffres BRUTS de Spoon, et un modèle de faits
 * construit avec les conventions du TP (§4) pour pouvoir réutiliser CallGraph et comparer avec JDT :
 * - pas de constructeur implicite, pas de constante d'énumération parmi les attributs ;
 * - un appel = une invocation de MÉTHODE (pas super(...) ni this(...)) ;
 * - types anonymes et locaux ignorés ; leurs appels reviennent à la méthode comptée qui les englobe ;
 * - appel hors méthode (initialiseur de champ) ignoré ;
 * - noms imbriqués avec un point (library.service.LoanService.Loan), comme JDT, et non avec $.
 */
public final class SpoonExtractor {

    /** Un nom de type local ou anonyme chez Spoon : Outer$1, Outer$1Local. */
    private static final Pattern LOCAL_OR_ANONYMOUS = Pattern.compile("\\$\\d");

    private SpoonExtractor() {
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static SpoonResult extract(Path sourceRoot) {
        Launcher launcher = launcher(sourceRoot);
        CtModel model = launcher.buildModel();

        List<CtType> allTypes = model.getElements(new TypeFilter<>(CtType.class));
        List<CtMethod> allMethods = model.getElements(new TypeFilter<>(CtMethod.class));
        List<CtConstructor> allConstructors = model.getElements(new TypeFilter<>(CtConstructor.class));
        List<CtField> allFields = model.getElements(new TypeFilter<>(CtField.class));
        List<CtEnumValue> enumValues = model.getElements(new TypeFilter<>(CtEnumValue.class));
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));

        int implicitConstructors = (int) allConstructors.stream().filter(CtElement::isImplicit).count();
        int constructorInvocations = (int) invocations.stream()
                .filter(i -> i.getExecutable() != null && i.getExecutable().isConstructor()).count();
        RawCounts raw = new RawCounts(model.getAllTypes().size(), allTypes.size(), allMethods.size(),
                allConstructors.size(), implicitConstructors, allFields.size(), enumValues.size(),
                model.getAllPackages().size(), invocations.size(), constructorInvocations);

        // 1re passe : les types et méthodes comptés, et l'identifiant de chaque méthode (clé = identité de l'objet,
        // car Spoon compare les éléments par structure : deux méthodes identiques de classes différentes seraient égales).
        Map<CtElement, String> ids = new IdentityHashMap<>();
        List<CtType<?>> counted = new ArrayList<>();
        for (CtType<?> type : (List<CtType<?>>) (List) allTypes) {
            if (isCounted(type)) {
                counted.add(type);
                for (CtExecutable<?> m : declaredMethods(type)) {
                    ids.put(m, idOf(m, type));
                }
            }
        }

        // 2e passe : les appels, rangés par méthode appelante.
        Map<String, List<CallFact>> callsByMethod = new java.util.HashMap<>();
        for (CtInvocation<?> invocation : (List<CtInvocation<?>>) (List) invocations) {
            if (invocation.getExecutable() == null || invocation.getExecutable().isConstructor()) {
                continue;
            }
            String caller = callerOf(invocation, ids);
            if (caller == null) {
                continue; // hors de toute méthode comptée (initialiseur de champ, classe non comptée seule)
            }
            callsByMethod.computeIfAbsent(caller, k -> new ArrayList<>()).add(toFact(invocation, caller, ids));
        }

        List<TypeFact> types = new ArrayList<>();
        for (CtType<?> type : counted) {
            types.add(toTypeFact(type, ids, callsByMethod));
        }
        types.sort(java.util.Comparator.comparing(TypeFact::qualifiedName));
        return new SpoonResult(raw, types);
    }

    private static Launcher launcher(Path src) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(src.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setShouldCompile(false);
        Path out = Paths.get(System.getProperty("java.io.tmpdir"), "spooned");
        launcher.setSourceOutputDirectory(out.toString());
        launcher.getEnvironment().setOutputType(spoon.OutputType.NO_OUTPUT);
        return launcher;
    }

    // ---------- types ----------

    private static boolean isCounted(CtType<?> type) {
        if (type instanceof CtTypeParameter) {
            return false;
        }
        String name = type.getQualifiedName();
        return name != null && !name.isEmpty() && !LOCAL_OR_ANONYMOUS.matcher(name).find();
    }

    private static TypeFact toTypeFact(CtType<?> type, Map<CtElement, String> ids,
            Map<String, List<CallFact>> callsByMethod) {
        List<MethodFact> methods = new ArrayList<>();
        for (CtExecutable<?> m : declaredMethods(type)) {
            String id = ids.get(m);
            methods.add(new MethodFact(id, m instanceof CtConstructor ? simpleName(type) : m.getSimpleName(),
                    parameterTypes(m), m instanceof CtConstructor, bodyLines(m),
                    callsByMethod.getOrDefault(id, List.of())));
        }
        List<FieldFact> fields = new ArrayList<>();
        for (CtField<?> f : type.getFields()) {
            if (!(f instanceof CtEnumValue)) {
                fields.add(new FieldFact(f.getSimpleName(), f.getType() == null ? "?" : f.getType().toString(),
                        visibility(f.getVisibility())));
            }
        }
        List<String> interfaces = new ArrayList<>();
        for (CtTypeReference<?> itf : type.getSuperInterfaces()) {
            interfaces.add(name(itf));
        }
        return new TypeFact(name(type), packageName(type), kind(type), superclasses(type), interfaces, fields, methods);
    }

    private static TypeKind kind(CtType<?> type) {
        if (type instanceof CtEnum) {
            return TypeKind.ENUM;
        }
        if (type instanceof CtRecord) {
            return TypeKind.RECORD;
        }
        if (type instanceof CtInterface) {
            return TypeKind.INTERFACE;
        }
        return TypeKind.CLASS;
    }

    /** Chaîne des superclasses jusqu'à Object exclu, comme StructureVisitor. */
    private static List<String> superclasses(CtType<?> type) {
        List<String> result = new ArrayList<>();
        CtTypeReference<?> current = type.getSuperclass();
        int guard = 0;
        while (current != null && !current.getQualifiedName().equals("java.lang.Object") && guard++ < 100) {
            result.add(name(current));
            CtType<?> declaration = current.getTypeDeclaration();
            current = declaration == null ? null : declaration.getSuperclass();
        }
        return result;
    }

    private static String packageName(CtType<?> type) {
        CtPackage pack = type.getPackage();
        return pack == null || pack.isUnnamedPackage() ? "" : pack.getQualifiedName();
    }

    private static Visibility visibility(ModifierKind kind) {
        if (kind == ModifierKind.PUBLIC) {
            return Visibility.PUBLIC;
        }
        if (kind == ModifierKind.PROTECTED) {
            return Visibility.PROTECTED;
        }
        if (kind == ModifierKind.PRIVATE) {
            return Visibility.PRIVATE;
        }
        return Visibility.PACKAGE;
    }

    // ---------- méthodes ----------

    /** Méthodes puis constructeurs EXPLICITES (les constructeurs implicites ne sont pas dans le code source). */
    private static List<CtExecutable<?>> declaredMethods(CtType<?> type) {
        List<CtExecutable<?>> result = new ArrayList<>();
        for (CtMethod<?> m : type.getMethods()) {
            if (!m.isImplicit()) {
                result.add(m);
            }
        }
        if (type instanceof CtClass<?> klass) {
            for (CtConstructor<?> c : klass.getConstructors()) {
                if (!c.isImplicit()) {
                    result.add(c);
                }
            }
        }
        return result;
    }

    private static String idOf(CtExecutable<?> method, CtType<?> type) {
        String simple = method instanceof CtConstructor ? simpleName(type) : method.getSimpleName();
        return name(type) + "#" + simple + "(" + String.join(",", parameterTypes(method)) + ")";
    }

    private static List<String> parameterTypes(CtExecutable<?> method) {
        List<String> result = new ArrayList<>();
        for (CtParameter<?> p : method.getParameters()) {
            result.add(name(p.getType()));
        }
        return result;
    }

    private static int bodyLines(CtExecutable<?> method) {
        if (method.getBody() == null || !method.getBody().getPosition().isValidPosition()) {
            return 0;
        }
        return method.getBody().getPosition().getEndLine() - method.getBody().getPosition().getLine() + 1;
    }

    // ---------- appels ----------

    /** La méthode COMPTÉE la plus proche autour de l'appel : lambdas, classes anonymes et locales sont traversées. */
    private static String callerOf(CtElement element, Map<CtElement, String> ids) {
        for (CtElement p = parentOf(element); p != null; p = parentOf(p)) {
            String id = ids.get(p);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static CtElement parentOf(CtElement element) {
        return element.isParentInitialized() ? element.getParent() : null;
    }

    private static CallFact toFact(CtInvocation<?> invocation, String caller, Map<CtElement, String> ids) {
        CtExecutableReference<?> reference = invocation.getExecutable();
        CtExecutable<?> declaration = reference.getExecutableDeclaration();
        String declared = declaration == null ? null : ids.get(declaration);

        Optional<String> target;
        boolean resolved;
        boolean internal;
        if (declared != null) {
            target = Optional.of(declared);
            resolved = true;
            internal = true;
        } else if (isKnown(reference)) {
            target = Optional.of(referenceId(reference));
            resolved = true;
            internal = false;
        } else {
            target = Optional.empty();
            resolved = false;
            internal = false;
        }
        int line = invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : 0;
        return new CallFact(caller, target, receiver(invocation), line, resolved, internal);
    }

    /** Le type qui déclare la méthode est connu de Spoon (JDK, bibliothèque sur le classpath) ou des sources. */
    private static boolean isKnown(CtExecutableReference<?> reference) {
        CtTypeReference<?> owner = reference.getDeclaringType();
        return owner != null && owner.getTypeDeclaration() != null;
    }

    private static String referenceId(CtExecutableReference<?> reference) {
        List<String> parameters = new ArrayList<>();
        for (CtTypeReference<?> p : reference.getParameters()) {
            parameters.add(name(p));
        }
        return name(reference.getDeclaringType()) + "#" + reference.getSimpleName() + "("
                + String.join(",", parameters) + ")";
    }

    private static String receiver(CtInvocation<?> invocation) {
        CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return "?";
        }
        if (target instanceof CtTypeAccess<?> access) {
            return name(access.getAccessedType());
        }
        return name(target.getType());
    }

    // ---------- noms ----------

    /** Nom qualifié sans arguments de type ; les classes imbriquées s'écrivent avec un point (comme JDT), pas $. */
    private static String name(CtTypeReference<?> reference) {
        return reference == null ? "?" : reference.getQualifiedName().replace('$', '.');
    }

    private static String name(CtType<?> type) {
        return type.getQualifiedName().replace('$', '.');
    }

    private static String simpleName(CtType<?> type) {
        return type.getSimpleName();
    }
}
