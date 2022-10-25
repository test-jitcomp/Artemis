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

package io.artemis;

import java.io.PrintStream;

public class AxLog {

    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_VERBOSE = 2;
    public static final int LEVEL_DEBUG = 3;

    private static int sLevel = LEVEL_INFO;
    private static PrintStream sStdout = System.out;
    private static PrintStream sStderr = System.err;

    public interface LogBlock {
        void log(PrintStream out, PrintStream err);
    }

    public static void setLevel(int level) {
        sLevel = level;
    }

    public static void setStdout(PrintStream out) {
        sStdout = out;
    }

    public static void setStderr(PrintStream err) {
        sStderr = err;
    }

    public static void e(String msg) {
        sStderr.println("***ERROR*** " + msg);
    }

    public static void w(String msg) {
        sStdout.println("\\\\\\WARN/// " + msg);
    }

    public static void i(String msg) {
        sStdout.println("[INFO] " + msg);
    }

    public static void v(String header, LogBlock b) {
        if (sLevel >= LEVEL_VERBOSE) {
            v(header + ": ");
            v("-----");
            b.log(sStdout, sStderr);
            v("-----");
        }
    }

    public static void v(String msg) {
        if (sLevel >= LEVEL_VERBOSE) {
            sStdout.println("[VERB] " + msg);
        }
    }

    public static void d(String msg) {
        if (sLevel >= LEVEL_DEBUG) {
            sStdout.println("[·DBG] " + msg);
        }
    }

    public static void d(String header, LogBlock b) {
        if (sLevel >= LEVEL_DEBUG) {
            d(header);
            d("-----");
            b.log(sStdout, sStderr);
            d("-----");
        }
    }

    public static void println(String msg) {
        sStdout.println(msg);
    }
}
