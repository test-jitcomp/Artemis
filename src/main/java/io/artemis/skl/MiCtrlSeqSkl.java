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
import io.artemis.syn.SklPh;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtField;
import spoon.template.Local;
import spoon.template.Parameter;
import spoon.template.StatementTemplate;

public class MiCtrlSeqSkl extends StatementTemplate {
    ///////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////// SKELETON DEFINITIONS //////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////
    // Values to substitute
    @Parameter
    private CtFieldRead<Boolean> _CTRL_;
    @Parameter
    private CtBlock<?> _BODY_;

    @Override
    public void statement() throws Throwable {
        if (_CTRL_.S()) {
            _BODY_.S();
            SklPh.placeholder("<early_return>");
        }
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////

    @Local
    public static CtStatement instantiate(Artemis ax, CtField<Boolean> ctrl, CtBlock<?> body,
            CtReturn<?> retStmt) {
        MiCtrlSeqSkl skl = new MiCtrlSeqSkl();

        skl._CTRL_ = ax.getSpoon().getFactory().createFieldRead();
        skl._CTRL_.setVariable(ctrl.getReference());
        skl._BODY_ = body;

        CtStatement stmt = skl.apply(ax.getTestClass());
        SklPh.substitute(stmt, "<early_return>", retStmt);
        return stmt;
    }

    @Local
    private MiCtrlSeqSkl() {}
}
