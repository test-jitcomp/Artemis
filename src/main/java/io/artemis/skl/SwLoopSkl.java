package io.artemis.skl;

import io.artemis.Artemis;
import io.artemis.AxChecker;
import io.artemis.syn.LoopSkl;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.factory.Factory;
import spoon.template.BlockTemplate;
import spoon.template.Local;
import spoon.template.Parameter;

public class SwLoopSkl extends BlockTemplate implements LoopSkl {
    @Parameter
    private CtLiteral<Integer> _START_;
    @Parameter
    private CtLiteral<Integer> _STEP_;
    @Parameter
    private CtLiteral<Integer> _TRIP_;

    // Blocks to substitute
    @Parameter
    private CtBlock<?> _PRE_BODY_;
    @Parameter
    private CtBlock<?> _POST_BODY_;

    // Names to substitute
    @Parameter
    private String _I_NAME_;
    @Parameter
    private String _EXEC_NAME_;

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
        AxChecker.check(blocks.length == getNamesCount(), "Insufficient blocks");

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

    @Override
    public void block() throws Throwable {
        boolean _EXEC_NAME_ = false;
        for (int _I_NAME_ = _START_.S(); _I_NAME_ < _START_.S() + _TRIP_.S(); _I_NAME_ +=
                _STEP_.S()) {
            _PRE_BODY_.S();
            if (!_EXEC_NAME_) {
                placeholder("<placeholder:stmt_to_wrap>");
                _EXEC_NAME_ = true;
            }
            _POST_BODY_.S();
        }
    }
}
