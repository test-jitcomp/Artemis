package io.artemis.syn;

import java.util.List;

import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.support.util.ModelList;

/**
 * A code brick is a special type of code skeleton which have inputs and a list of statements. To
 * use a code brick, synthesize a declaration for each input and link the statement. Sometimes, you
 * may need to import the imports (but this often does not need since Spoon can take care of auto
 * imports in its setting by Spoon.getEnvironment().setAutoImports(true)).
 */
/* package */ class CodeBrick {
    // The method that hangs the code brick
    private final CtMethod<?> mMethod;
    private final ModelList<CtImport> mImports;

    /**
     * Get the inputs of this code brick. Just take care. The inputs returned are already linked. So
     * please be sure to clone if they are expected to use elsewhere.
     * 
     * @return The inputs of this code brick.
     */
    public CtParameter<?>[] unsafeGetInputs() {
        List<CtParameter<?>> params = mMethod.getParameters();
        CtParameter<?>[] inputs = new CtParameter<?>[params.size()];
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = params.get(i);
        }
        return inputs;
    }

    /**
     * Get all statements of this code brick. Just take care. The statements returned are already
     * linked. So please be sure to clone if they are expected to use elsewhere.
     * 
     * @return All statements of this code brick.
     */
    public CtBlock<?> unsafeGetStatements() {
        return mMethod.getBody();
    }

    /**
     * Get required imports if using this code brick elsewhere. The imports returned are already
     * linked. So please be sure to clone if they are expected to use elsewhere.
     * 
     * @return Set of imports
     */
    public ModelList<CtImport> unsafeGetImports() {
        return mImports;
    }

    @Override
    public String toString() {
        return mMethod.toString();
    }

    /* package */ CodeBrick(CtMethod<?> cbMethod, ModelList<CtImport> imports) {
        mMethod = cbMethod;
        mImports = imports;
    }
}
