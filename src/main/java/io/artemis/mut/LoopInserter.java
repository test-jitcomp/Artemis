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
