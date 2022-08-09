package io.artemis.mut;

import io.artemis.Artemis;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;

public abstract class MethMutator extends Mutator {

    public MethMutator(Artemis ax) {
        super(ax);
    }

    @Override
    public final boolean canMutate(CtElement element) {
        if (!(element instanceof CtMethod)) {
            return false;
        }
        CtMethod<?> meth = (CtMethod<?>) element;
        if (meth.isAbstract()) {
            return false;
        }
        return canMutate(meth);
    }

    @Override
    public final void mutate(CtElement element) {
        mutate((CtMethod<?>) element);
    }

    protected abstract boolean canMutate(CtMethod<?> meth);

    protected abstract void mutate(CtMethod<?> meth);
}
