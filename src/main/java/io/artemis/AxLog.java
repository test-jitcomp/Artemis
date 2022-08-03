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
            v(header);
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
