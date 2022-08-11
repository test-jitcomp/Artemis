# Java* Fuzzer test generator

Java* Fuzzer test generator is a random Java tests generator. It is derived from Java* Fuzzer for Android* (https://github.com/android-art-intel/Fuzzer), adapted for desktop/server Java environment on Linux and extended to cover more Java syntax features (class inheritance, complex loop patterns, improved exception throwing patterns, etc). The tool compares the result of execution in JIT mode with interpreter mode or reference Java VM that allows to detect crashes, hangs and incorrect calculations. The main idea of the tool is to generate hundreds of thousands small random tests and to cover various cases using pre-defined test generator heuristics and provide a strong testing for Java VM compiler and runtime.

## Table of contents
1. [Setup and maintenance](#setup-and-maintenance)
2. [Tool files descrption](#the-tool-files-description)
    1. [Scripts](#scripts)
    2. [Java sources](#java-source-files)
    3. [Ruby sources](#ruby-source-files)
3. [How to run the tool](#how-to-run-the-tool)
4. [MM extension](#mm-extension)
5. [Basic configuration file settings](#basic-configuration-file-settings)
6. [Known issues](#known-issues)
7. [Authors](#authors)

## Setup and maintenance

Prepare environment:
- Software:
  - Linux, bash version 4.1.2 or later
  - Reference JDK version 6 or higher
  - Ruby version 1.8.7 or higher
- Set environment variables in common.sh script or through environment variables export:
  - REFERENCE_JAVA_HOME should point to the reference JDK location (default: the installed JDK location)
  - JAVA_UNDER_TEST should point to the java binary to be tested (\<JDK under test location\>/bin/java, default: the installed java binary)
  - Make sure ruby binary can be found in PATH
- If necessary, set additional environment variables in common.sh script or through environment variables export:
  - (optional, default value 300 seconds if not set) TIME_OUT specifies time in seconds for a test to be killed by timeout
  - (optional, empty if not set) JAVA_UNDER_TEST_OPTS=\<list of options to be passed to java to be tested\>
  - (optional, empty if not set) JAVA_REFERENCE_OPTS=\<list of options to be passed to reference java\>
  - (optional, empty if not set) JAVAC_OPTS=\<list of options to be passed to javac compiler\>
  - (optional, empty if not set) IGNORE_DEBUG_OUTPUT_PATTERNS=\<patterns to be ignored when comparing reference java output with java under test output\>. E.g., '.*CompilerOracle.*' or other warnings/verbose VM ouput patterns
  - (optional, empty if not set) MM="true" if you specify a multi-threaded configuration mode (see details in "MM extension" section)
- Speedup opportunity
  - If you have a RAM disk, you can mount it to */export/ram* and the Fuzzer will 
  automatically use it for temp files, which gives some speedup.

## Tool files description

### Scripts

- common.sh               - common part for the scripts related to running Fuzzer and 
generated tests
- mrt.sh                  - launches Fuzzer test cycle in host mode on multiple hosts 
in multiple processes
- rt.sh                   - runs Fuzzer tool and generated tests in a loop; re-runs 
generated tests in a given dir

### Java source files

- rb/FuzzerUtils.java
  - superclass for all generated tests; includes methods for initializing
arrays and calculating check sums

### Ruby source files

- Fuzzer.rb - The entry point of the tool
- Basics.rb - Core abstractions described here (JavaClass, Array, Variable, Context).
- Config.rb - Fuzzer configuration, importing from YML files. 
- Statements.rb  - General Java statements generation
- ControlFlow.rb - *if-then-else*, *switch-case*, *continue-break* statements support
- Exceptions.rb - Java exceptions and try-catch statements generation 
- Loops.rb      - loops statements
- Methods.rb    - Java Methods generation
- LibMethods.rb - special cases for Java Methods generation
- Vectorization.rb - Special cases for testing vectorization

## How to run the tool

1. Set required and optional environment variables as described in "Prepare environment" section
2. Create results directory  \<results dir\>
3. Examples of run commands:
  Single process runs:

  `bash ./rt.sh -r results -p test -sp -conf ./config.yml 1000`

  This command launches Fuzzer process that generates 1000 tests and runs them on reference and tested java and compares outputs. All tests are stored in the *results* dir in *passes*, *crashes*, *fails*, and *hangs* sub-dirs, test directory name prefix is "test".

  Multiple concurrent processes runs:

  `bash ./mrt.sh -NP 10 -NT 4000 -P test -R results-1`

  This command launches 10 Fuzzer processes, each of them generates 4K tests and runs them on reference and tested java and compares outputs. All the failed tests are stored in the *results-1* dir in crashes, fails, and hangs sub-dirs. 

  Another typical commands examples:

  `bash ./mrt.sh -R results-1 -NT 10 -P build1-jdk8- -A -conf ./config.yml -sp`

  This command launches 10 processes, each of them generates unlimited number of tests till the script is killed using configuration file \<Fuzzer tool location\>/config.yml, test directory prefix is set build1-jdk8- (suffix is a combination of process counter and test counter).

  **If you only want to generate test and disable differential testing, use `-g` option.**

  Explanation of options used here:

  mrt.sh options:

  | Opt  | Meaning | 
  | ---- | ------- |
  | -R   | Save the results to the specified directory |
  | -NT  | Number of tests to generate by each thread  |
  | -NP  | Number of processes - can be omitted, then rt.sh option will be used|
  | -P   | Prefix for the test names |
  | -A   | Pass the rest arguments to rt.sh script |

  rt.sh options:

  | Opt                  | Meaning | 
  | -------------------- | ------- |
  | -r \<dir\>           | Save the results to the specified directory |
  | -p \<prefix string\> | Prefix for the test names |
  | -sp                  | save all tests incuding passed tests (by default only failed tests directories are kept |
  | -conf \<yml file\>   | Specify configuration file located in \<Fuzzer tool location\>/rb/\<yml file\>  |
  | -g                   | Generates only (don't differential test) |
  | \<number\>           | Number of test to be generated, if set to -1 or not specified then unlimited number of tests is generated and run till the script is killed |


## MM extension

MM (Memory management) extension can be enabled by editing config.yml file. This is a special configuration that generates and runs multi-threaded tests. In this case tests can produce non-deterministic output and reference run comparison is disabled, only crashes (non-zero exit codes) can be caught.
 - Change the "mode" to "MM_extreme"
 - Set "max_threads" to a value greater or equal to 1
 - Set environment variable: export MM="true" (disables result comparison (this is needed for MM_extreme configuration, because generated tests are multi-threaded and could produce non-determenistic output)
 - *Optional:* Increase TIME_OUT 
 - *Optional:* Add -Xmx option to JAVA_UNDER_TEST_OPTS and JAVA_REFERENCE_OPTS to allow more memory-intensive tests not to fail with OOM

Example: `bash MM="true" ./mrt.sh -NP 2 -R results_multi_thread -A -conf configMultiThread.yml 10000`

#### Explanation of flags used here:

| Flag               | Meaning |
| -------------------| ------  |
| -R \<dir\>         | Save the results to the specified directory |
| -NP                | Number of processes |
| -A                 | Pass the rest options to rt.sh script |
| -conf \<yml file\> | Use \<yml file\> for Fuzzer configuration |


## Basic configuration file settings

### General settings

| Parameter                    | Meaning |
| ---------------------------- | ------- |
| mode                         | 'default', 'MM_extreme' values are supported. | 
| max_size                     | max length of arrays and loops; should not be less than 10 |
| max_nested_size              | max total number of iterations of nested loops in mainTest method |
| max_nested_size_not_mainTest | max total number of iterations of nested loops in methods called from mainTest |
| min_size_fraction            | possible values \>0, \<1: minimal fraction of max_size to be guaranteed (other fraction is random(max_size-min_size_fraction*max_size) |
| max_stmts                    | generated statements max count (per method) |
| max_arr_dim                  | array max dimension |
| max_meths                    | max count of methods (excluding main) in a class |
| max_args                     | max count of a method arguments |
| max_classes                  | max count of classes. The actual number can be greater due to foreign class fields generation |
| max_threads                  | max count of runThread(Runnable obj) usage |
| p_constructor                | probability of non-trivial constructor |
| max_callers_chain            | maximum chain of methods calling each other |
| p_non_static_method          | probability of non-static method |
| mainTest_calls_num           | number of mainTest method calls, should be adjusted with compilation threshold |
| p_extends_class =50          | probability of class extension |
| p_method_override = 80       | probability that method in child class will override parent method with matching signature |
| max_num                      | default value 0x10000: 16-bit int + 1 - max int literal |
| max_exp_depth                | max depth of expression |
| max_if_stmts                 | max count of statements in if part of if statement |
| max_el_stmts                 | max count of statements in else part of if statement |
| max_try_stmts                | max count of statements in try |
| max_loop_stmts               | max count of statements in a loop |
| max_loop_depth               | max depth of nested loops |
| p_unknown_loop_limit = 0     | probability that for loop has an unknown upper limit (random expression as a loop limit) |
| p_inequality_in_loop_condition | probability that for loop will have != in loop condition |
| p_loop_iter_num_gt_max_size  | probability that for loop iterations number will be greater than max arrays size |
| max_object_array_size        | max length of reference type arrays: can be set to a lower value than primitive type arrays to avoid OOM |
| allow_object_args            | allow objects to be passed as methods arguments, 0: disallow objects to be passed as methods arguments |
| p_invoc_expr                 | probability of method invocation in expression |
| p_volatile                   | probability of a variable being volatile |
| p_else                       | probability of Else in If statement |
| p_triang                     | probability of induction var in For initial value expr |
| p_meth_reuse                 | probability of re-using known meth in an invocation |
| p_return                     | probability of return statement |
| p_var_reuse                  | probability of reusing existing var or arr |
| p_class_reuse                | probability of reusing existing class |
| p_big_switch                 | probability of big switch |
| p_packed_switch              | probability of packed switch |
| p_switch_empty_case          | probability of having a case with empty body |
| for_step                     | custom for loop step can be set (e.g., +1, -1, +2, -2, +4, -4, ...)
| p_ind_var_type               | custom (other than integer) type can be used as loop induction variable type

### Var types description:

| Ver type | Meaning |
| -------- | ------- |
| non_static | non-static field of a current class |
| static | static field of a current class |
| local | local variable of a current method |
| static_other | static field of a foreign class, example: Class1.iFld |
| local_other | non-static field of an object of a foreign class, example: Object1.iFld |
| block | the variable is declared in current block (loop) |

### Other configurable items:

- types               - supported types: primitive types (boolean, byte, short, int, long, float, double), reference types. Reference types variables can
be objects of any generated class. Besides, all generated arrays are treated as Array variables and can be assigned/reassigned.  
- statements          - supported statements: ForLoopStmt, WhileDoStmt, EnhancedForStmt, ContinueStmt, BreakStmt, IfStmt, SwitchStmt, AssignmentStmt, IntDivStmt, ReturnStmt, TryStmt, ExcStmt (exception throw), VectStmt (assignment statement following a pattern that make vectorization optimization applicable), InvocationStmt (method invocation), CondInvocStmt,SmallMethStmt
- operator            - operators list can also be customized

## Known issues

March 01, 2018:
- Sometimes two or more @Override methods with the same name and signature are generated in the same child class causing a syntax error
- Sometimes static variable is generated and used but is not declared causing a syntax error
- Possible inconsistence cycled call chains detection: rejecting call chains too strictly 
- String type testing support is not enabled

## Authors
- Mohammad R. Haghighat (Intel Corporation)
- Dmitry Khukhro (Intel Corporation)
- Andrey Yakovlev (Intel Corporation)

### Authors of 2017-2018 modifications
- Nina Rinskaya (Azul Systems)
- Ivan Popov (Azul Systems)

### Authors of 2021- modifications 
- Cong Li (NJU SPAR and ETHZ AST Labs)
