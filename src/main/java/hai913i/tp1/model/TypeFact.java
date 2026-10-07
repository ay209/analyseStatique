package hai913i.tp1.model;
import java.util.Comparator;
import java.util.List;

public record TypeFact(
        String qualifiedName,
        String packageName,
        TypeKind kind,
        List<String> superclasses,
        List<String> interfaces,
        List<FieldFact> fields,
        List<MethodFact> methods
) {
    public TypeFact {
        superclasses = List.copyOf(superclasses);
        interfaces = List.copyOf(interfaces);
        fields = fields.stream().sorted(Comparator.comparing(FieldFact::name)).toList();
        methods = methods.stream().sorted(Comparator.comparing(MethodFact::id)).toList();
    }
}
