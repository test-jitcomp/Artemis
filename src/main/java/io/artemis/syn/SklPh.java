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

package io.artemis.syn;

import java.util.List;

import io.artemis.AxChecker;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.visitor.filter.TypeFilter;

public final class SklPh {

    public static void placeholder(String id) {}

    public static void substitute(CtStatement from, String id, CtStatement to) {
        try {
            from.getElements(new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation invoc) {
                    if (!super.matches(invoc) || invoc.getTarget() == null
                            || !invoc.getTarget().prettyprint().equals("SklPh")
                            || !invoc.getExecutable().getSimpleName().equals("placeholder")) {
                        return false;
                    }
                    List<CtExpression<?>> args = (List<CtExpression<?>>) invoc.getArguments();
                    if (args.size() != 1) {
                        return false;
                    }
                    CtExpression<?> a = args.get(0);
                    return a instanceof CtLiteral && a.prettyprint().equals("\"" + id + "\"");
                }
            }).get(0).replace(to);
        } catch (IndexOutOfBoundsException ignoreUnused) {
            // noinspection ConstantConditions
            AxChecker.check(false, "No placeholder namely " + id + " found");
        }
    }
}
