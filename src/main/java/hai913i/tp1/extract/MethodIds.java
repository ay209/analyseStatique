package hai913i.tp1.extract;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.eclipse.jdt.core.dom.IMethodBinding;

public final class MethodIds {
    private MethodIds() {}

    public static String of(IMethodBinding method) {
        IMethodBinding decl = method.getMethodDeclaration(); // remonte à la déclaration générique
        String owner = decl.getDeclaringClass().getErasure().getQualifiedName();
        String params = Arrays.stream(decl.getParameterTypes())
                .map(t -> t.getErasure().getQualifiedName())
                .collect(Collectors.joining(","));
        return owner + "#" + decl.getName() + "(" + params + ")";
    }
}
