package io.artemis.pol;

import java.util.ArrayList;
import java.util.List;

import io.artemis.Artemis;
import io.artemis.AxChecker;
import io.artemis.AxLog;
import io.artemis.AxRandom;
import io.artemis.mut.LoopInserter;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.AbstractFilter;

public class ArtemisPolicy extends MutationPolicy {

    public ArtemisPolicy(Artemis ax, Artemis.ExtraOpts opts) {
        super(ax, opts);
    }

    @Override
    public void apply(CtClass<?> clazz) {
        AxRandom rand = AxRandom.getInstance();
        LoopInserter inserter = new LoopInserter(mAx);

        // We never mutate initializer blocks, either static or not
        List<CtMethod<?>> methods = new ArrayList<>(clazz.getMethods());
        AxChecker.check(methods.size() > 0,
                "No methods found in the given class: " + clazz.getQualifiedName());

        CtMethod<?> meth = methods.get(rand.nextInt(methods.size()));
        AxLog.v("Mutating method: " + clazz.getQualifiedName() + "::" + meth.getSimpleName()
                + "()");
        List<CtStatement> statements = meth.getElements(new AbstractFilter<>() {
            @Override
            public boolean matches(CtStatement stmt) {
                return super.matches(stmt) && inserter.canMutate(stmt);
            }
        });
        AxChecker.check(statements.size() > 0, "No mutable statements found");

        CtStatement stmt = statements.get(AxRandom.getInstance().nextInt(statements.size()));
        AxLog.v("Mutating statement: ", (out, ignoreUnused) -> out.println(stmt));
        inserter.mutate(stmt);
    }
}
