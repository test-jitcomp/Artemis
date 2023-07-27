<p align="center">
    <img width="320" alt="logo" align="center" src=".github/artemis.png">
</p>

<p align="center">
    <a href="https://github.com/test-jitcomp/Artemis/actions" alt="Build status">
        <img src="https://img.shields.io/github/actions/workflow/status/test-jitcomp/Artemis/build.yml" />
    </a>
    <a href="https://semver.org" alt="Version">
        <img src="https://img.shields.io/github/v/release/test-jitcomp/Artemis" />
    </a>
    <a href="https://google.github.io/styleguide/javaguide.html" alt="Code style">
        <img src="https://img.shields.io/badge/style-Google-blue" />
    </a>
    <a href="https://opensource.org/licenses/MIT" alt="License">
        <img src="https://img.shields.io/github/license/test-jitcomp/Artemis" />
    </a>
</p>




Artemis is a Java program mutator specifically designed to test JVM's JIT compilers.
It also provides a fuzzing framework called [artemi](./artemi) to stress-test JVM's JIT compilers.
Given a set of seed Java programs, Artemis generates a set of mutants for testing.
Artemis has already found **80+** JVM's JIT compiler bugs in three widely-used production JVMs:
[HotSpot](https://github.com/openjdk),
[OpenJ9](https://github.com/eclipse-openj9/openj9),
and [Android Runtime](https://android.googlesource.com/platform/art/).

**JIT compiler bug**:
A JIT compiler bug is a bug that otherwise *won't* manifest if the JIT compiler is disabled for example by the `-Xint` option in prevalent JVMs.



## Requirements

Artemis is tested and developed on Ubuntu and macOS platforms with the following software/hardware requirements.

- Java: >= 11
- Python: >= 3.9.0
- Ruby: >= 2.7.0



## Installation

Install Java, Python, and Ruby. For Ubuntu:

```
$ sudo apt update && apt install -y openjdk-11-jdk python3 python3-pip ruby-full
```

Download Artemis from the [Releases](https://github.com/test-jitcomp/Artemis/releases) page and unzip to a directory say `/tmp/artemis`:

```
$ unzip artemis-<version>.zip -d /tmp/artemis
```

Download Code Bricks from [Releases](https://github.com/test-jitcomp/Artemis/releases) page and unzip to a directory say `/tmp/artemis/cbs`.

```
$ unzip code-bricks.zip -d /tmp/artemis/cbs
```

Install required dependencies:

```
$ cd /tmp/artemis
$ python3 -m venv venv
$ source venv/bin/activate
$ pip install -r requirements.txt
```



## Usage: artemi

1. **Edit artemi.yaml**. See [`artemi.ex.yaml`](./artemi/artemi.ex.yaml) for an example.
    + Do fill every option marked as `<required-to-change>` with correct value. But for the `jvm` and `generator` option, only fill those related despite marked as `<required-to-change>`.
    + Do fill with *absolute* paths for each path option, instead of relative paths.
    + The framework supports to test HotSpot, OpenJ9, Graal, and ART at present. Supporting other JVMs is on the way. For this option, do *download (or build by yourself) the specific-version JVM you'd like to test and points `*_home` sub-option to the home directory of it*. To build by yourself, follow these threads:
        - [HotSpot: Building the JDK (17)](https://github.com/openjdk/jdk17u-dev/blob/master/doc/building.md)
        - [OpenJ9: Building OpenJDK Version 17  with OpenJ9](https://github.com/eclipse-openj9/openj9/blob/master/doc/build-instructions/Build_Instructions_V17.md)
        - [ART: Building Android](https://source.android.com/docs/setup/build/building); for this, use `m build-art-host` or `m build-art-target` in the last step to build quickly.
    + The framework supports to use `Java*Fuzzer` and `JFuzz` as the program generator at present. Supporting other program generators is on the way.
    + The framework has no other command line arguments and options except those listed in `artemi.yaml`.

2. **Create required directories**. Create `out_dir` and `generator.out_dir` as you've specified in `artemi.yaml`. These are output directories of artemi and the Java generator you've used, respectively.
    ```
    $ mkdir -p <out_dir> <generator.out_dir>
    ```

3. **Run the artemi framework**. The artemi framework will run in an infinite loop. You can use the shortcut CTRL+C to terminate artemi manually.
    ```bash
    $ python artemi.py artemi.yaml
    ```

4. **Check detected bugs**. All bugs that are detected are listed in `<out_dir>/differences/diffs.csv` where `<out_dir>/differences/<diff_id>` saves the seed, the mutant, the output of the seed, the output of the mutant, and Artemis' mutation log.

**Note**. Although Artemis is designed to generate syntax- and semantic-valid mutants, bugs of Artemis itself may break this. Directory `<out_dir>/mutation-failures` saves cases which causes Artemis to fail in mutating, and `<out_dir>/compilation-failures` saves cases when the mutant fails to compile.



## Usage: Artemis

Besides the artemi framework, Artemis itself can be used as a seperate program mutator. It takes as input a Java source file, and outputs a Java source file with mutations specifically designed to test JIT compilers. See required arguments and available options by `-h`.

```
$ java -jar artemis.jar -h
```



## Bug-showcases

Artemis is fruitful in finding diverse bugs ranging from segmentation faults (SIGSEGV), fatal arithmetic error (SIGFPE), emergency abort (SIGABRT), assertion failures, mis-compilations, to performance issues. These bugs affect quite a few VM components. We list some of them here. More to come.

It should be noted that, to avoid flooding their issue trackers, we discussed with the corresponding VM developers and reported some difficult-to-reproduce, flaky tests into a single issue. This kept the number of bug reports under a small limit.

In addition, some bugs listed below were made internal by the respective JVM developers and are no longer publicly accessible for security reasons.

### HotSpot

+ [JDK-8287223](https://bugs.openjdk.org/browse/JDK-8287223): P3, C1, Assertion Failure, Inlining
+ [JDK-8288198](https://bugs.openjdk.org/browse/JDK-8288198): P2, C2, Assertion Failure, Ideal Graph Building
+ [JDK-8288734](https://bugs.openjdk.org/browse/JDK-8288734): P4, C2, Assertion Failure, Ideal Graph Building
+ [JDK-8305429](https://bugs.openjdk.org/browse/JDK-8305429): P4, C2, Assertion Failure, Ideal Graph Building
+ [JDK-8290781](https://bugs.openjdk.org/browse/JDK-8290781): P3, C2, Segmentation Fault, Ideal Loop Optimization
+ [JDK-8292766](https://bugs.openjdk.org/browse/JDK-8292766): P4, C2, Assertion Failure, Ideal Loop Optimization
+ [JDK-8294217](https://bugs.openjdk.org/browse/JDK-8294217): P4, C2, Assertion Failure, Ideal Loop Optimization
+ [JDK-8294433](https://bugs.openjdk.org/browse/JDK-8294433): P4, C2, Assertion Failure, Ideal Loop Optimization
+ [JDK-8294413](https://bugs.openjdk.org/browse/JDK-8294413): P4, C2, Assertion Failure, Ideal Loop Optimization
+ [JDK-8290778](https://bugs.openjdk.org/browse/JDK-8290778): P2, C2, Segmentation Fault, Ideal Loop Optimization
+ [JDK-8288558](https://bugs.openjdk.org/browse/JDK-8288558): P4, C2, Assertion Failure, Ideal Loop Optimization
+ [JDK-8288198](https://bugs.openjdk.org/browse/JDK-8288198): P4, C2, Assertion Failure, Ideal Loop Optimization
+ [JDK-8288106](https://bugs.openjdk.org/browse/JDK-8288106): P4, C2, Assertion Failure, Ideal Loop Optimization
+ [JDK-8305797](https://bugs.openjdk.org/browse/JDK-8305797): P4, C2, Assertion Failure, Ideal Loop Optimization
+ [JDK-8288187](https://bugs.openjdk.org/browse/JDK-8288187): P4, C2, Assertion Failure, Global Constant Propagation
+ [JDK-8288206](https://bugs.openjdk.org/browse/JDK-8288206): P4, C2, Assertion Failure, Global Value Numbering
+ [JDK-8288587](https://bugs.openjdk.org/browse/JDK-8288587): P4, C2, Assertion Failure, Global Value Numbering
+ [JDK-8287217](https://bugs.openjdk.org/browse/JDK-8287217): P4, C2, Assertion Failure, Global Value Numbering
+ [JDK-8293996](https://bugs.openjdk.org/browse/JDK-8293996): P4, C2, Assertion Failure, Global Value Numbering
+ [JDK-8288204](https://bugs.openjdk.org/browse/JDK-8288204): P3, C2, Assertion Failure, Global Value Numbering
+ [JDK-8288392](https://bugs.openjdk.org/browse/JDK-8288392): P4, C2, Assertion Failure, Escape Analysis
+ [JDK-8288559](https://bugs.openjdk.org/browse/JDK-8288559): P4, C2, Assertion Failure, Register Allocation
+ [JDK-8290862](https://bugs.openjdk.org/browse/JDK-8290862): P4, C2, Segmentation Fault, Register Allocation
+ [JDK-8290776](https://bugs.openjdk.org/browse/JDK-8290776): P3, C2, Segmentation Fault, Code Generation
+ [JDK-8288560](https://bugs.openjdk.org/browse/JDK-8288560): P4, C2, Assertion Failure, Code Generation
+ [JDK-8289043](https://bugs.openjdk.org/browse/JDK-8289043): P3, C2, Assertion Failure, Code Generation
+ [JDK-8305428](https://bugs.openjdk.org/browse/JDK-8305428): P4, C2, Assertion Failure, Code Generation
+ [JDK-8288190](https://bugs.openjdk.org/browse/JDK-8288190): P2, C2, Segmentation Fault, Code Execution
+ [JDK-8290789](https://bugs.openjdk.org/browse/JDK-8290789): P3, C2, Segmentation Fault, Code Execution
+ [JDK-8290864](https://bugs.openjdk.org/browse/JDK-8290864): P4, C2, Segmentation Fault, Code Execution
+ [JDK-8288975](https://bugs.openjdk.org/browse/JDK-8288975): P3, C2, Mis-compilation, Global Code Motion
+ [JDK-8290360](https://bugs.openjdk.org/browse/JDK-8290360): P3, C2, Performance Issue

### OpenJ9

+ [15332](https://github.com/eclipse-openj9/openj9/issues/15332): Assertion Failure, Local Value Propagation
+ [15311](https://github.com/eclipse-openj9/openj9/issues/15311): Segmentation Fault, Global Value Propagation
+ [15364](https://github.com/eclipse-openj9/openj9/issues/15364): Segmentation Fault, Global Value Propagation
+ [15335](https://github.com/eclipse-openj9/openj9/issues/15335): Segmentation Fault, Loop Vectorization
+ [15474](https://github.com/eclipse-openj9/openj9/issues/15474): Segmentation Fault, Deoptimization
+ [15305](https://github.com/eclipse-openj9/openj9/issues/15305): Segmentation Fault, Register Allocation
+ [15363](https://github.com/eclipse-openj9/openj9/issues/15363): Segmentation Fault, Code Generation
+ [15599](https://github.com/eclipse-openj9/openj9/issues/15599): Assertion Failure, Code Generation
+ [15338](https://github.com/eclipse-openj9/openj9/issues/15338): Segmentation Fault, Recompilation
+ [15475](https://github.com/eclipse-openj9/openj9/issues/15475): Segmentation Fault, Garbage Collection
+ [15476](https://github.com/eclipse-openj9/openj9/issues/15476): Assertion Failure, Garbage Collection
+ [15592](https://github.com/eclipse-openj9/openj9/issues/15592): Segmentation Fault, Garbage Collection
+ [15575](https://github.com/eclipse-openj9/openj9/issues/15575): Assertion Failure, Garbage Collection
+ [17045](https://github.com/eclipse-openj9/openj9/issues/17045): Segmentation Fault, Garbage Collection
+ [17052.1](https://github.com/eclipse-openj9/openj9/issues/17052): Segmentation Fault, Garbage Collection
+ [15534](https://github.com/eclipse-openj9/openj9/issues/15534): Miscompilation
+ [15369](https://github.com/eclipse-openj9/openj9/issues/15369): Miscompilation
+ [15306.1](https://github.com/eclipse-openj9/openj9/issues/15306#issue-1269628433): Miscompilation
+ [15306.3](https://github.com/eclipse-openj9/openj9/issues/15306#issuecomment-1196676217): Miscompilation
+ [15874](https://github.com/eclipse-openj9/openj9/issues/15874): Miscompilation
+ [15347.1](https://github.com/eclipse-openj9/openj9/issues/15347#issue-1273630629): Miscompilation
+ [15347.2](https://github.com/eclipse-openj9/openj9/issues/15347#issuecomment-1157737495): Miscompilation
+ [15349](https://github.com/eclipse-openj9/openj9/issues/15349): Miscompilation
+ [17033](https://github.com/eclipse-openj9/openj9/issues/17033): Miscompilation, Store Sinking
+ [15477](https://github.com/eclipse-openj9/openj9/issues/15477): Segmentation Fault, Code Execution
+ [15569](https://github.com/eclipse-openj9/openj9/issues/15569): Assertion Failure, Code Execution
+ [17052.2](https://github.com/eclipse-openj9/openj9/issues/17052#issuecomment-1487991963): Segmentation Faults and Assertion Failures, Other JIT Components like Heap Allocation, JIT-INT Interaction, Synchronization

### ART

+ [229134124](https://issuetracker.google.com/issues/229134124): P3, Abort
+ [230079540](https://issuetracker.google.com/issues/230079540): P2, Segmentation Fault
+ [227427222](https://issuetracker.google.com/issues/227427222): P3, Segmentation Fault
+ [226413323](https://issuetracker.google.com/issues/226413323): P3, Segmentation Fault
+ [227365247](https://issuetracker.google.com/issues/227365247): P3, Segmentation Fault, Loop Optimization
+ [230079537](https://issuetracker.google.com/issues/230079537): P2, Segmentation Fault
+ [230079539](https://issuetracker.google.com/issues/230079539): P2, Segmentation Fault
+ [229184394](https://issuetracker.google.com/issues/229184394): P3, Segmentation Fault
+ [227382489](https://issuetracker.google.com/issues/227382489): P3, Miscompilation, Code Generation
+ [227365246](https://issuetracker.google.com/issues/227365246): P3, Miscompilation, Code Generation
+ [229134126](https://issuetracker.google.com/issues/229134126): P3, Miscompilation
+ [230635320](https://issuetracker.google.com/issues/230635320): P2, Miscompilation
+ [230635319](https://issuetracker.google.com/issues/230635319): P2, Miscompilation
+ [230631558](https://issuetracker.google.com/issues/230631558): P3, Miscompilation
+ [230635329](https://issuetracker.google.com/issues/230635329): P3, Miscompilation
+ [232742203](https://issuetracker.google.com/issues/232742203): P3, Miscompilation

### Graal

+ [4801](https://github.com/oracle/graal/issues/4801): Miscompilation
+ [6350](https://github.com/oracle/graal/issues/6350): Segmentation Fault, Code Execution
+ [6351](https://github.com/oracle/graal/issues/6351): Segmentation Fault, Code Execution



## Contributing

1. Artemis is developed following Google's Java style. Check [this](https://google.github.io/styleguide/javaguide.html) and the [eclipse-formatter](./eclipse-formatter.xml) file.
2. For bugs/issues/questions/feature requests please file an issue. 



## License

```
MIT License

Copyright (c) 2021 Cong Li (congli@smail.nju.edu.cn, cong.li@inf.ethz.ch)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
