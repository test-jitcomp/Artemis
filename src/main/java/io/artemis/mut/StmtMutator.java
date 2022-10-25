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
        if (!super.canMutate(element) || !(element instanceof CtStatement)) {
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
