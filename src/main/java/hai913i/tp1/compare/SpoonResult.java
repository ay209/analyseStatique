package hai913i.tp1.compare;

import java.util.List;

import hai913i.tp1.model.TypeFact;

/** Ce que Spoon a produit : ses chiffres bruts, et les mêmes faits que JDT une fois les conventions du TP appliquées. */
public record SpoonResult(RawCounts raw, List<TypeFact> types) {
}
