package io.artemis.syn;

import io.artemis.AxRandom;
import io.artemis.util.Spoons;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.reference.CtArrayTypeReferenceImpl;
import spoon.support.reflect.reference.CtTypeReferenceImpl;

/* package */ class NewInstance extends Spoons.TypeSwitch<CtExpression<?>> {

    private Factory mFact;

    public NewInstance() {}

    @Override
    protected CtExpression<?> kaseArray(CtArrayTypeReferenceImpl<?> type) {
        CtNewArray<?> newArray = mFact.createNewArray();
        int dimenCount = type.getDimensionCount();
        if (dimenCount == 1) {
            int size = AxRandom.getInstance().nextInt(1, 10);
            for (int i = 0; i < size; i++) {
                newArray.addElement(svitch(type.getComponentType()));
            }
        } else {
            for (int i = 0; i < type.getDimensionCount(); i++) {
                newArray.addElement(svitch(type.getComponentType()));
            }
        }
        return newArray;
    }

    @Override
    protected CtExpression<?> kaseBoolean(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral(AxRandom.getInstance().nextBoolean());
    }

    @Override
    protected CtExpression<?> kaseByte(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral((byte) AxRandom.getInstance().nextInt());
    }

    @Override
    protected CtExpression<?> kaseShort(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral((short) AxRandom.getInstance().nextInt());
    }

    @Override
    protected CtExpression<?> kaseChar(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral((char) AxRandom.getInstance().nextInt(0, 256));
    }

    @Override
    protected CtExpression<?> kaseInt(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral(AxRandom.getInstance().nextInt());
    }

    @Override
    protected CtExpression<?> kaseLong(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral(AxRandom.getInstance().nextLong());
    }

    @Override
    protected CtExpression<?> kaseFloat(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral(AxRandom.getInstance().nextFloat());
    }

    @Override
    protected CtExpression<?> kaseDouble(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral(AxRandom.getInstance().nextDouble());
    }

    @Override
    protected CtExpression<?> kaseString(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral("1");
    }

    @Override
    protected CtExpression<?> kaseRef(CtTypeReferenceImpl<?> type) {
        return null;
    }

    // Never use the svitch() method
    public CtExpression<?> newInstance(Factory fact, CtTypeReference<?> type) {
        mFact = fact;
        return svitch(type);
    }
}
