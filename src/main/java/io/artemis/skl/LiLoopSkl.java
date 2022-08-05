package io.artemis.skl;

import io.artemis.Artemis;
import io.artemis.AxChecker;
import io.artemis.syn.LoopSkl;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.factory.Factory;
import spoon.template.Local;
import spoon.template.Parameter;
import spoon.template.StatementTemplate;

public class LiLoopSkl extends StatementTemplate implements LoopSkl {
    @Parameter
    private CtLiteral<Integer> _START_;
    @Parameter
    private CtLiteral<Integer> _STEP_;
    @Parameter
    private CtLiteral<Integer> _TRIP_;

    // Blocks to substitute
    @Parameter
    private CtBlock<?> _BODY_;

    // Names to substitute
    @Parameter
    private String _I_NAME_;

    @Local
    @Override
    public int getBlockCount() {
        return 1;
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
        AxChecker.check(blocks.length == getNamesCount(), "Insufficient blocks");

        Factory fact = ax.getSpoon().getFactory();

        _START_ = fact.createLiteral(start);
        _STEP_ = fact.createLiteral(step);
        _TRIP_ = fact.createLiteral(trip);
        _I_NAME_ = names[0];
        _BODY_ = blocks[0];

        return apply(ax.getTestClass());
    }

    @Override
    public void statement() throws Throwable {
        for (int _I_NAME_ = _START_.S(); _I_NAME_ < _START_.S() + _TRIP_.S(); _I_NAME_ +=
                _STEP_.S()) {
            _BODY_.S();
        }
    }
}
