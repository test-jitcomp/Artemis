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

package io.artemis.util;

import java.util.ArrayList;
import java.util.List;

import io.artemis.Artemis;
import io.artemis.AxChecker;
import io.artemis.AxLog;
import spoon.Launcher;
import spoon.OutputType;
import spoon.SpoonAPI;
import spoon.refactoring.CtRenameGenericVariableRefactoring;
import spoon.refactoring.RefactoringException;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.reflect.reference.CtArrayTypeReferenceImpl;
import spoon.support.reflect.reference.CtTypeReferenceImpl;

public class Spoons {

    public static abstract class TypeSwitch<T> {

        protected abstract T kaseArray(CtArrayTypeReferenceImpl<?> type);

        protected abstract T kaseBoxedVoid(CtTypeReferenceImpl<?> type);

        protected abstract T kaseBoxedBoolean(CtTypeReferenceImpl<?> type);

        protected abstract T kaseBoxedByte(CtTypeReferenceImpl<?> type);

        protected abstract T kaseBoxedShort(CtTypeReferenceImpl<?> type);

        protected abstract T kaseBoxedChar(CtTypeReferenceImpl<?> type);

        protected abstract T kaseBoxedInt(CtTypeReferenceImpl<?> type);

        protected abstract T kaseBoxedLong(CtTypeReferenceImpl<?> type);

        protected abstract T kaseBoxedFloat(CtTypeReferenceImpl<?> type);

        protected abstract T kaseBoxedDouble(CtTypeReferenceImpl<?> type);

        protected abstract T kaseVoid(CtTypeReferenceImpl<?> type);

        protected abstract T kaseBoolean(CtTypeReferenceImpl<?> type);

        protected abstract T kaseByte(CtTypeReferenceImpl<?> type);

        protected abstract T kaseShort(CtTypeReferenceImpl<?> type);

        protected abstract T kaseChar(CtTypeReferenceImpl<?> type);

        protected abstract T kaseInt(CtTypeReferenceImpl<?> type);

        protected abstract T kaseLong(CtTypeReferenceImpl<?> type);

        protected abstract T kaseFloat(CtTypeReferenceImpl<?> type);

        protected abstract T kaseDouble(CtTypeReferenceImpl<?> type);

        protected abstract T kaseString(CtTypeReferenceImpl<?> type);

        protected abstract T kaseRef(CtTypeReferenceImpl<?> type);

        public final T svitch(CtTypeReference<?> type) {
            if (type instanceof CtArrayTypeReferenceImpl) {
                return kaseArray((CtArrayTypeReferenceImpl<?>) type);
            } else {
                switch (type.getQualifiedName()) {
                    case "void":
                        return kaseVoid((CtTypeReferenceImpl<?>) type);
                    case "java.lang.Void":
                        return kaseBoxedVoid((CtTypeReferenceImpl<?>) type);
                    case "boolean":
                        return kaseBoolean((CtTypeReferenceImpl<?>) type);
                    case "java.lang.Boolean":
                        return kaseBoxedBoolean((CtTypeReferenceImpl<?>) type);
                    case "byte":
                        return kaseByte((CtTypeReferenceImpl<?>) type);
                    case "java.lang.Byte":
                        return kaseBoxedByte((CtTypeReferenceImpl<?>) type);
                    case "short":
                        return kaseShort((CtTypeReferenceImpl<?>) type);
                    case "java.lang.Short":
                        return kaseBoxedShort((CtTypeReferenceImpl<?>) type);
                    case "char":
                        return kaseChar((CtTypeReferenceImpl<?>) type);
                    case "java.lang.Character":
                        return kaseBoxedChar((CtTypeReferenceImpl<?>) type);
                    case "int":
                        return kaseInt((CtTypeReferenceImpl<?>) type);
                    case "java.lang.Integer":
                        return kaseBoxedInt((CtTypeReferenceImpl<?>) type);
                    case "long":
                        return kaseLong((CtTypeReferenceImpl<?>) type);
                    case "java.lang.Long":
                        return kaseBoxedLong((CtTypeReferenceImpl<?>) type);
                    case "float":
                        return kaseFloat((CtTypeReferenceImpl<?>) type);
                    case "java.lang.Float":
                        return kaseBoxedFloat((CtTypeReferenceImpl<?>) type);
                    case "double":
                        return kaseDouble((CtTypeReferenceImpl<?>) type);
                    case "java.lang.Double":
                        return kaseBoxedDouble((CtTypeReferenceImpl<?>) type);
                    case "java.lang.String":
                        return kaseString((CtTypeReferenceImpl<?>) type);
                    default:
                        return kaseRef((CtTypeReferenceImpl<?>) type);
                }
            }
        }
    }

    public static String getSimpleName(CtField<?> field) {
        return field.getDeclaringType().getQualifiedName() + "." + field.getSimpleName();
    }

    public static String getSimpleName(CtMethod<?> meth) {
        return meth.getDeclaringType().getQualifiedName() + "::" + meth.getSimpleName() + "()";
    }

    public static CtCompilationUnit getCompUnit(CtElement e) {
        AxChecker.check(e.getPosition().isValidPosition(), "Invalid position: " + e);
        return e.getPosition().getCompilationUnit();
    }

    public static List<CtStatement> flat(CtStatement blk) {
        if (blk instanceof CtStatementList) {
            List<CtStatement> blkStmts = new ArrayList<>(((CtStatementList) blk).getStatements());
            blkStmts.forEach(CtElement::delete);
            return blkStmts;
        } else {
            return List.of(blk);
        }
    }

    // Insert statement ``before'' after the statement where ``ele'' is residing
    public static void insertBeforeStmt(CtElement ele, CtStatement before) {
        CtElement after = ele;
        while (after != null) {
            if (after instanceof CtStatement && after.getParent() instanceof CtStatementList) {
                break;
            }
            after = after.getParent();
        }
        AxChecker.check(after != null, "Element does not reside in a statement");
        ((CtStatement) after).insertBefore(before);
    }

    public static CtClass<?> ensureClassLoaded(String path, String className) {
        for (CtType<?> type : ensureCompUnitLoaded(path).getDeclaredTypes()) {
            if (type instanceof CtClass && type.getQualifiedName().equals(className)) {
                return (CtClass<?>) type;
            }
        }
        // noinspection ConstantConditions
        AxChecker.check(false, "Class " + className + " is not found in file: " + path);
        throw new CannotReachHereException("After assertion");
    }

    public static CtCompilationUnit ensureCompUnitLoaded(String path) {
        SpoonAPI spoon = new Launcher();
        spoon.getEnvironment().setComplianceLevel(Artemis.JAVA_VERSION);
        spoon.getEnvironment().setNoClasspath(true);
        spoon.getEnvironment().setAutoImports(true);
        spoon.getEnvironment().setOutputType(OutputType.NO_OUTPUT);
        spoon.getEnvironment().setCopyResources(false);
        spoon.addInputResource(path);
        spoon.buildModel();

        CtCompilationUnit unit = spoon.getFactory().CompilationUnit().getOrCreate(path);
        AxChecker.check(unit != null, "Compilation unit is not found in file: " + path);

        return unit;
    }

    public static void renameVariable(CtVariable<?> var, String newName) {
        CtRenameGenericVariableRefactoring refactor = new CtRenameGenericVariableRefactoring();
        refactor.setTarget(var);
        refactor.setNewName(newName);
        try {
            refactor.refactor();
        } catch (RefactoringException e) {
            AxLog.w(e.getMessage());
        }
    }

    public static <T extends CtElement> T mark(T ele, String key) {
        CtScanner markScanner = new CtScanner() {
            @Override
            protected void enter(CtElement e) {
                e.putMetadata(key, true);
            }
        };
        markScanner.scan(ele);
        return ele;
    }

    public static boolean isMarked(CtElement ele, String key) {
        return ele.getMetadata(key) != null;
    }

    public static boolean isVoidType(CtTypeReference<?> type) {
        return type.getQualifiedName().equals("void");
    }

    public static boolean isPrimitiveAlikeType(CtTypeReference<?> type) {
        return new TypeSwitch<Boolean>() {
            @Override
            public Boolean kaseArray(CtArrayTypeReferenceImpl<?> type) {
                return false;
            }

            @Override
            protected Boolean kaseVoid(CtTypeReferenceImpl<?> type) {
                return false;
            }

            @Override
            protected Boolean kaseBoxedVoid(CtTypeReferenceImpl<?> type) {
                return false;
            }

            @Override
            protected Boolean kaseBoolean(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            protected Boolean kaseBoxedBoolean(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseByte(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            protected Boolean kaseBoxedByte(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseShort(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            protected Boolean kaseBoxedShort(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseChar(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            protected Boolean kaseBoxedChar(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseInt(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            protected Boolean kaseBoxedInt(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseLong(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            protected Boolean kaseBoxedLong(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseFloat(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            protected Boolean kaseBoxedFloat(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseDouble(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            protected Boolean kaseBoxedDouble(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseString(CtTypeReferenceImpl<?> type) {
                return false;
            }

            @Override
            public Boolean kaseRef(CtTypeReferenceImpl<?> type) {
                return false;
            }
        }.svitch(type);
    }
}
