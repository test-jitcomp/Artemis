package io.artemis.mut;

import io.artemis.Artemis;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtElement;

public abstract class StmtMutator extends Mutator {

    public StmtMutator(Artemis ax) {
        super(ax);
    }

    @Override
    public final boolean canMutate(CtElement element) {
        if (!(element instanceof CtStatement)) {
            return false;
        }
        CtStatement stmt = (CtStatement) element;
        CtElement parent = stmt.getParent();
        // Every statement we mutate should reside in a statement list, thus
        // we directly reject those not satisfying this requirement like
        // CtUnaryOperator and CtCase. This is a weired design that Spoon treats
        // CtUnaryOperator as a subtype of CtStatement.
        return parent instanceof CtStatementList && canMutate(stmt);
    }

    @Override
    public final void mutate(CtElement element) {
        mutate((CtStatement) element);
    }

    protected abstract boolean canMutate(CtStatement stmt);

    protected abstract void mutate(CtStatement stmt);
}
