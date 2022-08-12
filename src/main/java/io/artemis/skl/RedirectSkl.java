package io.artemis.skl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import io.artemis.Artemis;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;
import spoon.template.ExtensionTemplate;
import spoon.template.Local;
import spoon.template.Substitution;

public class RedirectSkl extends ExtensionTemplate {
    ///////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////// SKELETON DEFINITIONS //////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////
    private static final PrintStream devNull = new PrintStream(new OutputStream() {
        @Override
        public void write(int i) throws IOException {
            /* DO NOTHING */
        }
    });
    private static final PrintStream stdOutBk = System.out;
    private static final PrintStream stdErrBk = System.err;

    public static void redirect() {
        System.setOut(devNull);
        System.setErr(devNull);
    }

    public static void recover() {
        System.setOut(stdOutBk);
        System.setErr(stdErrBk);
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////

    @Local
    public static CtClass<?> instantiate(Artemis ax, String className) {
        CtClass<?> rhClass = ax.getSpoon().getFactory().createClass(className);
        Substitution.insertAll(rhClass, new RedirectSkl());
        return rhClass;
    }

    @Local
    public static CtStatement callRedirect(Artemis ax, CtClass<?> rhClass) {
        Factory fact = ax.getSpoon().getFactory();
        return fact.createInvocation(fact.createTypeAccess(rhClass.getReference()),
                rhClass.getMethod("redirect").getReference());
    }

    @Local
    public static CtStatement callRecover(Artemis ax, CtClass<?> rhClass) {
        Factory fact = ax.getSpoon().getFactory();
        return fact.createInvocation(fact.createTypeAccess(rhClass.getReference()),
                rhClass.getMethod("recover").getReference());
    }

    @Local
    private RedirectSkl() {}
}
