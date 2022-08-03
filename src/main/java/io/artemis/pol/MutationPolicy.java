package io.artemis.pol;

import io.artemis.Artemis;
import spoon.reflect.declaration.CtClass;

public abstract class MutationPolicy {

    protected Artemis mAx;

    public MutationPolicy(Artemis ax, Artemis.ExtraOpts ignoreUnused) {
        mAx = ax;
    }

    public abstract void apply(CtClass<?> clazz);
}
