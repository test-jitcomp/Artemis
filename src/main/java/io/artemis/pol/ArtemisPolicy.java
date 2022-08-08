package io.artemis.pol;

import java.util.ArrayList;
import java.util.List;

import io.artemis.Artemis;
import io.artemis.AxChecker;
import io.artemis.AxLog;
import io.artemis.AxRandom;
import io.artemis.mut.LoopInserter;
import io.artemis.mut.StmtWrapper;
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

        // We never mutate initializer blocks, either static or not
        List<CtMethod<?>> methods = new ArrayList<>(clazz.getMethods());
        AxChecker.check(methods.size() > 0,
                "No methods found in the given class: " + clazz.getQualifiedName());

        for (CtMethod<?> meth : methods) {
            if (rand.nextBoolean()) {
                AxLog.v("Don't mutating method: " + clazz.getQualifiedName() + "::"
                        + meth.getSimpleName() + "()");
                continue;
            }

            float prob = rand.nextFloat();
            if (prob <= 0.5f) {
                LoopInserter inserter = new LoopInserter(mAx);

                AxLog.v("Mutating (LoopInserter) method: " + clazz.getQualifiedName() + "::"
                        + meth.getSimpleName() + "()");
                List<CtStatement> statements = meth.getElements(new AbstractFilter<>() {
                    @Override
                    public boolean matches(CtStatement stmt) {
                        return super.matches(stmt) && inserter.canMutate(stmt);
                    }
                });
                AxChecker.check(statements.size() > 0, "No mutable statements found");

                CtStatement stmt =
                        statements.get(AxRandom.getInstance().nextInt(statements.size()));
                AxLog.v("Inserting to statement: ", (out, ignoreUnused) -> out.println(stmt));
                inserter.mutate(stmt);
            } else {
                StmtWrapper wrapper = new StmtWrapper(mAx);

                AxLog.v("Mutating (StmtWrapper) method: " + clazz.getQualifiedName() + "::"
                        + meth.getSimpleName() + "()");
                List<CtStatement> statements = meth.getElements(new AbstractFilter<>() {
                    @Override
                    public boolean matches(CtStatement stmt) {
                        return super.matches(stmt) && wrapper.canMutate(stmt);
                    }
                });

                CtStatement stmt =
                        statements.get(AxRandom.getInstance().nextInt(statements.size()));
                AxLog.v("Wrapping statement: ", (out, ignoreUnused) -> out.println(stmt));
                wrapper.mutate(stmt);
            }
        }
    }
}
