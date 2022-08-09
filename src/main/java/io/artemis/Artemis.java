package io.artemis;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import io.artemis.pol.MutationPolicy;
import io.artemis.pol.PolicyFactory;
import io.artemis.syn.CodeSyn;
import io.artemis.util.CannotReachHereException;
import io.artemis.util.Options;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtType;
import spoon.support.compiler.VirtualFile;
import spoon.support.compiler.VirtualFolder;

public class Artemis {
    // @formatter:off
    public static final String ARTEMIS_USAGE = 
            "Usage: artemis [options] -b BRICKS -i INPUT -o OUTPUT_DIR\n"
            + "\n" 
            + "Arguments:\n" 
            + "  -b BRICKS      path to the directory saving code bricks\n"
            + "  -i INPUT       path to the input .java file\n" 
            + "  -o OUTPUT      path to an output directory to save the .java mutant\n"
            + "\n" 
            + "Options:\n"
            + "  -m MIN_TRIPS   minimum loop trips (default: 50,000)\n"
            + "  -M MAX_TRIPS   minimum loop trips (default: 100,000,000)\n"
            + "  -s SEED        random seed (default: current time in ms)\n"
            + "  -p POLICY      mutation policy, one of: artemis (default: artemis)\n"
            + "  -Xkey:value    extra options, available options are:\n"
            + "  -v             verbose mode (default: false)\n"
            + "  -V             more verbose (vverbose) mode, implies -v (default: false)\n"
            + "  -h             show this message\n"
            + "\n" 
            + "Notice:\n"
            + "  (1) Currently, Artemis only supports to mutation Java 8 programs, but the\n"
            + "  mutants can be used to test any Java virtual machines supporting at least\n"
            + "  Java 8. Please don't feed any Java 9+ inputs to Artemis, otherwise, \n"
            + "  Artemis cannot assure its behavior to be expected.\n"
            + "  (2) Currently, Artemis only supports to mutate a the given input .java file.\n"
            + "  It won't mutate any likely dependencies (i.e., files under classpath). ";
    // @formatter:on

    // Supported Java version: currently we only support Java 8.
    public static final int JAVA_VERSION = 8;

    private static final int MIN_LOOP_TRIPS = 500_000;
    private static final int MAX_LOOP_TRIPS = 100_000_000;
    private static final Map<String, PolicyFactory.PolicyName> POLICY_PLANS = new HashMap<>();
    static {
        POLICY_PLANS.put(PolicyFactory.PolicyName.ARTEMIS.name, PolicyFactory.PolicyName.ARTEMIS);
    }
    private static final VirtualFolder SKELETON_FOLDER = new VirtualFolder();
    static {
        final String SKL_FOLDER_NAME = "skeletons";
        final String[] SKL_NAMES = new String[] {"LiLoopSkl.java", "SwLoopSkl.java",
                "MiLoopSkl.java", "MiCtrlSeqSkl.java", "ExHandleSkl.java", "RedirectSkl.java"};
        ClassLoader loader = Artemis.class.getClassLoader();
        try {
            for (String sklName : SKL_NAMES) {
                InputStream skeleton =
                        loader.getResourceAsStream(SKL_FOLDER_NAME + File.separator + sklName);
                if (skeleton == null) {
                    throw new IOException("Skeleton not found: " + sklName);
                }
                SKELETON_FOLDER
                        .addFile(new VirtualFile(new String(skeleton.readAllBytes()), sklName));
            }
        } catch (IOException e) {
            AxLog.e(e.getMessage());
            System.exit(1);
        }
    }

    // Arguments
    private File mInput;
    private File mCbFolder;
    private File mOutput;

    // Options: with default values
    private int mMinLoopTrips = MIN_LOOP_TRIPS;
    private int mMaxLoopTrips = MAX_LOOP_TRIPS;
    private PolicyFactory.PolicyName mPolicyName = PolicyFactory.PolicyName.ARTEMIS;
    private final ExtraOpts mExtraOpts = new ExtraOpts();

    // Program related stuff
    private MutationPolicy mPolicy;
    private CodeSyn mCodeSyn;

    private Launcher mSpoon;
    private CtCompilationUnit mTestCompU;
    private CtClass<?> mTestClass;

    public Artemis(Options options) {
        if (!processOptions(options)) {
            System.exit(1);
        }
    }

    public int getMinLoopTrips() {
        return mMinLoopTrips;
    }

    public int getMaxLoopTrips() {
        return mMaxLoopTrips;
    }

    public CodeSyn getCodeSyn() {
        return mCodeSyn;
    }

    public Launcher getSpoon() {
        return mSpoon;
    }

    public CtCompilationUnit getTestCompUnit() {
        return mTestCompU;
    }

    public CtClass<?> getTestClass() {
        return mTestClass;
    }

    public void run() {
        AxLog.v("Building Spoon model");

        mSpoon = new Launcher();
        mSpoon.getEnvironment().setComplianceLevel(JAVA_VERSION);
        // Use simple name instead of full-qualified name
        mSpoon.getEnvironment().setAutoImports(true);
        mSpoon.getEnvironment().setCommentEnabled(false);
        // Let's temporarily disable all other classpath (e.g., rt.jar) other than the parent folder
        mSpoon.getEnvironment().setSourceClasspath(new String[] {mInput.getParent()});
        mSpoon.addInputResource(mInput.getAbsolutePath());
        mSpoon.addTemplateResource(SKELETON_FOLDER);
        mSpoon.setSourceOutputDirectory(mOutput);
        mSpoon.buildModel();

        // Artemis assumes that:
        // (1) the input java file to be named with the same name as its test class
        // (2) no package name is given
        mTestCompU = mSpoon.getFactory().CompilationUnit().getOrCreate(mInput.getAbsolutePath());
        // We cast the mainType to CtClass assuming that the test class
        CtType<?> testClass = mTestCompU.getMainType();
        if (testClass == null) {
            AxLog.e("No test class found in the given input file: " + mInput);
            System.exit(1);
        } else if (!(testClass instanceof CtClass)) {
            AxLog.e("The input file's main (public) type is not a class: " + mInput);
            System.exit(1);
        }
        mTestClass = (CtClass<?>) testClass;

        // Apply the mutation policy to get a mutant
        AxLog.v("Applying policy (" + mPolicyName + ") to mutate input");
        mPolicy.apply(mTestClass);

        AxLog.v("Writing mutant to " + mOutput + File.separator + mInput.getName());
        mSpoon.prettyprint();
    }

    private boolean processOptions(Options options) {
        try {
            processOptions(options, 0);
        } catch (Options.IllegalOptionException e) {
            AxLog.e(e.getMessage());
            return false;
        }

        if (mCbFolder == null) {
            AxLog.e("Code bricks are not given, use --code-brick or -b to give Artemis");
            return false;
        }

        if (mInput == null) {
            AxLog.e("Input is not given, use --input or -i to give Artemis");
            return false;
        }

        if (mOutput == null) {
            AxLog.e("Output directory is not given, use --output or -o to give Artemis");
            return false;
        }

        mPolicy = PolicyFactory.create(mPolicyName, this, mExtraOpts);
        try {
            mCodeSyn = new CodeSyn(this, mCbFolder);
        } catch (IOException e) {
            AxLog.e(e.getMessage());
            return false;
        }

        return true;
    }

    @SuppressWarnings("SameParameterValue")
    private void processOptions(Options options, int ignoreUnused)
            throws Options.IllegalOptionException {
        if (options.hasOption("-V") || options.hasOption("--vverbose")) {
            AxLog.setLevel(AxLog.LEVEL_DEBUG);
        } else if (options.hasOption("-v") || options.hasOption("--verbose")) {
            AxLog.setLevel(AxLog.LEVEL_VERBOSE);
        } else {
            AxLog.setLevel(AxLog.LEVEL_INFO);
        }

        AxLog.v("From options: " + String.join(" ", options.getUnparsedOptions()));

        for (String opt : options) {
            switch (opt) {
                case "--verbose":
                case "-v":
                case "--vverbose":
                case "-V":
                    break;
                case "--input":
                case "-i":
                    mInput = options.getFile(opt);
                    break;
                case "--code-brick":
                case "-b":
                    mCbFolder = options.getFile(opt);
                    if (!mCbFolder.isDirectory()) {
                        throw new Options.IllegalOptionException(opt,
                                "Path to code brick is not a directory");
                    }
                    break;
                case "--output":
                case "-o":
                    mOutput = options.getFile(opt);
                    break;
                case "--seed":
                case "-s":
                    AxRandom.getInstance().setSeed(options.getLong(opt));
                    break;
                case "--min-trips":
                case "-m":
                    mMinLoopTrips = options.getInteger(opt);
                    break;
                case "--max-trips":
                case "-M":
                    mMaxLoopTrips = options.getInteger(opt);
                    break;
                case "--policy":
                case "-p":
                    String policy = options.getString(opt);
                    mPolicyName = POLICY_PLANS.getOrDefault(policy, null);
                    if (mPolicyName == null) {
                        throw new Options.IllegalOptionException(opt,
                                "No such mutation policy: " + policy);
                    }
                    break;
                case "-X":
                    String opts = options.getString(opt);
                    for (String keyValue : opts.split(",")) {
                        if (keyValue.length() == 0) {
                            continue;
                        }
                        String[] tokens = keyValue.split(":");
                        if (tokens.length != 2) {
                            throw new Options.IllegalOptionException(opt,
                                    "Invalid mutator setting (should use \":\"): " + keyValue);
                        }
                        mExtraOpts.put(tokens[0], tokens[1]);
                    }
                    break;
                case "--help":
                case "-h":
                    showUsage();
                    System.exit(0);
                default:
                    AxLog.e("Unrecognized option: " + opt);
                    System.exit(1);
            }
        }
    }

    public static void showUsage() {
        AxLog.println(ARTEMIS_USAGE);
        AxLog.println("");
    }

    public static void main(String[] args) {
        new Artemis(Options.parse(args)).run();
    }

    public static class ExtraOpts extends HashMap<String, String> {
        public ExtraOpts() {
            super();
        }

        @SuppressWarnings("ConstantConditions")
        public int getInteger(String key) {
            try {
                checkContainsKey(key);
                return Integer.parseInt(get(key));
            } catch (NumberFormatException e) {
                AxChecker.check(false, "Invalid integer for option " + key + ": " + e.getMessage());
                throw new CannotReachHereException();
            }
        }

        @SuppressWarnings("ConstantConditions")
        public float getFloat(String key) {
            try {
                checkContainsKey(key);
                return Float.parseFloat(get(key));
            } catch (NumberFormatException e) {
                AxChecker.check(false, "Invalid float for option " + key + ": " + e.getMessage());
                throw new CannotReachHereException();
            }
        }

        @SuppressWarnings("ConstantConditions")
        public boolean getBoolean(String key) {
            try {
                checkContainsKey(key);
                return Boolean.parseBoolean(get(key));
            } catch (Exception e) {
                AxChecker.check(false, "Invalid boolean for option " + key + ": " + e.getMessage());
                throw new CannotReachHereException();
            }
        }

        public float getProb(String key) {
            float prob = getFloat(key);
            AxChecker.check(0 <= prob && prob <= 1,
                    "Invalid probability for option " + key + ", must between (0, 1): " + prob);
            return prob;
        }

        public File getFile(String key, boolean shouldExist) {
            checkContainsKey(key);
            File file = new File(get(key));
            AxChecker.check(!shouldExist || file.exists(),
                    "Invalid file for option " + key + ": not exist");
            return file;
        }

        public File getFile(String key) {
            return getFile(key, /* shouldExist */ true);
        }

        private void checkContainsKey(String key) {
            AxChecker.check(containsKey(key), "No such option: " + key);
        }
    }
}
