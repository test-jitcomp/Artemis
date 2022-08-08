package io.artemis.syn;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

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
            // Don't be too large, otherwise it may occupy too much memory.
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
    protected CtExpression<?> kaseBoxedBoolean(CtTypeReferenceImpl<?> type) {
        return kaseBoolean(type);
    }

    @Override
    protected CtExpression<?> kaseByte(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral((byte) AxRandom.getInstance().nextInt());
    }

    @Override
    protected CtExpression<?> kaseBoxedByte(CtTypeReferenceImpl<?> type) {
        return kaseByte(type);
    }

    @Override
    protected CtExpression<?> kaseShort(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral((short) AxRandom.getInstance().nextInt());
    }

    @Override
    protected CtExpression<?> kaseBoxedShort(CtTypeReferenceImpl<?> type) {
        return kaseShort(type);
    }

    @Override
    protected CtExpression<?> kaseChar(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral((char) AxRandom.getInstance().nextInt(0, 0xFFFF));
    }

    @Override
    protected CtExpression<?> kaseBoxedChar(CtTypeReferenceImpl<?> type) {
        return kaseChar(type);
    }

    @Override
    protected CtExpression<?> kaseInt(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral(AxRandom.getInstance().nextInt());
    }

    @Override
    protected CtExpression<?> kaseBoxedInt(CtTypeReferenceImpl<?> type) {
        return kaseInt(type);
    }

    @Override
    protected CtExpression<?> kaseLong(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral(AxRandom.getInstance().nextLong());
    }

    @Override
    protected CtExpression<?> kaseBoxedLong(CtTypeReferenceImpl<?> type) {
        return kaseLong(type);
    }

    @Override
    protected CtExpression<?> kaseFloat(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral(AxRandom.getInstance().nextFloat());
    }

    @Override
    protected CtExpression<?> kaseBoxedFloat(CtTypeReferenceImpl<?> type) {
        return kaseFloat(type);
    }

    @Override
    protected CtExpression<?> kaseDouble(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral(AxRandom.getInstance().nextDouble());
    }

    @Override
    protected CtExpression<?> kaseBoxedDouble(CtTypeReferenceImpl<?> type) {
        return kaseDouble(type);
    }

    @Override
    protected CtExpression<?> kaseString(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral("1");
    }

    @Override
    protected CtExpression<?> kaseRef(CtTypeReferenceImpl<?> type) {
        // Let's load the class and check its constructors
        Class<?> clazz;
        try {
            clazz = Class.forName(type.getQualifiedName());
        } catch (ClassNotFoundException e) {
            clazz = null;
        }

        // No class found. Either not in classpath, or the qualified name is a bit quirky.
        if (clazz == null) {
            return mFact.createLiteral(null);
        }

        // Interfaces and abstract classes cannot be initialized
        if (clazz.isInterface()) {
            return mFact.createLiteral(null);
        } else if (Modifier.isAbstract(clazz.getModifiers())) {
            return mFact.createLiteral(null);
        }

        // Let's choose only the default constructor, otherwise we have to do some recursive
        // synthesis. It's a bit time-consuming so let's disable that temporarily.
        // TODO Support recursive new-object synthesis.
        Constructor<?> defCtor;
        try {
            defCtor = clazz.getConstructor();
        } catch (NoSuchMethodException e) {
            defCtor = null;
        }

        // No default constructor, let's just give it a null
        if (defCtor == null) {
            return mFact.createLiteral(null);
        }

        // There's a default constructor, then it's safe for us to new an instance
        return mFact.createConstructorCall(type);
    }

    // Never use the svitch() method
    public CtExpression<?> newInstance(Factory fact, CtTypeReference<?> type) {
        mFact = fact;
        return svitch(type);
    }
}
