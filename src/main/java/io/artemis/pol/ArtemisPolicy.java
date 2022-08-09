package io.artemis.pol;

import java.util.ArrayList;
import java.util.List;

import io.artemis.Artemis;
import io.artemis.AxChecker;
import io.artemis.AxLog;
import io.artemis.AxRandom;
import io.artemis.mut.LoopInserter;
import io.artemis.mut.MethInvocator;
import io.artemis.mut.MethMutator;
import io.artemis.mut.Mutator;
import io.artemis.mut.StmtMutator;
import io.artemis.mut.StmtWrapper;
import io.artemis.util.Spoons;
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
            // Let's flip a coin to decide whether to mutate meth or not
            if (rand.nextBoolean()) {
                float prob = rand.nextFloat();
                Mutator mut;
                if (prob <= 0.33f) {
                    mut = new LoopInserter(mAx);
                } else if (0.33f < prob && prob <= 0.67f) {
                    mut = new StmtWrapper(mAx);
                } else {
                    mut = new MethInvocator(mAx);
                }
                AxLog.v("Flip coin (front): mutating method " + Spoons.getSimpleName(meth) + " by "
                        + mut.getClass().getSimpleName());
                doApply(mut, meth);
            } else {
                AxLog.v("Flip coin (back): don't mutate method: " + Spoons.getSimpleName(meth));
            }
        }
    }

    private void doApply(Mutator mut, CtMethod<?> meth) {
        if (mut instanceof StmtMutator) {
            doApply((StmtMutator) mut, meth);
        } else if (mut instanceof MethMutator) {
            doApply((MethMutator) mut, meth);
        }
    }

    private void doApply(MethMutator mut, CtMethod<?> meth) {
        if (mut.canMutate(meth)) {
            mut.mutate(meth);
        } else {
            AxLog.v("The method cannot be mutated, abandon");
        }
    }

    private void doApply(StmtMutator mut, CtMethod<?> meth) {
        List<CtStatement> statements = meth.getElements(new AbstractFilter<>() {
            @Override
            public boolean matches(CtStatement stmt) {
                return super.matches(stmt) && mut.canMutate(stmt);
            }
        });

        if (statements.size() == 0) {
            AxLog.v("No available statements to mutate, abandon");
            return;
        }

        CtStatement stmt = statements.get(AxRandom.getInstance().nextInt(statements.size()));
        AxLog.v("Mutating statement", (out, ignoreUnused) -> out.println(stmt));
        mut.mutate(stmt);
    }
}
