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

package io.artemis.util;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Options implements Iterable<String> {

    public static class IllegalOptionException extends Exception {
        public IllegalOptionException(String opt, String message) {
            super("Option \"" + opt + "\" is illegal: " + message);
        }
    }

    private final String[] mOpts;
    private final Map<String, String> mParsedOpts = new HashMap<>();

    private int mNextOpt = 0;
    private String nCurOptVal = null;

    public static Options parse(String[] opts) {
        Options options = new Options(opts);
        options.parseOptions();
        return options;
    }

    public String[] getUnparsedOptions() {
        return mOpts;
    }

    public boolean hasOption(String opt) {
        return mParsedOpts.containsKey(opt);
    }

    public String getOption(String opt, boolean allowNull) throws IllegalOptionException {
        String val = mParsedOpts.getOrDefault(opt, null);
        if (!allowNull && val == null) {
            throw new IllegalOptionException(opt, "Option value is null");
        }
        return val;
    }

    public String getOption(String opt) throws IllegalOptionException {
        return getOption(opt, /* allowNull */ false);
    }

    public String getString(String opt, boolean allowEmpty) throws IllegalOptionException {
        String val = mParsedOpts.getOrDefault(opt, "");
        if (val == null) {
            throw new IllegalOptionException(opt, "Option value is null");
        } else if (!allowEmpty && val.equals("")) {
            throw new IllegalOptionException(opt, "Option value is an empty string");
        }
        return val;
    }

    public String getString(String opt) throws IllegalOptionException {
        return getString(opt, /* allowEmpty */ false);
    }

    public int getInteger(String opt) throws IllegalOptionException {
        try {
            return Integer.parseInt(getString(opt));
        } catch (NumberFormatException e) {
            throw new IllegalOptionException(opt, e.getMessage());
        }
    }

    public long getLong(String opt) throws IllegalOptionException {
        try {
            return Long.parseLong(getString(opt));
        } catch (NumberFormatException e) {
            throw new IllegalOptionException(opt, e.getMessage());
        }
    }

    public File getFile(String opt, boolean allowCreating) throws IllegalOptionException {
        String path = getString(opt);
        File val = new File(path);
        if (!allowCreating && !val.exists()) {
            throw new IllegalOptionException(opt, "File does not exist: " + path);
        }
        return val;
    }

    public File getFile(String opt) throws IllegalOptionException {
        return getFile(opt, /* allowCreating */ false);
    }

    @Override
    public Iterator<String> iterator() {
        return mParsedOpts.keySet().iterator();
    }

    private void parseOptions() {
        String opt;
        while (true) {
            opt = nextOption();
            if (opt == null) {
                break;
            }
            mParsedOpts.put(opt, nextOptionVal());
        }
        mNextOpt = -1;
        nCurOptVal = null;
    }

    /**
     * Return the next command line option. This has a number of special cases which closely, but
     * not exactly, follow the POSIX command line options patterns:
     *
     * -- means to stop processing additional options -z means option z -z VAL means option z with
     * (non-optional) value VAL -zVAL means option z with (optional) value VAL --zz means option zz
     * --zz VAL means option zz with (non-optional) value VAL
     *
     * Note that you cannot combine single letter options; -abc != -a -b -c
     *
     * @return Returns the option string, or null if there are no more options.
     */
    private String nextOption() {
        if (mNextOpt >= mOpts.length) {
            return null;
        }

        String opt = mOpts[mNextOpt];
        if (!opt.startsWith("-")) { // not an option, stop processing
            return null;
        }
        mNextOpt++;
        if (opt.equals("--")) { // --, stop processing
            return null;
        }
        if (opt.length() > 1 && opt.charAt(1) != '-') { // -z, -z VAL, or -zVAL
            if (opt.length() > 2) { // -zVAL
                nCurOptVal = opt.substring(2); // VAL
                return opt.substring(0, 2); // -z
            } else { // -z, or -z VAL
                nCurOptVal = null;
                return opt; // -z
            }
        } else if (opt.length() > 1) { // --zz, or --zz VAL
            nCurOptVal = null;
            return opt; // --zz
        } else { // -, skip it
            nCurOptVal = null;
            return nextOption();
        }
    }

    /**
     * Return the next value associated with the current option.
     *
     * @return Returns the value string, or null of there are no more options.
     */
    private String nextOptionVal() {
        if (nCurOptVal != null) {
            return nCurOptVal;
        }
        if (mNextOpt >= mOpts.length) {
            return null;
        }
        String val = mOpts[mNextOpt];
        if (val.startsWith("-")) { // next option
            return null;
        } else if (val.startsWith("'")) { // next value wrapped by ''
            val = nextOptionValWithQuotes("'");
        } else if (val.startsWith("\"")) { // next value wrapped by ""
            val = nextOptionValWithQuotes("\"");
        }
        mNextOpt++;
        return val;
    }

    private String nextOptionValWithQuotes(String q) {
        int start = mNextOpt;
        while (!mOpts[mNextOpt].endsWith(q)) {
            mNextOpt++;
        }
        String val = String.join(" ", Arrays.copyOfRange(mOpts, start, mNextOpt + 1));
        return val.substring(1, val.length() - 1);
    }

    private Options(String[] opts) {
        mOpts = opts;
    }

    public static void main(String[] args) throws IllegalOptionException {
        args = "-q1 -w_3 --neg \"-12\" -t 2_3 -e - --a --s 23 --sd 24 -- -p 23 -w 3".split(" ");
        System.out.println(String.join(" ", args));
        Options parser = new Options(args);
        for (String opt : parser) {
            System.out.println(opt + " => " + parser.getOption(opt));
        }

        System.out.println();

        args = "-p com.example.simon.myapplication -s 0 -P dfs -C 10".split(" ");
        System.out.println(String.join(" ", args));
        parser = new Options(args);
        for (String opt : parser) {
            System.out.println(opt + " => " + parser.getOption(opt));
        }

        System.out.println();

        args = "-x \"208.95996\" -y \"1133.9062\"".split(" ");
        System.out.println(String.join(" ", args));
        parser = new Options(args);
        for (String opt : parser) {
            System.out.println(opt + " => " + parser.getOption(opt));
        }

        System.out.println();

        args = "--desc \"Advanced Oppos Operations\" -y \"1133.9062\"".split(" ");
        System.out.println(String.join(" ", args));
        parser = new Options(args);
        for (String opt : parser) {
            System.out.println(opt + " => " + parser.getOption(opt));
        }
    }
}
