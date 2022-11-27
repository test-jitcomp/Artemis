<!-- TODO Add logo, make public, and release the draft -->

<p align="center">
    <img width="320" alt="logo" align="center" src=".github/artemis.png">
</p>

<!-- <p align="center">
    <a href="https://github.com/test-jitcomp/Artemis/actions" alt="Build status">
        <img src="https://img.shields.io/github/workflow/status/test-jitcomp/Artemis/Build%20and%20archive%20check" />
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
 -->



Artemis is a Java program mutator specifically designed to test JVM's JIT compilers.
It also provides a fuzzing framework called [artemi](./artemi) to stress-test JVM's JIT compilers.
Given a set of seed Java programs, Artemis generates a set of mutants for testing.
Artemis has already found **60+** JVM's JIT compiler bugs in three widely-used production JVMs:
[HotSpot](https://github.com/openjdk),
[OpenJ9](https://github.com/eclipse-openj9/openj9),
and [Android Runtime](https://android.googlesource.com/platform/art/).

**JIT compiler bug**:
A JIT compiler bug is a bug that otherwise *won't* manifest if the JIT compiler is disabled for example by the `-Xint` option in prevalent JVMs.



## Installation

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

### HotSpot

+ [JDK-8287223](https://bugs.openjdk.org/browse/JDK-8287223): P3, Inlining
+ [JDK-8288198](https://bugs.openjdk.org/browse/JDK-8288198): P2, Ideal Graph Building
+ [JDK-8288734](https://bugs.openjdk.org/browse/JDK-8288734): P4, Ideal Graph Building
+ [JDK-8290781](https://bugs.openjdk.org/browse/JDK-8290781): P3, Ideal Loop Optimization
+ [JDK-8292766](https://bugs.openjdk.org/browse/JDK-8292766): P4, Ideal Loop Optimization
+ [JDK-8294217](https://bugs.openjdk.org/browse/JDK-8294217): P4, Ideal Loop Optimization
+ [JDK-8294433](https://bugs.openjdk.org/browse/JDK-8294433): P4, Ideal Loop Optimization
+ [JDK-8294413](https://bugs.openjdk.org/browse/JDK-8294413): P4, Ideal Loop Optimization
+ [JDK-8290778](https://bugs.openjdk.org/browse/JDK-8290778): P2, Ideal Loop Optimization
+ [JDK-8288558](https://bugs.openjdk.org/browse/JDK-8288558): P4, Ideal Loop Optimization
+ [JDK-8288198](https://bugs.openjdk.org/browse/JDK-8288198): P4, Ideal Loop Optimization
+ [JDK-8288106](https://bugs.openjdk.org/browse/JDK-8288106): P4, Ideal Loop Optimization
+ [JDK-8288187](https://bugs.openjdk.org/browse/JDK-8288187): P4, Global Constant Propagation
+ [JDK-8288206](https://bugs.openjdk.org/browse/JDK-8288206): P4, Global Value Numbering
+ [JDK-8288587](https://bugs.openjdk.org/browse/JDK-8288587): P4, Global Value Numbering
+ [JDK-8287217](https://bugs.openjdk.org/browse/JDK-8287217): P4, Global Value Numbering
+ [JDK-8293996](https://bugs.openjdk.org/browse/JDK-8293996): P4, Global Value Numbering
+ [JDK-8288204](https://bugs.openjdk.org/browse/JDK-8288204): P3, Global Value Numbering
+ [JDK-8288392](https://bugs.openjdk.org/browse/JDK-8288392): P4, Escape Analysis
+ [JDK-8288559](https://bugs.openjdk.org/browse/JDK-8288559): P4, Register Allocation 
+ [JDK-8290862](https://bugs.openjdk.org/browse/JDK-8290862): P4, Register Allocation
+ [JDK-8290776](https://bugs.openjdk.org/browse/JDK-8290776): P3, Code Generation
+ [JDK-8288560](https://bugs.openjdk.org/browse/JDK-8288560): P4, Code Generation
+ [JDK-8289043](https://bugs.openjdk.org/browse/JDK-8289043): P3, Code Generation
+ [JDK-8288190](https://bugs.openjdk.org/browse/JDK-8288190): P2, Code Execution
+ [JDK-8290789](https://bugs.openjdk.org/browse/JDK-8290789): P3, Code Execution
+ [JDK-8290864](https://bugs.openjdk.org/browse/JDK-8290864): P4, Code Execution
+ [JDK-8288975](https://bugs.openjdk.org/browse/JDK-8288975): P3, mis-compilation, Global Code Motion
+ [JDK-8290360](https://bugs.openjdk.org/browse/JDK-8290360): P3, Performance

### OpenJ9

+ [15332](https://github.com/eclipse-openj9/openj9/issues/15332): Local Value Propagation
+ [15311](https://github.com/eclipse-openj9/openj9/issues/15311): Global Value Propagation
+ [15364](https://github.com/eclipse-openj9/openj9/issues/15364): Global Value Propagation
+ [15335](https://github.com/eclipse-openj9/openj9/issues/15335): Loop Vectorization
+ [15474](https://github.com/eclipse-openj9/openj9/issues/15474): Deoptimization
+ [15305](https://github.com/eclipse-openj9/openj9/issues/15305): Register Allocation
+ [15363](https://github.com/eclipse-openj9/openj9/issues/15363): Code Generation
+ [15599](https://github.com/eclipse-openj9/openj9/issues/15599): Code Generation
+ [15338](https://github.com/eclipse-openj9/openj9/issues/15338): Recompilation
+ [15477](https://github.com/eclipse-openj9/openj9/issues/15477): Code Execution
+ [15475](https://github.com/eclipse-openj9/openj9/issues/15475): Code Execution
+ [15569](https://github.com/eclipse-openj9/openj9/issues/15569): Code Execution
+ [15476](https://github.com/eclipse-openj9/openj9/issues/15476): Garbage Collection
+ [15592](https://github.com/eclipse-openj9/openj9/issues/15592): Garbage Collection
+ [15575](https://github.com/eclipse-openj9/openj9/issues/15575): Garbage Collection
+ [15534](https://github.com/eclipse-openj9/openj9/issues/15534): miscompilation
+ [15369](https://github.com/eclipse-openj9/openj9/issues/15369): miscompilation
+ [15306.1](https://github.com/eclipse-openj9/openj9/issues/15306#issue-1269628433): miscompilation
+ [15306.3](https://github.com/eclipse-openj9/openj9/issues/15306#issuecomment-1196676217): miscompilation
+ [15874](https://github.com/eclipse-openj9/openj9/issues/15874): miscompilation
+ [15347.1](https://github.com/eclipse-openj9/openj9/issues/15347#issue-1273630629): miscompilation
+ [15347.2](https://github.com/eclipse-openj9/openj9/issues/15347#issuecomment-1157737495): miscompilation
+ [15349](https://github.com/eclipse-openj9/openj9/issues/15349): miscompilation

### ART

+ [229134124](https://issuetracker.google.com/issues/229134124): P3, -,
+ [230079540](https://issuetracker.google.com/issues/230079540): P2, -,
+ [227427222](https://issuetracker.google.com/issues/227427222): P3, -,
+ [226413323](https://issuetracker.google.com/issues/226413323): P3, -,
+ [227365247](https://issuetracker.google.com/issues/227365247): P3, Loop Optimization,
+ [230079537](https://issuetracker.google.com/issues/230079537): P2, -,
+ [230079539](https://issuetracker.google.com/issues/230079539): P2, -,
+ [229184394](https://issuetracker.google.com/issues/229184394): P3, -,
+ [227382489](https://issuetracker.google.com/issues/227382489): P3, miscompilation, Code Generation
+ [227365246](https://issuetracker.google.com/issues/227365246): P3, miscompilation, Code Generation
+ [229134126](https://issuetracker.google.com/issues/229134126): P3, miscompilation
+ [230635320](https://issuetracker.google.com/issues/230635320): P2, miscompilation
+ [230635319](https://issuetracker.google.com/issues/230635319): P2, miscompilation
+ [230631558](https://issuetracker.google.com/issues/230631558): P3, miscompilation
+ [230635329](https://issuetracker.google.com/issues/230635329): P3, miscompilation
+ [232742203](https://issuetracker.google.com/issues/232742203): P3, miscompilation

### Graal

+ [4801](https://github.com/oracle/graal/issues/4801)



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