package io.artemis.skl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import io.artemis.Artemis;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.template.BlockTemplate;
import spoon.template.Local;
import spoon.template.Parameter;

public class RedirectSkl extends BlockTemplate {

    // Values to substitute
    @Parameter
    private CtBlock<?> _BLOCK_;

    // Names to substitute
    @Parameter
    private String _OUT_BK_NAME_;
    @Parameter
    private String _ERR_BK_NAME_;
    @Parameter
    private String _NEW_NAME_;

    @Local
    public static CtStatement instantiate(Artemis ax, String outBkName, String errBkName,
            String newName, CtBlock<?> block) {
        RedirectSkl skl = new RedirectSkl();

        skl._OUT_BK_NAME_ = outBkName;
        skl._ERR_BK_NAME_ = errBkName;
        skl._NEW_NAME_ = newName;
        skl._BLOCK_ = block;

        return skl.apply(ax.getTestClass());
    }

    @Override
    public void block() throws Throwable {
        final PrintStream _OUT_BK_NAME_ = System.out;
        final PrintStream _ERR_BK_NAME_ = System.err;
        PrintStream _NEW_NAME_ = new PrintStream(new OutputStream() {
            @Override
            public void write(int i) throws IOException {
                // DISCARD EVERYTHING
            }
        });
        System.setOut(_NEW_NAME_);
        System.setErr(_NEW_NAME_);
        _BLOCK_.S();
        System.setOut(_OUT_BK_NAME_);
        System.setErr(_ERR_BK_NAME_);
    }

    @Local
    private RedirectSkl() {}
}
