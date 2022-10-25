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
import io.artemis.AxChecker;
import io.artemis.syn.LoopSkl;
import io.artemis.syn.SklPh;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.factory.Factory;
import spoon.template.BlockTemplate;
import spoon.template.Local;
import spoon.template.Parameter;

public class SwLoopSkl extends BlockTemplate implements LoopSkl {
    ///////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////// SKELETON DEFINITIONS //////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////
    // Loop headers to substitute
    @Parameter
    private CtLiteral<Integer> _START_;
    @Parameter
    private CtLiteral<Integer> _STEP_;
    @Parameter
    private CtLiteral<Integer> _TRIP_;

    // Blocks to synthesize and substitute
    @Parameter
    private CtBlock<?> _PRE_BODY_;
    @Parameter
    private CtBlock<?> _POST_BODY_;

    // Names to synthesize and substitute
    @Parameter
    private String _I_NAME_;
    @Parameter
    private String _EXEC_NAME_;

    @Override
    public void block() throws Throwable {
        boolean _EXEC_NAME_ = false;
        for (int _I_NAME_ = _START_.S(); _I_NAME_ < _START_.S() + _TRIP_.S(); _I_NAME_ +=
                _STEP_.S()) {
            _PRE_BODY_.S();
            if (!_EXEC_NAME_) {
                SklPh.placeholder("<exec_stmt>");
                _EXEC_NAME_ = true;
            }
            _POST_BODY_.S();
        }
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////

    @Local
    @Override
    public int getBlockCount() {
        return 2;
    }

    @Local
    @Override
    public int getNamesCount() {
        return 2;
    }

    @Local
    @Override
    public CtStatement instantiate(Artemis ax, int start, int step, int trip, String[] names,
            CtBlock<?>[] blocks) {
        AxChecker.check(names.length == getNamesCount(), "Insufficient names");
        AxChecker.check(blocks.length == getBlockCount(), "Insufficient blocks");

        Factory fact = ax.getSpoon().getFactory();

        _START_ = fact.createLiteral(start);
        _STEP_ = fact.createLiteral(step);
        _TRIP_ = fact.createLiteral(trip);
        _I_NAME_ = names[0];
        _EXEC_NAME_ = names[1];
        _PRE_BODY_ = blocks[0];
        _POST_BODY_ = blocks[1];

        return apply(ax.getTestClass());
    }

    public static void wrapStmt(CtStatement loop, CtStatement stmt) {
        SklPh.substitute(loop, "<exec_stmt>", stmt);
    }
}
