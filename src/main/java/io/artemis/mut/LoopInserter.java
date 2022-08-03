package io.artemis.mut;

import java.util.HashSet;
import java.util.Set;

import io.artemis.Artemis;
import io.artemis.AxChecker;
import io.artemis.AxLog;
import io.artemis.syn.PPoint;
import io.artemis.util.Spoons;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtCompilationUnit;

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

        AxLog.v("Synthesizing new loops");
        CtStatement loop = mAx.getLoopSyn().synLoop(pp);

        AxLog.v("Inserting the following loop before the statement to mutate:",
                (out, err) -> out.println(loop));
        stmt.insertBefore(loop);
    }
}
