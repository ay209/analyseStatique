package hai913i.tp1.model;
import java.util.Comparator;
import java.util.List;
public record MethodFact (
    String id,
    String name,
    List<String> parameterTypes,
    boolean constructor,
    int bodyLoc,
    List<CallFact> calls
){
    public MethodFact {
        parameterTypes = List.copyOf(parameterTypes);
        // Ordre déterministe : ligne, puis cible, puis receveur (indépendant du parcours JDT)
        calls = calls.stream()
                .sorted(Comparator.comparingInt(CallFact::sourceLine)
                        .thenComparing(c -> c.targetId().orElse(""))
                        .thenComparing(CallFact::staticReceiverType))
                .toList();
    }
    
}
