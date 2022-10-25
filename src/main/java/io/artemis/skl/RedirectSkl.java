/**
 * MIT License
 * 
 * Copyright (c) 2021 Cong Li (congli@smail.nju.edu.cn, cong.li@inf.ethz.ch)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
