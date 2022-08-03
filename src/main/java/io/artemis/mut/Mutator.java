package io.artemis.mut;

import io.artemis.Artemis;
import spoon.reflect.declaration.CtElement;

public abstract class Mutator {

    protected final Artemis mAx;

    public Mutator(Artemis ax) {
        mAx = ax;
    }

    public void set(Artemis.ExtraOpts settings) {}

    public abstract boolean canMutate(CtElement element);

    public abstract void mutate(CtElement element);
}
