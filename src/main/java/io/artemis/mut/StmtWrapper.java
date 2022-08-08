package io.artemis.mut;

import java.util.List;

import io.artemis.Artemis;
import io.artemis.AxLog;
import io.artemis.skl.SwLoopSkl;
import io.artemis.syn.PPoint;
import spoon.reflect.code.CtBreak;
import spoon.reflect.code.CtContinue;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtYieldStatement;
import spoon.reflect.visitor.filter.TypeFilter;

public class StmtWrapper extends StmtMutator {

    public StmtWrapper(Artemis ax) {
        super(ax);
    }

    @Override
    protected boolean canMutate(CtStatement stmt) {
        // Never wrap any declarations and flow-altering statements
        return !(stmt instanceof CtLocalVariable) && !(stmt instanceof CtBreak)
                && !(stmt instanceof CtContinue) && !(stmt instanceof CtReturn)
                && !(stmt instanceof CtYieldStatement);
    }

    @Override
    protected void mutate(CtStatement stmt) {
        PPoint pp = PPoint.beforeStmt(mAx.getTestClass(), stmt);

        AxLog.v("Synthesizing new loops with StmtWrapper's skeleton");
        CtStatement loop = mAx.getLoopSyn().synLoop(pp, new SwLoopSkl());

        AxLog.v("Wrapping the statement by the following loop:", (out, err) -> out.println(loop));
        // Replace the statement to wrap stmt by our synthetic loop
        stmt.replace(loop);
        // Substitute the placeholder by the our statement to wrap stmt
        loop.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation invoc) {
                if (!super.matches(invoc)
                        || !invoc.getExecutable().getSimpleName().equals("placeholder")) {
                    return false;
                }
                List<CtExpression<?>> args = invoc.getArguments();
                if (args.size() != 1) {
                    return false;
                }
                CtExpression<?> a = args.get(0);
                return a instanceof CtLiteral
                        && a.prettyprint().equals("\"<placeholder:stmt_to_wrap>\"");
            }
        }).get(0).replace(stmt);
    }
}