/**
 * MIT License
 * 
 * Copyright (c) 2021 Cong Li (congli@smail.nju.edu.cn, cong.li@inf.ethz.ch)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.artemis.syn;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import io.artemis.AxRandom;
import io.artemis.util.CannotReachHereException;
import io.artemis.util.Spoons;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.reference.CtArrayTypeReferenceImpl;
import spoon.support.reflect.reference.CtTypeReferenceImpl;

/* package */ class NewInstance extends Spoons.TypeSwitch<CtExpression<?>> {

    private Factory mFact;

    public NewInstance() {}

    @Override
    protected CtExpression<?> kaseArray(CtArrayTypeReferenceImpl<?> type) {
        CtNewArray<?> array = mFact.createNewArray();
        CtArrayTypeReference<?> arrayType = type.clone();
        CtTypeReference<?> arrayFinestType = arrayType.getArrayType();
        // Remove generics (aka type arguments) to avoid generic array creation
        if (!arrayFinestType.getActualTypeArguments().isEmpty()) {
            arrayFinestType.setActualTypeArguments(new ArrayList<>());
        }
        array.setType((CtTypeReference) arrayType);
        int dimenCount = type.getDimensionCount();
        if (dimenCount == 1) {
            // Don't be too large, otherwise it may occupy too much memory.
            int size = AxRandom.getInstance().nextInt(1, 10);
            for (int i = 0; i < size; i++) {
                array.addElement(svitch(type.getComponentType()));
            }
        } else {
            for (int i = 0; i < type.getDimensionCount(); i++) {
                array.addElement(svitch(type.getComponentType()));
            }
        }
        return array;
    }

    @Override
    protected CtExpression<?> kaseVoid(CtTypeReferenceImpl<?> type) {
        throw new CannotReachHereException("Cannot create an instance for void primitive");
    }

    @Override
    protected CtExpression<?> kaseBoxedVoid(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral(null);
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
        return mFact.createLiteral((byte) AxRandom.getInstance().nextInt())
                .addTypeCast(mFact.createCtTypeReference(byte.class));
    }

    @Override
    protected CtExpression<?> kaseBoxedByte(CtTypeReferenceImpl<?> type) {
        return kaseByte(type);
    }

    @Override
    protected CtExpression<?> kaseShort(CtTypeReferenceImpl<?> type) {
        return mFact.createLiteral((short) AxRandom.getInstance().nextInt())
                .addTypeCast(mFact.createCtTypeReference(short.class));
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
        return mFact.createLiteral("s");
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

        // If the constructor explicitly throw, give it a null
        if (defCtor.getExceptionTypes().length != 0) {
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
