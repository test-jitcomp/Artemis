package io.artemis.util;

import io.artemis.Artemis;
import io.artemis.AxChecker;
import io.artemis.AxLog;
import spoon.FluentLauncher;
import spoon.refactoring.CtRenameGenericVariableRefactoring;
import spoon.refactoring.RefactoringException;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.reference.CtArrayTypeReferenceImpl;
import spoon.support.reflect.reference.CtTypeReferenceImpl;

public class Spoons {

    public static abstract class TypeSwitch<T> {

        protected abstract T kaseArray(CtArrayTypeReferenceImpl<?> type);

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
                    case "boolean":
                    case "java.lang.Boolean":
                        return kaseBoolean((CtTypeReferenceImpl<?>) type);
                    case "byte":
                    case "java.lang.Byte":
                        return kaseByte((CtTypeReferenceImpl<?>) type);
                    case "short":
                    case "java.lang.Short":
                        return kaseShort((CtTypeReferenceImpl<?>) type);
                    case "char":
                    case "java.lang.Character":
                        return kaseChar((CtTypeReferenceImpl<?>) type);
                    case "int":
                    case "java.lang.Integer":
                        return kaseInt((CtTypeReferenceImpl<?>) type);
                    case "long":
                    case "java.lang.Long":
                        return kaseLong((CtTypeReferenceImpl<?>) type);
                    case "float":
                    case "java.lang.Float":
                        return kaseFloat((CtTypeReferenceImpl<?>) type);
                    case "double":
                    case "java.lang.Double":
                        return kaseDouble((CtTypeReferenceImpl<?>) type);
                    case "java.lang.String":
                        return kaseString((CtTypeReferenceImpl<?>) type);
                    default:
                        return kaseRef((CtTypeReferenceImpl<?>) type);
                }
            }
        }
    }

    public static CtClass<?> ensureClassLoaded(String path, String className) {
        CtClass<?> clazz =
                new FluentLauncher().inputResource(path).complianceLevel(Artemis.JAVA_VERSION)
                        .buildModel().getRootPackage().getType(className);
        AxChecker.check(clazz != null, "Class " + className + " is not found in file: " + path);
        return clazz;
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

    public static boolean isPrimitiveType(CtTypeReference<?> type) {
        return new TypeSwitch<Boolean>() {
            @Override
            public Boolean kaseArray(CtArrayTypeReferenceImpl<?> type) {
                return false;
            }

            @Override
            protected Boolean kaseBoolean(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseByte(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseShort(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseChar(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseInt(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseLong(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseFloat(CtTypeReferenceImpl<?> type) {
                return true;
            }

            @Override
            public Boolean kaseDouble(CtTypeReferenceImpl<?> type) {
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
