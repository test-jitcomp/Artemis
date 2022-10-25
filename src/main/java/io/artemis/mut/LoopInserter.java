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

import java.util.ArrayList;
import java.util.List;

import io.artemis.Artemis;
import io.artemis.AxLog;
import io.artemis.skl.LiLoopSkl;
import io.artemis.syn.PPoint;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtImport;

public class LoopInserter extends StmtMutator {

    public LoopInserter(Artemis ax) {
        super(ax);
    }

    @Override
    protected boolean canMutate(CtStatement stmt) {
        return !(stmt instanceof CtBlock);
    }

    @Override
    protected void mutate(CtStatement stmt) {
        PPoint pp = PPoint.beforeStmt(mAx.getTestClass(), stmt);

        AxLog.v("Synthesizing new loops with LoopInserter's skeleton");
        List<CtImport> imports = new ArrayList<>(5);
        CtStatement loop = mAx.getCodeSyn().synLoop(pp, new LiLoopSkl(), imports);

        AxLog.v("Inserting the following loop before the statement to mutate",
                (out, err) -> out.println(loop));
        stmt.insertBefore(loop);

        // Add required imports to our tests
        mAx.getTestCompUnit().getImports().addAll(imports);
    }
}
