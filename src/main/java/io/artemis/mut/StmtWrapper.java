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
