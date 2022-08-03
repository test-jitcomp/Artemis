package io.artemis.syn;

import java.util.List;
import java.util.stream.Collectors;

import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.support.util.ModelList;

/* package */ class CodeBrick {
    /* package */ static final String CODE_BRICK_METHOD_NAME = "method";

    // The method that hangs the code brick
    private final CtMethod<?> mMethod;
    private final List<CtImport> mImports;

    public CtParameter<?>[] getInputs() {
        List<CtParameter<?>> params = mMethod.getParameters();
        CtParameter<?>[] inputs = new CtParameter<?>[params.size()];
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = params.get(i).clone();
        }
        return inputs;
    }

    public CtBlock<?> getStatements() {
        return mMethod.getBody().clone();
    }

    public List<String> getImports() {
        return mImports.stream().map(i -> i.getReference().getSimpleName())
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return mMethod.toString();
    }

    /* package */ List<CtParameter<?>> unsafeGetInput() {
        return mMethod.getParameters();
    }

    /* package */ CodeBrick(CtMethod<?> cbMethod, List<CtImport> cbImports) {
        mMethod = cbMethod;
        mImports = cbImports;
    }
}
