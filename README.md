<!-- TODO: Add Logo -->



# Artemis

A Java program mutator specifically designed to test JVM's JIT compilers.
Artemis also provides a fuzzing framework called [artemi](./artemi) to stress-test JVM's JIT compilers.
Given a set of seed Java programs, Artemis generates a set of mutants for testing.
Artemis has already found **60+** JVM's JIT compiler bugs in three widely-used production JVMs:
[HotSpot](https://github.com/openjdk),
[OpenJ9](https://github.com/eclipse-openj9/openj9),
and [Android Runtime](https://android.googlesource.com/platform/art/).

**JIT compiler bug**:
A JIT compiler bug is a bug that otherwise *won't* manifest if the JIT compiler is disabled for example by the `-Xint` option in prevalent JVMs.



## Installation

Download Artemis from the [Releases](releases) page and unzip to a directory say `/tmp/artemis`:

```
$ unzip artemis-<version>.zip -d /tmp/artemis
```

Download Code Bricks from [Releases](releases) page and unzip to a directory say `/tmp/artemis/cbs`.

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
    + The framework supports to test HotSpot, OpenJ9, Graal, and ART at present. Supporting other JVMs is on the way. For this option, do *build the JVM with the version you'd like to test* manually by yourself. For example, follow [this thread](https://github.com/openjdk/jdk17u-dev/blob/master/doc/building.md) to build HotSpot with JDK17 and [this thread](https://github.com/eclipse-openj9/openj9/blob/master/doc/build-instructions/Build_Instructions_V17.md) for OpenJ9 with JDK17.
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
$ java -jar Artemis-<version>.jar -h
```



## Contributions

1. Artemis is developed following Google's Java style. Check [this](https://google.github.io/styleguide/javaguide.html) and the [eclipse-formatter](./eclipse-formatter) file.
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