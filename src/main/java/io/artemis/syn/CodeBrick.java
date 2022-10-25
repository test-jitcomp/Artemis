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
    private final int mId;
    // The method that hangs the code brick
    private final CtMethod<?> mMethod;
    // Required imports when using this brick elsewhere
    private final ModelList<CtImport> mImports;

    public int getId() {
        return mId;
    }

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

    /* package */ CodeBrick(int id, CtMethod<?> cbMethod, ModelList<CtImport> imports) {
        mId = id;
        mMethod = cbMethod;
        mImports = imports;
    }
}
