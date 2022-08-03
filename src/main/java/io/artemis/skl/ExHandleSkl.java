package io.artemis.skl;

import io.artemis.Artemis;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.template.Local;
import spoon.template.Parameter;
import spoon.template.StatementTemplate;

public class ExHandleSkl extends StatementTemplate {
    // Values to substitute
    @Parameter
    private CtBlock<?> _TRY_BLOCK_;
    @Parameter
    private CtBlock<?> _FINALLY_BLOCK_;

    // Names to substitute
    @Parameter
    private String _EX_NAME_;

    @Local
    public static CtStatement instantiate(Artemis ax, String exName, CtStatement tryStmt) {
        return instantiate(ax, exName, ax.getSpoon().getFactory().createCtBlock(tryStmt), null);
    }

    @Local
    public static CtStatement instantiate(Artemis ax, String exName, CtBlock<?> tryBlock) {
        return instantiate(ax, exName, tryBlock, null);
    }

    @Local
    public static CtStatement instantiate(Artemis ax, String exName, CtStatement tryStmt,
            CtStatement finallyStmt) {
        return instantiate(ax, exName, ax.getSpoon().getFactory().createCtBlock(tryStmt),
                ax.getSpoon().getFactory().createCtBlock(finallyStmt));
    }

    @Local
    public static CtStatement instantiate(Artemis ax, String exName, CtBlock<?> tryBlock,
            CtBlock<?> finallyBlock) {
        ExHandleSkl skl = new ExHandleSkl();

        skl._EX_NAME_ = exName;
        skl._TRY_BLOCK_ = tryBlock;
        skl._FINALLY_BLOCK_ = finallyBlock;

        return skl.apply(ax.getTestClass());
    }

    @Override
    public void statement() throws Throwable {
        try {
            _TRY_BLOCK_.S();
        } catch (Throwable _EX_NAME_) {
            /* DO NOTHING */
        } finally {
            _FINALLY_BLOCK_.S();
        }
    }

    @Local
    private ExHandleSkl() {}
}
