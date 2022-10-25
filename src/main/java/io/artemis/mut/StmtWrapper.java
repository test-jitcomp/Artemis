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
import io.artemis.skl.SwLoopSkl;
import io.artemis.syn.PPoint;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtBodyHolder;
import spoon.reflect.code.CtBreak;
import spoon.reflect.code.CtContinue;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.code.CtSynchronized;
import spoon.reflect.code.CtYieldStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.visitor.filter.TypeFilter;

public class StmtWrapper extends StmtMutator {

    public StmtWrapper(Artemis ax) {
        super(ax);
    }

    @Override
    protected boolean canMutate(CtStatement stmt) {
        // We only prefer to wrap single statement. So we don't like block-alike e.g. if/loops/...
        // since they often contain unpredictable things like control-flow altering (continue/break)
        // which, once wrapped, may break the semantics
        if (stmt instanceof CtStatementList || stmt instanceof CtBodyHolder || stmt instanceof CtIf
                || stmt instanceof CtSwitch || stmt instanceof CtNewClass
                || stmt instanceof CtSynchronized) {
            return false;
        }

        // Never wrap any variable and type declarations
        if ((stmt instanceof CtVariable) || (stmt instanceof CtClass)
                || (stmt instanceof CtInterface)) {
            return false;
        }

        // Never wrap any flow-altering statements
        if ((stmt instanceof CtBreak) || (stmt instanceof CtContinue) || (stmt instanceof CtReturn)
                || (stmt instanceof CtYieldStatement)) {
            return false;
        }

        // Never wrap any method and constructor invocations
        return stmt.getElements(new TypeFilter<>(CtAbstractInvocation.class)).size() == 0;
    }

    @Override
    protected void mutate(CtStatement stmt) {
        PPoint pp = PPoint.beforeStmt(mAx.getTestClass(), stmt);

        AxLog.v("Synthesizing new loops with StmtWrapper's skeleton");
        List<CtImport> imports = new ArrayList<>(5);
        CtStatement loop = mAx.getCodeSyn().synLoop(pp, new SwLoopSkl(), imports);

        AxLog.v("Wrapping the statement by the following loop", (out, err) -> out.println(loop));
        // Replace the statement to wrap stmt by our synthetic loop
        stmt.replace(loop);
        // Substitute the placeholder by our statement to wrap stmt
        SwLoopSkl.wrapStmt(loop, stmt);

        // Add required imports to our tests
        mAx.getTestCompUnit().getImports().addAll(imports);
    }
}
