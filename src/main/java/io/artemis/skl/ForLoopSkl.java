package io.artemis.skl;

import io.artemis.Artemis;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.factory.Factory;
import spoon.template.Local;
import spoon.template.Parameter;
import spoon.template.StatementTemplate;

public class ForLoopSkl extends StatementTemplate {
    // Values to substitute
    @Parameter
    private CtLiteral<Integer> _START_;
    @Parameter
    private CtLiteral<Integer> _STEP_;
    @Parameter
    private CtLiteral<Integer> _TRIP_;
    @Parameter
    private CtBlock<?> _BODY_;

    // Names to substitute
    @Parameter
    private String _I_NAME_;

    @Local
    public static CtStatement instantiate(Artemis ax, String iVarName, int start, int step,
            int trip, CtBlock<?> body) {
        ForLoopSkl skl = new ForLoopSkl();
        Factory factory = ax.getSpoon().getFactory();

        skl._I_NAME_ = iVarName;
        skl._START_ = factory.createLiteral(start);
        skl._STEP_ = factory.createLiteral(step);
        skl._TRIP_ = factory.createLiteral(trip);
        skl._BODY_ = body;

        return skl.apply(ax.getTestClass());
    }

    @Override
    public void statement() throws Throwable {
        for (int _I_NAME_ = _START_.S(); _I_NAME_ < _START_.S() + _TRIP_.S(); _I_NAME_ +=
                _STEP_.S()) {
            _BODY_.S();
        }
    }

    @Local
    private ForLoopSkl() {}
}
