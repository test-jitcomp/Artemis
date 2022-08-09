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
