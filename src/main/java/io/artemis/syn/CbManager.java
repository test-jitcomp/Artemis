package io.artemis.syn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.function.Consumer;

import io.artemis.AxChecker;
import io.artemis.AxNames;
import io.artemis.util.CannotReachHereException;
import io.artemis.util.Spoons;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.reference.CtArrayTypeReferenceImpl;
import spoon.support.reflect.reference.CtTypeReferenceImpl;

/**
 * CbManager manages all code bricks and initializers that we have collected. Since there's numerous
 * bricks and initializers, it follows a lazy load manner to load them. There's an implicit defining
 * convention of our bricks and initializers. This class assumes our bricks and initializers are
 * already in a correct format (i.e., following our conventions) and thereby it does not
 * additionally check that convention. Specifically,
 * // @formatter:off
 * (1) Each code brick should be named as CB_INITZ_CLASS_NAME_PREFIX_{i}.java and there's a method 
 *     namely CB_METHOD_NAME saving all the brick. Parameters of that method are inputs (holes).
 * (2) All initializers are managed according to their types. Each initializer should be set as a
 *     field of its specific INITZ_CLASS_NAME_{type} class. For initializers of primitive types,
 *     should guarantee that the initializer can be safely assigned to the boxed type.
 * // @formatter:on
 */
/* package */ class CbManager {

    private static final String MANIFEST_NAME = "MANIFEST";
    private static final String MANIFEST_LINE_CB_INITZ_PREFIX = "InitzCount=";
    private static final String MANIFEST_LINE_CB_PREFIX = "CbCount=";
    private static final String CB_INITZ_CLASS_NAME_PREFIX = "InitzCls";
    private static final String INITZ_CLASS_NAME_STRING = CB_INITZ_CLASS_NAME_PREFIX + "String";
    private static final String INITZ_CLASS_NAME_ARRAY = CB_INITZ_CLASS_NAME_PREFIX + "Array";
    private static final String INITZ_CLASS_NAME_REF = CB_INITZ_CLASS_NAME_PREFIX + "Ref";
    private static final String INITZ_CLASS_NAME_BYTE = CB_INITZ_CLASS_NAME_PREFIX + "Byte";
    private static final String INITZ_CLASS_NAME_BOOLEAN = CB_INITZ_CLASS_NAME_PREFIX + "Boolean";
    private static final String INITZ_CLASS_NAME_SHORT = CB_INITZ_CLASS_NAME_PREFIX + "Short";
    private static final String INITZ_CLASS_NAME_CHAR = CB_INITZ_CLASS_NAME_PREFIX + "Char";
    private static final String INITZ_CLASS_NAME_INT = CB_INITZ_CLASS_NAME_PREFIX + "Int";
    private static final String INITZ_CLASS_NAME_LONG = CB_INITZ_CLASS_NAME_PREFIX + "Long";
    private static final String INITZ_CLASS_NAME_FLOAT = CB_INITZ_CLASS_NAME_PREFIX + "Float";
    private static final String INITZ_CLASS_NAME_DOUBLE = CB_INITZ_CLASS_NAME_PREFIX + "Double";
    private static final String CB_CLASS_NAME_PREFIX = "TplClass";
    private static final String CB_METHOD_NAME = "method";

    // Code bricks: lazy load
    private final File mCbFolder;
    private int mCbCount;

    // Initializers: lazy load
    private final InitzLazyLoader mInitzLoader;
    private int mInitzCount;
    private CtClass<?> mInitzClsArray;
    private CtClass<?> mInitzClsRef;
    private CtClass<?> mInitzClsByte;
    private CtClass<?> mInitzClsBoolean;
    private CtClass<?> mInitzClsShort;
    private CtClass<?> mInitzClsChar;
    private CtClass<?> mInitzClsInt;
    private CtClass<?> mInitzClsLong;
    private CtClass<?> mInitzClsFloat;
    private CtClass<?> mInitzClsDouble;
    private CtClass<?> mInitzClsString;

    public CbManager(File cbFolder) {
        mCbFolder = cbFolder;
        mCbCount = -1;
        mInitzCount = -1;
        mInitzLoader = new InitzLazyLoader();
    }

    public void init() throws IOException {
        // Read manifest to learn the number of initializer and templates
        File mani = new File(mCbFolder, MANIFEST_NAME);
        BufferedReader reader = new BufferedReader(new FileReader(mani));
        String newLine;
        while ((newLine = reader.readLine()) != null) {
            if (newLine.startsWith(MANIFEST_LINE_CB_INITZ_PREFIX)) {
                mInitzCount =
                        Integer.parseInt(newLine.substring(MANIFEST_LINE_CB_INITZ_PREFIX.length()));
            } else if (newLine.startsWith(MANIFEST_LINE_CB_PREFIX)) {
                mCbCount = Integer.parseInt(newLine.substring(MANIFEST_LINE_CB_PREFIX.length()));
            }
        }
    }

    public int getCbCount() {
        return mCbCount;
    }

    public int getInitzCount() {
        return mInitzCount;
    }

    public void forEachInitz(CtTypeReference<?> type, Consumer<CtExpression<?>> con) {
        CtClass<?> initzCls = mInitzLoader.ensureLoaded(type);
        if (initzCls == mInitzClsArray || initzCls == mInitzClsRef) {
            initzCls.getFields().forEach(field -> {
                if (field.getType().equals(type)) {
                    con.accept(field.getAssignment());
                }
            });
        } else {
            initzCls.getFields().forEach(field -> con.accept(field.getAssignment()));
        }
    }

    public CodeBrick getCodeBrick(int index) {
        String cbClassName = CB_CLASS_NAME_PREFIX + index;
        File cbFile =
                new File(mCbFolder.getAbsolutePath() + File.separator + cbClassName + ".java");
        AxChecker.check(cbFile.exists(), "Code brick class not found: " + cbFile.getAbsolutePath());

        // We assume that the code brick class have the same name as the file
        CtClass<?> cbClass = Spoons.ensureClassLoaded(cbFile.getAbsolutePath(), cbClassName);
        AxChecker.check(cbClass.getMethods().size() == 1, "The code brick has >=1 code bricks");

        CtMethod<?> cbMethod = null;
        try {
            cbMethod = cbClass.getMethodsByName(CB_METHOD_NAME).get(0);
        } catch (IndexOutOfBoundsException e) {
            // noinspection ConstantConditions
            AxChecker.check(false, "No code brick namely " + CB_METHOD_NAME + "() found");
        }
        AxChecker.check(cbMethod != null, "No code brick namely " + CB_METHOD_NAME + "() found");
        AxChecker.check(cbMethod.getBody() != null,
                "No statements found in code brick " + cbClassName + "#" + CB_METHOD_NAME + "()");

        // Rename every parameter (i.e., input of the brick) and local variable
        // such that we don't conflict when instantiating the brick.
        for (CtParameter<?> param : cbMethod.getParameters()) {
            Spoons.renameVariable(param, AxNames.getInstance().nextName());
        }
        for (CtLocalVariable<?> local : cbMethod
                .getElements(new TypeFilter<>(CtLocalVariable.class))) {
            Spoons.renameVariable(local, AxNames.getInstance().nextName());
        }

        return new CodeBrick(cbMethod);
    }

    private class InitzLazyLoader extends Spoons.TypeSwitch<CtClass<?>> {

        @Override
        protected CtClass<?> kaseArray(CtArrayTypeReferenceImpl<?> type) {
            if (mInitzClsArray == null) {
                mInitzClsArray = doLoad(INITZ_CLASS_NAME_ARRAY);
            }
            return mInitzClsArray;
        }

        @Override
        protected CtClass<?> kaseVoid(CtTypeReferenceImpl<?> type) {
            throw new CannotReachHereException("Cannot have initializers for primitive type void");
        }

        @Override
        protected CtClass<?> kaseBoxedVoid(CtTypeReferenceImpl<?> type) {
            // We treat boxed void as a reference
            return kaseRef(type);
        }

        @Override
        protected CtClass<?> kaseBoolean(CtTypeReferenceImpl<?> type) {
            if (mInitzClsBoolean == null) {
                mInitzClsBoolean = doLoad(INITZ_CLASS_NAME_BOOLEAN);
            }
            return mInitzClsBoolean;
        }

        @Override
        protected CtClass<?> kaseBoxedBoolean(CtTypeReferenceImpl<?> type) {
            return kaseBoolean(type);
        }

        @Override
        protected CtClass<?> kaseByte(CtTypeReferenceImpl<?> type) {
            if (mInitzClsByte == null) {
                mInitzClsByte = doLoad(INITZ_CLASS_NAME_BYTE);
            }
            return mInitzClsByte;
        }

        @Override
        protected CtClass<?> kaseBoxedByte(CtTypeReferenceImpl<?> type) {
            return kaseByte(type);
        }

        @Override
        protected CtClass<?> kaseShort(CtTypeReferenceImpl<?> type) {
            if (mInitzClsShort == null) {
                mInitzClsShort = doLoad(INITZ_CLASS_NAME_SHORT);
            }
            return mInitzClsShort;
        }

        @Override
        protected CtClass<?> kaseBoxedShort(CtTypeReferenceImpl<?> type) {
            return kaseShort(type);
        }

        @Override
        protected CtClass<?> kaseChar(CtTypeReferenceImpl<?> type) {
            if (mInitzClsChar == null) {
                mInitzClsChar = doLoad(INITZ_CLASS_NAME_CHAR);
            }
            return mInitzClsChar;
        }

        @Override
        protected CtClass<?> kaseBoxedChar(CtTypeReferenceImpl<?> type) {
            return kaseChar(type);
        }

        @Override
        protected CtClass<?> kaseInt(CtTypeReferenceImpl<?> type) {
            if (mInitzClsInt == null) {
                mInitzClsInt = doLoad(INITZ_CLASS_NAME_INT);
            }
            return mInitzClsInt;
        }

        @Override
        protected CtClass<?> kaseBoxedInt(CtTypeReferenceImpl<?> type) {
            return kaseInt(type);
        }

        @Override
        protected CtClass<?> kaseLong(CtTypeReferenceImpl<?> type) {
            if (mInitzClsLong == null) {
                mInitzClsLong = doLoad(INITZ_CLASS_NAME_LONG);
            }
            return mInitzClsLong;
        }

        @Override
        protected CtClass<?> kaseBoxedLong(CtTypeReferenceImpl<?> type) {
            return kaseLong(type);
        }

        @Override
        protected CtClass<?> kaseFloat(CtTypeReferenceImpl<?> type) {
            if (mInitzClsFloat == null) {
                mInitzClsFloat = doLoad(INITZ_CLASS_NAME_FLOAT);
            }
            return mInitzClsFloat;
        }

        @Override
        protected CtClass<?> kaseBoxedFloat(CtTypeReferenceImpl<?> type) {
            return kaseFloat(type);
        }

        @Override
        protected CtClass<?> kaseDouble(CtTypeReferenceImpl<?> type) {
            if (mInitzClsDouble == null) {
                mInitzClsDouble = doLoad(INITZ_CLASS_NAME_DOUBLE);
            }
            return mInitzClsDouble;
        }

        @Override
        protected CtClass<?> kaseBoxedDouble(CtTypeReferenceImpl<?> type) {
            return kaseDouble(type);
        }

        @Override
        protected CtClass<?> kaseString(CtTypeReferenceImpl<?> type) {
            if (mInitzClsString == null) {
                mInitzClsString = doLoad(INITZ_CLASS_NAME_STRING);
            }
            return mInitzClsString;
        }

        @Override
        protected CtClass<?> kaseRef(CtTypeReferenceImpl<?> type) {
            if (mInitzClsRef == null) {
                mInitzClsRef = doLoad(INITZ_CLASS_NAME_REF);
            }
            return mInitzClsRef;
        }

        public CtClass<?> ensureLoaded(CtTypeReference<?> type) {
            return svitch(type);
        }

        private CtClass<?> doLoad(String className) {
            File initzFile =
                    new File(mCbFolder.getAbsolutePath() + File.separator + className + ".java");
            AxChecker.check(initzFile.exists(),
                    "Initz class not found: " + initzFile.getAbsolutePath());
            return Spoons.ensureClassLoaded(initzFile.getAbsolutePath(), className);
        }
    }
}
