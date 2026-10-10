package hai913i.tp1.compare;

/**
 * Les chiffres BRUTS de Spoon, avant toute convention du TP (bonus C1).
 *
 * @param topLevelTypes       model.getAllTypes() : types de premier niveau seulement
 * @param allTypes            tous les CtType du modèle (membres, locaux et anonymes compris)
 * @param methods             CtMethod
 * @param constructors        CtConstructor, implicites compris
 * @param implicitConstructors parmi eux, ceux que le code source ne contient pas
 * @param fields              CtField, constantes d'énumération comprises
 * @param enumConstants       parmi eux, les CtEnumValue
 * @param packages            model.getAllPackages() : paquetage racine et paquetages intermédiaires compris
 * @param invocations         CtInvocation, appels super(...) et this(...) compris
 * @param constructorInvocations parmi eux, les appels de constructeur
 */
public record RawCounts(int topLevelTypes, int allTypes, int methods, int constructors, int implicitConstructors,
        int fields, int enumConstants, int packages, int invocations, int constructorInvocations) {
}
