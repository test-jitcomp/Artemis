package io.artemis.skl;

import java.util.List;

import io.artemis.Artemis;
import io.artemis.AxChecker;
import io.artemis.syn.LoopSkl;
import io.artemis.syn.SklPh;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.factory.Factory;
import spoon.template.BlockTemplate;
import spoon.template.Local;
import spoon.template.Parameter;

public class MiLoopSkl extends BlockTemplate implements LoopSkl {
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

    @Override
    public void block() throws Throwable {
        for (int _I_NAME_ = _START_.S(); _I_NAME_ < _START_.S() + _TRIP_.S(); _I_NAME_ +=
                _STEP_.S()) {
            _PRE_BODY_.S();
            SklPh.placeholder("<enable_ctrl>");
            SklPh.placeholder("<invoc_meth>");
            SklPh.placeholder("<disable_ctrl>");
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
        return 1;
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
        _PRE_BODY_ = blocks[0];
        _POST_BODY_ = blocks[1];

        return apply(ax.getTestClass());
    }

    @Local
    public static void enableCtrl(CtStatement loop, CtField<Boolean> ctrl, Factory fact) {
        SklPh.substitute(loop, "<enable_ctrl>", fact.createVariableAssignment(ctrl.getReference(),
                ctrl.isStatic(), fact.createLiteral(true)));
    }

    @Local
    public static void disableCtrl(CtStatement loop, CtField<Boolean> ctrl, Factory fact) {
        SklPh.substitute(loop, "<disable_ctrl>", fact.createVariableAssignment(ctrl.getReference(),
                ctrl.isStatic(), fact.createLiteral(false)));
    }

    @Local
    public static void invocMeth(CtStatement loop, CtInvocation<?> invoc,
            List<CtExpression<?>> args, Factory fact) {
        SklPh.substitute(loop, "<invoc_meth>", fact.createInvocation(invoc.getTarget().clone(),
                invoc.getExecutable().clone(), args));
    }
}