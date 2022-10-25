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

package io.artemis.skl;

import io.artemis.Artemis;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.template.BlockTemplate;
import spoon.template.Local;
import spoon.template.Parameter;

public class ExHandleSkl extends BlockTemplate {
    ///////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////// SKELETON DEFINITIONS //////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////
    // Values to substitute
    @Parameter
    private CtBlock<?> _TRY_BLOCK_;
    @Parameter
    private CtBlock<?> _FINALLY_BLOCK_;

    // Names to substitute
    @Parameter
    private String _EX_NAME_;

    @Override
    public void block() throws Throwable {
        try {
            _TRY_BLOCK_.S();
        } catch (Throwable _EX_NAME_) {
            /* DO NOTHING */
        } finally {
            _FINALLY_BLOCK_.S();
        }
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////

    @Local
    public static CtBlock<?> instantiate(Artemis ax, String exName, CtStatement tryStmt) {
        return instantiate(ax, exName, ax.getSpoon().getFactory().createCtBlock(tryStmt), null);
    }

    @Local
    public static CtBlock<?> instantiate(Artemis ax, String exName, CtBlock<?> tryBlock) {
        return instantiate(ax, exName, tryBlock, null);
    }

    @Local
    public static CtBlock<?> instantiate(Artemis ax, String exName, CtStatement tryStmt,
            CtStatement finallyStmt) {
        return instantiate(ax, exName, ax.getSpoon().getFactory().createCtBlock(tryStmt),
                ax.getSpoon().getFactory().createCtBlock(finallyStmt));
    }

    @Local
    public static CtBlock<?> instantiate(Artemis ax, String exName, CtBlock<?> tryBlock,
            CtBlock<?> finallyBlock) {
        ExHandleSkl skl = new ExHandleSkl();

        skl._EX_NAME_ = exName;
        skl._TRY_BLOCK_ = tryBlock;
        skl._FINALLY_BLOCK_ = finallyBlock;

        return skl.apply(ax.getTestClass());
    }

    @Local
    private ExHandleSkl() {}
}
