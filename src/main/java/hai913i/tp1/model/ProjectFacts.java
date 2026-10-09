package hai913i.tp1.model;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public record ProjectFacts(
        List<TypeFact> types,
        List<Path> sourceFiles,
        int totalLines) {
    public ProjectFacts {
        types = types.stream().sorted(Comparator.comparing(TypeFact::qualifiedName)).toList();
        sourceFiles = sourceFiles.stream().sorted().toList();
    }
}
