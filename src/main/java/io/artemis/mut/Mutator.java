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
import spoon.reflect.declaration.CtElement;

public abstract class Mutator {

    protected final Artemis mAx;

    public Mutator(Artemis ax) {
        mAx = ax;
    }

    public void setExtraOptions(Artemis.ExtraOpts opts) {}

    /**
     * Test whether the given element can be mutated by this mutator. Always call this method before
     * calling mutate(); otherwise, the mutator cannot guarantee the mutation behavior. When
     * overriding this method, always call super().
     * 
     * @param element The element to mutate
     * @return Return true if the mutator can mutate element, or false
     */
    public boolean canMutate(CtElement element) {
        // The element should not be synthetic
        return !mAx.getCodeSyn().isSyn(element);
    }

    public abstract void mutate(CtElement element);
}
