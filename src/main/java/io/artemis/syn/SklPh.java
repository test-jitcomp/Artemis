package io.artemis.syn;

import java.util.List;

import io.artemis.AxChecker;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.template.Local;

public class SklPh {

    public static void placeholder(String id) {}

    @Local
    public static void substitute(CtStatement from, String id, CtStatement to) {
        try {
            from.getElements(new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation invoc) {
                    if (!super.matches(invoc) || invoc.getTarget() == null
                            || !invoc.getTarget().prettyprint().equals("SklPh")
                            || !invoc.getExecutable().getSimpleName().equals("placeholder")) {
                        return false;
                    }
                    List<CtExpression<?>> args = (List<CtExpression<?>>) invoc.getArguments();
                    if (args.size() != 1) {
                        return false;
                    }
                    CtExpression<?> a = args.get(0);
                    return a instanceof CtLiteral && a.prettyprint().equals("\"" + id + "\"");
                }
            }).get(0).replace(to);
        } catch (IndexOutOfBoundsException ignoreUnused) {
            // noinspection ConstantConditions
            AxChecker.check(false, "No placeholder namely " + id + " found");
        }
    }
}
