package io.artemis.mut;

import io.artemis.Artemis;
import spoon.reflect.declaration.CtElement;

public abstract class Mutator {

    protected final Artemis mAx;

    public Mutator(Artemis ax) {
        mAx = ax;
    }

    public void setExtraOptions(Artemis.ExtraOpts opts) {}

    /**
     * Test whether the given element can be mutated by this mutator. Always call this method before
     * calling mutate(); otherwise, the mutator cannot guarantee the mutation behavior. When
     * overriding this method, always call super().
     * 
     * @param element The element to mutate
     * @return Return true if the mutator can mutate element, or false
     */
    public boolean canMutate(CtElement element) {
        // The element should in the source file (i.e., not synthetic)
        return element.getPosition().isValidPosition();
    }

    public abstract void mutate(CtElement element);
}
