package hai913i.tp1.model;

import java.util.Optional;

/**
 * CallFact
 */
public record CallFact(
    String callerId,
    Optional<String> targetId,
    String staticReceiverType,
    int sourceLine,
    boolean resolved,
    boolean internal
) {

}
