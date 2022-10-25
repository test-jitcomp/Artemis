# MIT License
# 
# Copyright (c) 2021 Cong Li (congli@smail.nju.edu.cn, cong.li@inf.ethz.ch)
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import random
from yaml import safe_load as yaml_load

from jvm import *
from runner import *
from utils import *


"""
artemi: A testing framework for JIT compilers of modern Java Virtual
        Machines like HotSpot, OpenJ9, ART, and Graal.

This framework relies on a JavaCodeGenerator for generating .java files,
and Artemis (a mutator) to mutate .java: the reference .java and the
mutant .java should satisfy the following constraint:

           jvm(reference) === jvm(mutant).

After mutation, the reference and mutant are compiled using the provided
Java toolchain and fed into JVM for differential testing.

Any violation of the above constraint is deemed as a failure. Succeeded
tests are directly discarded while failed tests are saved (including
reference, mutant, and their outputs).
"""


AxGenerator = MprGenerator[Path]


#
# Java Code Generators
#

class JavaGenerator(AxGenerator):

    @abstractmethod
    def __next__(self) -> Path:
        """
        Every JavaGenerator should produce a single test .java file.
        All java dependencies of the generated .java file should be
        put under the same directory as the generated .java file
        :return: path to the generated .java file
        """
        pass

    @abstractmethod
    def __iter__(self):
        pass


#
# Java*Fuzzer: A Random Java Code Generator
#

class JavaFuzzer(JavaGenerator):

    NAME = 'Java*Fuzzer'

    def __init__(self, home: Path, conf: Path, out_dir: Path, seed: Optional[int] = None):
        super(JavaFuzzer, self).__init__()
        self.home = home
        self.conf = conf
        self.out_dir = out_dir
        self.load_path = home / 'rb'
        self.fuzzer = home / 'rb' / 'Fuzzer.rb'
        script_check(self.fuzzer.exists(), f'Fuzzer.rb does not exist in {JavaFuzzer.NAME}\'s home: {self.home}')
        self._fuzzer_util_java_file = home / 'rb' / 'FuzzerUtils.java'
        script_check(self._fuzzer_util_java_file.exists(),
                     f'File `FuzzerUtils.javas` does not exist in {JavaFuzzer.NAME}\'s home: {self.home}')
        self._curr_index = 0
        if seed is not None: random.seed(seed)

    def __next__(self) -> Path:
        self._curr_index += 1

        class_dir = self.out_dir / str(self._curr_index)
        res = Command.mkdir(class_dir, can_exist=True)
        script_check(res.retcode == 0, f'Fail to mkdir for {class_dir}: {res.output}')

        java_file = class_dir / 'Test.java'
        with java_file.open('w', encoding='utf-8') as opened_java_file:
            res = Command.redirected_run(f'ruby'
                                         f' -I {self.load_path.absolute()}'
                                         f' {self.fuzzer.absolute()}'
                                         f' -f {self.conf.absolute()}',
                                         stdout=opened_java_file,
                                         stderr=STDOUT,
                                         timeout=1 * 60)
            script_check(res.retcode == 0, f'{self.NAME} failed to generate java file: {java_file.read_text()}')

        res = Command.copy(self._fuzzer_util_java_file, class_dir)
        script_check(res.retcode == 0, f'Failed to copy FuzzerUtils.java to {class_dir}: {res.output}')

        return java_file

    def __iter__(self):
        # Invoked when iterator is created, used to do
        # initialization before iterating inside items
        self._curr_index = -1
        return self


class JFuzz(JavaGenerator):

    NAME = 'JFuzz'

    def __init__(self, home: Path, out_dir: Path, seed: Optional[int] = None):
        super(JFuzz, self).__init__()
        self.home = home
        self.out_dir = out_dir
        script_check(sys_arch_is_64(), f'{JFuzz.NAME} does not support 32bit platform')
        os_type = sys_os_type()
        if os_type == "linux":
            self.jfuzz = self.home / 'bin' / 'linux' / 'x86_64' / 'jfuzz'
        elif os_type == "darwin":
            self.jfuzz = self.home / 'bin' / 'darwin' / 'x86_64' / 'jfuzz'
        else:
            self.jfuzz = None
        script_check(self.jfuzz is not None, f'{JFuzz.NAME} does not support {os_type} platform')
        script_check(self.jfuzz.exists(), f'Binary jfuzz does not exist in {JFuzz.NAME}\'s home: {self.home}')
        self.max_expr_depth = 5
        self.max_stmt_list_size = 10
        self.max_nested_branch = 5
        self.max_nested_loop = 3
        self.max_nested_try_catch = 2
        self._curr_index = 0
        if seed is not None: random.seed(seed)

    def set_max_stmt_list_size(self, size):
        self.max_stmt_list_size = size

    def set_max_nested_branch(self, num):
        self.max_nested_branch = num

    def set_max_nested_loop(self, num):
        self.max_nested_loop = num

    def set_max_nested_try_catch(self, num):
        self.max_nested_try_catch = num

    def __next__(self) -> Path:
        self._curr_index += 1

        class_dir = self.out_dir / str(self._curr_index)
        res = Command.mkdir(class_dir, can_exist=True)
        script_check(res.retcode == 0, f'Fail to mkdir for {class_dir}: {res.output}')

        java_file = class_dir / 'Test.java'
        with java_file.open('w', encoding='utf-8') as opened_java_file:
            res = Command.redirected_run(f'{self.jfuzz}'
                                         f' -s {random.randint(0, 0xFFFFFFFF)}'
                                         f' -d {self.max_expr_depth}'
                                         f' -l {self.max_stmt_list_size}'
                                         f' -i {self.max_nested_branch}'
                                         f' -n {self.max_nested_loop}'
                                         f' -t {self.max_nested_try_catch}',
                                         stdout=opened_java_file,
                                         stderr=STDOUT,
                                         timeout=1 * 60)
            script_check(res.retcode == 0, f'{self.NAME} failed to generate java file: {java_file.read_text()}')

        return java_file

    def __iter__(self):
        # Invoked when iterator is created, used to do
        # initialization before iterating inside items
        self._curr_index = -1
        return self


class ExistingTests(JavaGenerator):

    """
    ExistingTests enumerates existing tests in a given directory.
    Every test in the given directory should be a directory containing
    a 'MANIFEST' file and some .java files. The MANIFEST file should
    be a single-line file which writes the name of the test like
    'Test' indicating Test.java in the test's directory. ExistingTests
    treats all other .java files not in MANIFEST as dependencies.
    """

    NAME = 'ExistingTests'

    def __init__(self, test_dir: Path, out_dir: Path):
        script_check(test_dir.exists(), f"Directory does not exist: {test_dir}")
        script_check(test_dir.is_dir(), f"Not directory: {test_dir}")
        self.test_dir = test_dir
        self.out_dir = out_dir
        self._curr_index = 0
        self._curr_tests = []
        self._dir_iter = None

    def __next__(self):
        self._curr_index += 1

        if len(self._curr_tests) == 0:
            # Find and parser next packaged tests
            class_dir = next(self._dir_iter)
            manifest = class_dir / 'MANIFEST'
            while not class_dir.is_dir() or not manifest.exists():
                class_dir = next(self._dir_iter)
                manifest = class_dir / 'MANIFEST'
            test_names = manifest.read_text().splitlines(keepends=False)
            for name in test_names:
                java_file = class_dir / f'{name}.java'
                script_check(java_file.exists() and java_file.is_file(),
                             f"File listed MANIFEST does not exist or is not a file: {java_file}")
                self._curr_tests.append(java_file)
        script_check(len(self._curr_tests) != 0, f"No tests found")
        java_file = self._curr_tests.pop()
        class_dir = java_file.parent
        file_name = java_file.name

        # Copy to out_dir and return the java file in out_dir
        out_class_dir = self.out_dir / str(self._curr_index)
        res = Command.copy(class_dir, out_class_dir, is_dir=True)
        script_check(res.retcode == 0, f'Fail to copy directory to {out_class_dir}: {res.output}')

        (out_class_dir / 'LOCATION').write_text(str(java_file.absolute()))

        return out_class_dir / file_name

    def __iter__(self):
        self._curr_index = -1
        self._curr_tests.clear()
        self._dir_iter = self.test_dir.iterdir()
        return self


#
# Artemis: A Mutator for Stress-testing JVM's JIT compiler
#

class ArtemisMutateResult:

    def __init__(self, mutant, output):
        # mutant: mutant .java file, None means mutation fails
        # output: if mutant is None, this is the error message
        #         otherwise, the command output
        self.mutant = mutant
        self.output = output


class Artemis:

    NAME = 'Artemis'
    POLICIES = ['artemis']

    def __init__(self, jar_path: Path, java: Java):
        super(Artemis, self).__init__()
        self.jar_path = jar_path
        self.java = java
        self.extra_opts = {}
        self.policy = 'artemis'
        self.min_loop_trip = 32
        self.max_loop_trip = 256
        self.bricks: Optional[Path] = None

    def update_policy(self, policy: str):
        script_check(policy in self.POLICIES, f"Unsupported policy: {policy}")
        self.policy = policy

    def update_bricks(self, bricks: Path):
        self.bricks = bricks

    def update_extra_opts(self, opts: dict):
        self.extra_opts = opts

    def update_min_max_loop_trips(self, min_val: int, max_val: int):
        script_check(0 <= min_val <= max_val, "Min/max values must satisfy 0<=min<=max")
        self.min_loop_trip = min_val
        self.max_loop_trip = max_val

    def mutate(self, file: Path, out_dir: Path,
               seed: int = int(time.time() * 1_000_000),
               timeout: int = 10) -> ArtemisMutateResult:
        x_opt_list = [f'{k}:{self.extra_opts[k]}' for k in self.extra_opts]
        x_opt = f"-X{','.join(x_opt_list)}" if len(x_opt_list) > 0 else ""
        brick_opt = f"-b {self.bricks.absolute()}" if self.bricks is not None else ""
        result = self.java.jar_run(self.jar_path,
                                   main_class=None,
                                   main_args=f'-v'
                                             f' {x_opt}'
                                             f' -s {seed}'
                                             f' -p {self.policy}'
                                             f' -m {self.min_loop_trip}'
                                             f' -M {self.max_loop_trip}'
                                             f' {brick_opt}'
                                             f' -o {out_dir}'
                                             f' -i {file.absolute()}',
                                   timeout=timeout)
        if result.retcode != 0:
            return ArtemisMutateResult(None, result.output)
        return ArtemisMutateResult(out_dir / file.name, result.output)


#
# Conf
#

def parse_java_conf(key_path: str, java_conf: dict) -> dict:
    home_str = check_conf_type(f'{key_path}.home', java_conf['home'], str)
    java_conf['home'] = check_conf_dir(f'{key_path}.home', home_str)
    script_check((java_conf['home'] / 'bin' / 'javac').exists(),
                 f'{key_path}.home is incorrect: command `javac` does not exist')
    script_check((java_conf['home'] / 'bin' / 'java').exists(),
                 f'{key_path}.home is incorrect: command `java` does not exist')
    check_conf_type(f'{key_path}.classpath', java_conf['classpath'], list)
    return java_conf


def parse_jvm_conf(key_path: str, jvm_conf: dict) -> dict:
    check_conf_type(f'{key_path}.options', jvm_conf['options'], list)
    jvm_type = check_conf_type(f'{key_path}.type', jvm_conf['type'], str)
    if jvm_type == 'host-art':
        host_home_str = check_conf_type(f'{key_path}.host_home', jvm_conf['host_home'], str)
        jvm_conf['host_home'] = check_conf_dir(f'{key_path}.host_home', host_home_str)
        script_check((jvm_conf['host_home'] / 'host' / 'linux-x86' / 'bin' / 'art').exists(),
                     f"{key_path}.host_home is invalid (must contain host directory): {jvm_conf['host_home']}")
        check_conf_type(f'{key_path}.min_api', jvm_conf['min_api'], int)
    elif jvm_type == 'target-art':
        android_home_str = check_conf_type(f'{key_path}.android_home', jvm_conf['android_home'], str)
        jvm_conf['android_home'] = check_conf_dir(f'{key_path}.android_home', android_home_str)

        build_tools = check_conf_type(f'{key_path}.build_tools', jvm_conf['build_tools'], str)
        script_check((jvm_conf['android_home'] / 'build-tools' / build_tools).exists(),
                     f"{key_path}.build_tools does not exist: {build_tools}")

        adb = jvm_conf['android_home'] / 'platform-tools' / 'adb'
        script_check(adb.exists(), f'{key_path}.home is incorrect: `adb` command does not exist')

        serial_no = check_conf_type(f'{key_path}.serial_no', jvm_conf['serial_no'], str)
        res = Command.run(f'{adb} -s {serial_no} shell echo')
        script_check(res.retcode == 0, f'{key_path}.serial_no does not alive: {serial_no}')

        check_conf_type(f'{key_path}.app_process', jvm_conf['app_process'], bool)
        check_conf_type(f'{key_path}.min_api', jvm_conf['min_api'], int)
    elif jvm_type == 'hotspot':
        home_str = check_conf_type(f'{key_path}.java_home', jvm_conf['java_home'], str)
        jvm_conf['java_home'] = check_conf_dir(f'{key_path}.java_home', home_str)
        script_check((jvm_conf['java_home'] / 'bin' / 'javac').exists(),
                     f'{key_path}.java_home is incorrect: command `javac` does not exist')
        script_check((jvm_conf['java_home'] / 'bin' / 'java').exists(),
                     f'{key_path}.java_home is incorrect: command `java` does not exist')
        check_conf_type(f'{key_path}.classpath', jvm_conf['classpath'], list)
    elif jvm_type == 'openj9':
        home_str = check_conf_type(f'{key_path}.java_home', jvm_conf['java_home'], str)
        jvm_conf['java_home'] = check_conf_dir(f'{key_path}.java_home', home_str)
        script_check((jvm_conf['java_home'] / 'bin' / 'javac').exists(),
                     f'{key_path}.java_home is incorrect: command `javac` does not exist')
        script_check((jvm_conf['java_home'] / 'bin' / 'java').exists(),
                     f'{key_path}.java_home is incorrect: command `java` does not exist')
        check_conf_type(f'{key_path}.classpath', jvm_conf['classpath'], list)
    elif jvm_type == 'graal':
        home_str = check_conf_type(f'{key_path}.java_home', jvm_conf['java_home'], str)
        jvm_conf['java_home'] = check_conf_dir(f'{key_path}.java_home', home_str)
        script_check((jvm_conf['java_home'] / 'bin' / 'javac').exists(),
                     f'{key_path}.java_home is incorrect: command `javac` does not exist')
        script_check((jvm_conf['java_home'] / 'bin' / 'java').exists(),
                     f'{key_path}.java_home is incorrect: command `java` does not exist')
        check_conf_type(f'{key_path}.classpath', jvm_conf['classpath'], list)
    return jvm_conf


def parse_artemis_conf(key_path: str, ax_conf: dict) -> dict:
    jar_str = check_conf_type(f'{key_path}.jar', ax_conf['jar'], str)
    ax_conf['jar'] = check_conf_file(f'{key_path}.jar', jar_str)

    bricks_str = check_conf_type(f'{key_path}.code_bricks', ax_conf['code_bricks'], str)
    ax_conf['code_bricks'] = check_conf_dir(f'{key_path}.code_bricks', bricks_str)

    policy = check_conf_type(f'{key_path}.policy', ax_conf['policy'], str)
    script_check(policy in Artemis.POLICIES,
                 f"{key_path}.policy does not support {policy}")

    check_conf_type(f'{key_path}.min_loop_trip', ax_conf['min_loop_trip'], int)
    check_conf_type(f'{key_path}.max_loop_trip', ax_conf['max_loop_trip'], int)

    check_conf_type(f'{key_path}.extra_opts', ax_conf['extra_opts'], dict)

    return ax_conf


def parse_generator_conf(key_path: str, gen_conf: dict) -> dict:
    out_dir_str = check_conf_type(f'{key_path}.out_dir', gen_conf['out_dir'], str)
    gen_conf['out_dir'] = check_conf_dir(f'{key_path}.out_dir', out_dir_str)

    which_gen = gen_conf['name']
    # Check generator options according to generator
    if which_gen == JavaFuzzer.NAME:
        conf_str = check_conf_type(f'{key_path}.conf', gen_conf['conf'], str)
        if conf_str == "none" or conf_str == "None":
            gen_conf['conf'] = None
        else:
            gen_conf['conf'] = check_conf_file_or_dir(f'{key_path}.conf', conf_str)
    elif which_gen == JFuzz.NAME:
        check_conf_type(f'{key_path}.max_stmt_list_size', gen_conf['max_stmt_list_size'], int)
        check_conf_type(f'{key_path}.max_nested_branch', gen_conf['max_nested_branch'], int)
        check_conf_type(f'{key_path}.max_nested_loop', gen_conf['max_nested_loop'], int)
        check_conf_type(f'{key_path}.max_nested_try_catch', gen_conf['max_nested_try_catch'], int)
    elif which_gen == ExistingTests.NAME:
        exist_dir_str = check_conf_type(f'{key_path}.exist_dir', gen_conf['exist_dir'], str)
        gen_conf['exist_dir'] = check_conf_dir(f'{key_path}.exist_dir', exist_dir_str)
    else:
        script_check(False, f'{key_path}.name does not support {which_gen} at present')
    return gen_conf


def parse_conf(key_path: str, conf: dict) -> dict:
    """
    Transform environment variable in conf to the variable's value
    """
    for key in conf:
        if type(conf[key]) == dict:
            parse_conf(f'{key_path}.{key}', conf[key])
        elif type(conf[key]) == str and conf[key].startswith("$"):
            conf[key] = check_conf_env_var(f'{key_path}.{key}', conf[key])
    return conf


def read_conf(conf_path: Path) -> dict:
    conf_obj = parse_conf('', yaml_load(conf_path.open(encoding='utf-8')))

    check_conf_type('.num_proc', conf_obj['num_proc'], int)
    check_conf_type('.prog_timeout', conf_obj['prog_timeout'], int)
    check_conf_type('.rand_seed', conf_obj['rand_seed'], int)
    check_conf_type('.num_mutation', conf_obj['num_mutation'], int)
    check_conf_type('.save_timeouts', conf_obj['save_timeouts'], bool)

    check_conf_type('.out_dir', conf_obj['out_dir'], str)
    conf_obj['out_dir'] = check_conf_dir('.out_dir', conf_obj['out_dir'])

    check_conf_type('.java', conf_obj['java'], dict)
    conf_obj['java'] = parse_java_conf('.java', conf_obj['java'])

    check_conf_type('.jvm', conf_obj['jvm'], dict)
    conf_obj['jvm'] = parse_jvm_conf('.jvm', conf_obj['jvm'])

    check_conf_type('.generator', conf_obj['generator'], dict)
    conf_obj['generator'] = parse_generator_conf('.generator', conf_obj['generator'])

    check_conf_type('.artemis', conf_obj['artemis'], dict)
    conf_obj['artemis'] = parse_artemis_conf('.artemis', conf_obj['artemis'])

    return conf_obj


def create_java_from_conf(java_conf: dict):
    java = Java(java_conf['home'])
    java.set_default_classpath(java_conf['classpath'])
    return java


def create_jvm_from_conf(jvm_conf: dict, java: Java):
    jvm = None
    if jvm_conf['type'] == 'host-art':
        jvm = HostArt(jvm_conf['host_home'], java)
        jvm.set_min_api(jvm_conf['min_api'])
    elif jvm_conf['type'] == 'target-art':
        jvm = TargetArt(jvm_conf['android_home'], jvm_conf['build_tools'], java)
        jvm.enable_app_process(jvm_conf['app_process'])
        jvm.connect(jvm_conf['serial_no'])
        jvm.set_min_api(jvm_conf['min_api'])
    elif jvm_conf['type'] == 'hotspot':
        jvm = HotSpot(jvm_conf['java_home'])
        jvm.set_default_classpath(jvm_conf['classpath'])
    elif jvm_conf['type'] == 'openj9':
        jvm = OpenJ9(jvm_conf['java_home'])
        jvm.set_default_classpath(jvm_conf['classpath'])
    elif jvm_conf['type'] == 'graal':
        jvm = Graal(jvm_conf['java_home'])
        jvm.set_default_classpath(jvm_conf['classpath'])
    else:
        script_check(False, f"Unsupported jvm type: {jvm_conf['type']}")
    script_check(jvm is not None, f'Jvm is not created')
    script_check(jvm.is_alive(), f"Jvm is not alive: {jvm}")
    jvm.set_default_opts(jvm_conf['options'])
    return jvm


def create_generator_from_conf(gen_conf: dict):
    if gen_conf['name'] == JavaFuzzer.NAME:
        script_check(JAVA_FUZZER_PATH.exists(), 'JAVA_FUZZER_PATH is incorrectly set: does not exist')
        script_check(JAVA_FUZZER_DEFAULT_CONF.exists(), 'JAVA_FUZZER_DEFAULT_CONF is incorrectly set: does not exist')
        jf_conf_file = gen_conf['conf'] if gen_conf['conf'] is not None else JAVA_FUZZER_DEFAULT_CONF
        java_gen = JavaFuzzer(JAVA_FUZZER_PATH,
                              jf_conf_file,
                              gen_conf['out_dir'])
    elif gen_conf['name'] == JFuzz.NAME:
        script_check(JFUZZ_PATH.exists(), 'JFUZZ_PATH is incorrectly set: does not exist')
        java_gen = JFuzz(JFUZZ_PATH, gen_conf['out_dir'])
        java_gen.set_max_stmt_list_size(gen_conf['max_stmt_list_size'])
        java_gen.set_max_nested_branch(gen_conf['max_nested_branch'])
        java_gen.set_max_nested_loop(gen_conf['max_nested_loop'])
        java_gen.set_max_nested_try_catch(gen_conf['max_nested_try_catch'])
    elif gen_conf['name'] == ExistingTests.NAME:
        java_gen = ExistingTests(gen_conf['exist_dir'], gen_conf['out_dir'])
    else:
        script_check(False, f"Currently does not support generator: {gen_conf['name']}")
        assert False  # Workaround to dismiss following java_gen warnings
    return java_gen


def create_artemis_from_conf(ax_conf: dict, java: Java):
    artemis = Artemis(ax_conf['jar'], java)
    artemis.update_policy(ax_conf['policy'])
    artemis.update_min_max_loop_trips(ax_conf['min_loop_trip'],
                                      ax_conf['max_loop_trip'])
    artemis.update_extra_opts(ax_conf['extra_opts'])
    if ax_conf['code_bricks'] is not None:
        artemis.update_bricks(ax_conf['code_bricks'])
    return artemis


#
# Test flow
#

JAVA_FUZZER_PATH = Path('./java_fuzzer')
JAVA_FUZZER_DEFAULT_CONF = JAVA_FUZZER_PATH / 'config.yml'

JFUZZ_PATH = Path('./jfuzz')

MUTANTS_DIR_NAME = 'mutants'
TIMEOUT_SPEC_CODE = 0xC0FFEE


class MutantResult: pass


class MutationError(MutantResult):
    """
    Fails to mutate a reference file
    """
    def __init__(self, mutant_dir, err_msg):
        self.mutant_dir = mutant_dir
        self.err_msg = err_msg


class MutantCompError(MutantResult):
    """
    The mutant does not compile
    """
    def __init__(self, mutant_file, mutation_msg, err_msg):
        self.mutant_file = mutant_file
        self.mutation_msg = mutation_msg
        self.err_msg = err_msg

    @property
    def mutant_dir(self):
        return self.mutant_file.parent


class MutantAllTmoError(MutantResult):
    """
    All runs timed-out
    """
    def __init__(self, mutant_file, mutation_msg):
        self.mutant_file = mutant_file
        self.mutation_msg = mutation_msg

    @property
    def mutant_dir(self):
        return self.mutant_file.parent


class MutantRunResult(MutantResult):
    """
    Successfully run mutants
    """
    def __init__(self, mutant_file, mutation_msg, result):
        self.mutant_file = mutant_file
        self.mutation_msg = mutation_msg
        self.result: CommandResult = result

    @property
    def mutant_dir(self):
        return self.mutant_file.parent

    @property
    def retcode(self):
        return self.result.retcode

    @property
    def output(self):
        return self.result.output


class TestResult:

    def __init__(self, ref_file):
        self.ref_file: Path = ref_file


class RefTmoTestResult(TestResult): pass


class NormalTestResult(TestResult):

    def __init__(self, ref_file, ref_result):
        super(NormalTestResult, self).__init__(ref_file)
        self.ref_result: CommandResult = ref_result
        self.mut_results: List[MutantResult] = []


def run_test(ref_file: Path,
             jvm: JavaVM, artemis: Artemis,
             num_mutation: int, run_timeout: int) -> TestResult:
    compilation_timeout = 30  # seconds
    mutation_timeout = 30     # seconds

    ref_dir = ref_file.parent
    print(f'+ Run test: received reference {ref_file}, '
          f'num-mutation: {num_mutation}, run-timeout: {run_timeout}s')

    # Compile reference, should always compile
    res = jvm.compile(ref_file, timeout=compilation_timeout)
    script_check(res.clazz is not None, f"Failed to compile reference Java code: {ref_file}: {res.err_msg}")

    # Run the ref_file under JVM
    print(f'- JVM exec: running reference under JVM, {ref_file}')
    try:
        # Allow tests to exit with !0 code
        ref_result = jvm.run(res, timeout=run_timeout)
    except TimeoutExpired as e:
        print(f'- Timeout: run reference timed out under JVM, from {ref_file}: {e}')
        return RefTmoTestResult(ref_file)  # skip references that are timeout

    test_result = NormalTestResult(ref_file, ref_result)

    # Mutate until successfully run num_mutation times
    succeeded_mutation, max_num_mutation = 0, 2 * num_mutation
    for i in range(max_num_mutation):
        ref_name = ref_dir.name
        mutant_dir = ref_dir / MUTANTS_DIR_NAME / str(i)
        res = Command.mkdir(mutant_dir, can_exist=True)
        script_check(res.retcode == 0, f'Cannot mkdir for {mutant_dir.absolute()}: {res.output}')
        print(f'- Mutate {ref_name}: {succeeded_mutation}/{num_mutation}, {mutant_dir}')

        res = artemis.mutate(ref_file,
                             seed=random.randint(0, 0xFFFFFFFF),
                             out_dir=mutant_dir,
                             timeout=mutation_timeout)
        if res.mutant is None:
            # Failed to mutate ref_file
            test_result.mut_results.append(MutationError(mutant_dir, res.output))
            continue

        mutant_file = res.mutant
        mutation_msg = res.output

        # Copy every non-.java/.class/.dex file to mutant_dir
        for f in ref_dir.iterdir():
            if f.suffix not in ['.java', '.class', '.dex']: continue
            if f.name == ref_file.name: continue
            res = Command.copy(f, mutant_dir)
            script_check(res.retcode == 0, f'Cannot copy {f} to {mutant_dir}: {res.output}')

        # Compile mutant
        res = jvm.compile(mutant_file, timeout=compilation_timeout)
        if res.clazz is None:
            # Failed to compile mutant file
            test_result.mut_results.append(MutantCompError(mutant_file, mutation_msg, res.err_msg))
            continue

        print(f'- JVM exec: running mutant under JVM, {mutant_file}')
        try:
            mut_result = jvm.run(res, timeout=run_timeout*2)  # allow more time for mutant
        except TimeoutExpired as e:
            print(f'- Timeout: run mutant timed out under JVM, from {ref_file}: {e}')
            mut_result = CommandResult(TIMEOUT_SPEC_CODE, str(e))

        if ref_result.retcode == TIMEOUT_SPEC_CODE == mut_result.retcode:
            print(f'- Timeout: both reference and mutant timed out: {mutant_file}')
            # Both ref_file and mutant_file timed out
            test_result.mut_results.append(MutantAllTmoError(mutant_file, mutation_msg))
        else:
            # Successfully run a mutant
            test_result.mut_results.append(MutantRunResult(mutant_file, mutation_msg, mut_result))

        succeeded_mutation += 1
        if succeeded_mutation >= num_mutation:
            break

    return test_result


class WriterStat:
    @abstractmethod
    def ref_count(self): pass

    @abstractmethod
    def inc_ref_count(self): pass

    @abstractmethod
    def mut_count(self): pass

    @abstractmethod
    def inc_mut_count(self): pass

    @abstractmethod
    def diff_count(self): pass

    @abstractmethod
    def inc_diff_count(self): pass

    @abstractmethod
    def mutation_failure_count(self): pass

    @abstractmethod
    def inc_mutation_failure_count(self): pass

    @abstractmethod
    def compilation_failure_count(self): pass

    @abstractmethod
    def inc_compilation_failure_count(self): pass

    @abstractmethod
    def mutant_timeout_count(self): pass

    @abstractmethod
    def inc_mutant_timeout_count(self): pass

    @abstractmethod
    def timeout_count(self): pass

    @abstractmethod
    def inc_timeout_count(self): pass


class ProcSharedWriterStat(WriterStat):

    def __init__(self, manager: Manager):
        self._ref_count = manager.Value('i', 0)
        self._mut_count = manager.Value('i', 0)
        self._diff_count = manager.Value('i', 0)
        self._mutf_count = manager.Value('i', 0)
        self._compf_count = manager.Value('i', 0)
        self._mtmo_count = manager.Value('i', 0)
        self._tmo_count = manager.Value('i', 0)

    def ref_count(self):
        return self._ref_count.value

    def inc_ref_count(self):
        self._ref_count.value += 1

    def mut_count(self):
        return self._mut_count.value

    def inc_mut_count(self):
        self._mut_count.value += 1

    def diff_count(self):
        return self._diff_count.value

    def inc_diff_count(self):
        self._diff_count.value += 1

    def mutation_failure_count(self):
        return self._mutf_count.value

    def inc_mutation_failure_count(self):
        self._mutf_count.value += 1

    def compilation_failure_count(self):
        return self._compf_count.value

    def inc_compilation_failure_count(self):
        self._compf_count.value += 1

    def mutant_timeout_count(self):
        return self._mtmo_count.value

    def inc_mutant_timeout_count(self):
        self._mtmo_count.value += 1

    def timeout_count(self):
        return self._tmo_count.value

    def inc_timeout_count(self):
        self._tmo_count.value += 1


class TestResultWriter:
    """
    Ensure to use TestResultWrite in a single process, such that we don't
    need to care about the process safety of out_dir
    """
    def __init__(self, out_dir: Path, stat: WriterStat):
        self.out_dir = out_dir
        self.stat = stat

        self._save_tmo = False

        self.mutant_dir_name = 'mutant'

        self.mutf_dir = out_dir / 'mutation-failures'
        self.mut_fname = 'mutation.txt'
        self.mutf_fname = 'mutation.err.txt'
        res = Command.mkdir(self.mutf_dir, can_exist=True)
        script_check(res.retcode == 0, f'Cannot mkdir for {self.mutf_dir}: {res.output}')

        self.compf_dir = out_dir / 'compilation-failures'
        self.compf_fname = 'compilation.err.txt'
        res = Command.mkdir(self.compf_dir, can_exist=True)
        script_check(res.retcode == 0, f'Cannot mkdir for {self.compf_dir}: {res.output}')

        self.mtmo_dir = out_dir / 'mutant-timeouts'
        res = Command.mkdir(self.mtmo_dir, can_exist=True)
        script_check(res.retcode == 0, f'Cannot mkdir for {self.mtmo_dir}: {res.output}')

        self.tmo_dir = out_dir / 'all-timeouts'
        res = Command.mkdir(self.tmo_dir, can_exist=True)
        script_check(res.retcode == 0, f'Cannot mkdir for {self.tmo_dir}: {res.output}')

        self.diff_dir = out_dir / 'differences'
        self._diff_file = self.diff_dir / 'diffs.csv'
        res = Command.mkdir(self.diff_dir, can_exist=True)
        script_check(res.retcode == 0, f'Cannot mkdir for {self.diff_dir}: {res.output}')
        script_check(not self._diff_file.exists() or self._diff_file.is_file(), f'{self._diff_file} is not a file')
        with self._diff_file.open('w', encoding='utf-8') as f:
            # Write header
            f.write("diff_id,ref_id,mut_id,diff_type,"
                    "reference_return_code,reference_output_length,"
                    "mutant_return_code,mutant_output_length\n")

    def set_save_timeouts(self, save: bool):
        self._save_tmo = save

    def append(self, test_res: Optional[TestResult]):
        # Check whether there are exceptions
        if test_res is None: return

        ref_file = test_res.ref_file
        script_check(ref_file is not None, "No reference file is given")

        ref_id = self.stat.ref_count()
        self.stat.inc_ref_count()

        if isinstance(test_res, NormalTestResult):
            ref_result = test_res.ref_result
            mut_results = test_res.mut_results
            script_check(ref_result is not None, "No reference result is given")
            script_check(mut_results is not None, "No mutant results are given")
            self._process_test_results(ref_id, ref_file, ref_result, mut_results)

        # Reference is timeout, directly skip
        elif isinstance(test_res, RefTmoTestResult): pass

        # Cannot have other type of results
        else: script_check(False, "Cannot reach here")

        # Totally remove ref_dir to save space
        ref_dir = ref_file.parent
        res = Command.remove(ref_dir, is_dir=True, force=True)
        script_check(res.retcode == 0, f"Failed to remove {ref_dir.absolute()}: {res.output}")

    def _process_test_results(self, ref_id: int, ref_file: Path,
                              ref_result: CommandResult,
                              mut_results: List[MutantResult]):
        for mut_result in mut_results:
            mut_id = self.stat.mut_count()
            self.stat.inc_mut_count()

            if isinstance(mut_result, MutationError):
                self._process_mutation_err(mut_id, ref_file, mut_result)

            elif isinstance(mut_result, MutantCompError):
                self._process_compilation_err(mut_id, ref_file, mut_result)

            elif isinstance(mut_result, MutantAllTmoError):
                self._process_timeout_err(mut_id, ref_file, mut_result)

            elif isinstance(mut_result, MutantRunResult):
                self._process_diff_result(mut_id, ref_id, ref_file, ref_result, mut_result)

            else:
                script_check(False, "Cannot reach here")

    def _remake_ref_dir_and_return_mutant_dir(self, ref_file, target_dir, mutant):
        res = Command.mkdir(target_dir, can_exist=True)
        script_check(res.retcode == 0, f'Fail to mkdir for {target_dir}: {res.output}')

        ref_dir = ref_file.parent
        # Copy all files except mutants and any dir from ref_dir to target_dir
        for f in ref_dir.iterdir():
            if f.name == MUTANTS_DIR_NAME: continue
            if f.is_dir(): continue
            res = Command.copy(f, target_dir)
            script_check(res.retcode == 0, f'Cannot copy {f} to {ref_dir}: {res.output}')

        mutant_dir = target_dir / self.mutant_dir_name

        if mutant is not None:
            # Move directory of mutant to mutant_dir
            res = Command.move(mutant, mutant_dir)
            script_check(res.retcode == 0, f'Fail to move {mutant} to {mutant_dir}: {res.output}')
        else:
            # Create a new mutant dir
            res = Command.mkdir(mutant_dir, can_exist=True)
            script_check(res.retcode == 0, f'Fail to mkdir for {mutant_dir}: {res.output}')

        return mutant_dir

    def _process_mutation_err(self, mut_id: int, ref_file: Path, mut_err: MutationError):
        script_check(mut_err.err_msg is not None, "No mutation error message is provided")
        script_check(mut_err.mutant_dir is not None, "No mutation error message is provided")

        mutf_id = self.stat.mutation_failure_count()
        self.stat.inc_mutation_failure_count()

        mutf_dir = self.mutf_dir / f'{mutf_id}'
        print(f'> Ooops: {mut_id}/{mutf_id}, '
              f'mutation error, save to {mutf_dir}, from {mut_err.mutant_dir}')

        # Move directory of reference to mutf_dir
        mutant_dir = self._remake_ref_dir_and_return_mutant_dir(ref_file, mutf_dir, mutant=None)
        # Save mutation error message data to separate file
        (mutant_dir / self.mutf_fname).write_text(mut_err.err_msg)

    def _process_compilation_err(self, mut_id: int, ref_file: Path, comp_err: MutantCompError):
        script_check(comp_err.mutant_file is not None, "No mutation file is provided")
        script_check(comp_err.mutation_msg is not None, "No mutation message is provided")
        script_check(comp_err.err_msg is not None, "No compilation error message is provided")

        compf_id = self.stat.compilation_failure_count()
        self.stat.inc_compilation_failure_count()

        compf_dir = self.compf_dir / f'{compf_id}'
        print(f'> Ooops: {mut_id}/{compf_id}, '
              f'mutant compilation error, save to {compf_dir}, from {comp_err.mutant_dir}')

        # Move directory of reference and mutant to compf_dir
        mutant_dir = self._remake_ref_dir_and_return_mutant_dir(ref_file, compf_dir, mutant=comp_err.mutant_dir)
        # Save mutation message data to separate file
        (mutant_dir / self.mut_fname).write_text(comp_err.mutation_msg)
        # Save compilation error message data to separate file
        (mutant_dir / self.compf_fname).write_text(comp_err.err_msg)

    def _process_timeout_err(self, mut_id: int, ref_file: Path, tmo_err: MutantAllTmoError):
        script_check(tmo_err.mutant_file is not None, "No mutation file is provided")
        script_check(tmo_err.mutation_msg is not None, "No mutation message is provided")

        self.stat.inc_timeout_count()

        if not self._save_tmo:
            return  # Don't save timeout errors, directly return

        tmo_id = self.stat.timeout_count() - 1

        tmo_dir = self.tmo_dir / f'{tmo_id}'
        print(f'> Ooops: {mut_id}/{tmo_id}, '
              f'both reference and mutant timed-out, save to {tmo_dir}, from {tmo_err.mutant_dir}')

        # Move directory of reference to tmo_dir
        mutant_dir = self._remake_ref_dir_and_return_mutant_dir(ref_file, tmo_dir, mutant=tmo_err.mutant_dir)
        # Save mutation error message data to separate file
        (mutant_dir / self.mut_fname).write_text(tmo_err.mutation_msg)

    def _process_diff_result(self,
                             mut_id: int,
                             ref_id: int,
                             ref_file: Path,
                             ref_result: CommandResult,
                             test_result: MutantRunResult):
        script_check(test_result.mutant_file is not None, f'No mutant file is given: {ref_file}')
        script_check(test_result.result is not None, f'No mutant result is given: {ref_file}')

        mut_result = test_result.result

        not_found = 'not-found'
        retcode = 'return-code'
        output = 'prog-output'

        diff_type = not_found
        if ref_result.retcode != mut_result.retcode:
            diff_type = retcode
        elif ref_result.output != mut_result.output:
            diff_type = output

        if diff_type == not_found:
            print(f'> Great: {mut_id}, discard {test_result.mutant_dir}')
            return

        elif diff_type == retcode and mut_result.retcode == TIMEOUT_SPEC_CODE:
            # Mutant timed-out
            return self._process_mutanttmo_err(mut_id, ref_file, test_result)

        diff_id = self.stat.diff_count()
        self.stat.inc_diff_count()

        # Record this difference in diff_file
        print(f'> AHAHA: {mut_id}/{diff_id}: '
              f'FOUND A DIFFERENCE {diff_type}, from {test_result.mutant_dir}')
        with self._diff_file.open('a', encoding='utf-8') as out_file:
            out_file.write(f"{diff_id},{ref_id},{mut_id},{diff_type},"
                           f"{ref_result.retcode},{len(ref_result.output)},"    # ref_result
                           f"{mut_result.retcode},{len(mut_result.output)}\n")  # mut_result

        # Move directory of reference and mutant to diff_dir
        diff_dir = self.diff_dir / f'{diff_id}'
        mutant_dir = self._remake_ref_dir_and_return_mutant_dir(ref_file, diff_dir, mutant=test_result.mutant_dir)
        # Save mutation message data to separate file
        (mutant_dir / self.mut_fname).write_text(test_result.mutation_msg)
        # Save output data to separate file
        with (diff_dir / 'reference.txt').open('w') as f:
            f.write(f'Return code: {ref_result.retcode}\n')
            f.write(ref_result.output)
        with (diff_dir / 'mutant.txt').open('w') as f:
            f.write(f'Return code: {mut_result.retcode}\n')
            f.write(mut_result.output)

    def _process_mutanttmo_err(self, mut_id: int, ref_file: Path, test_res: MutantRunResult):
        script_check(test_res.mutant_file is not None, "No mutation file is provided")
        script_check(test_res.mutation_msg is not None, "No mutation message is provided")
        script_check(test_res.result.retcode == TIMEOUT_SPEC_CODE, "Mutant does not timeout")

        self.stat.inc_mutant_timeout_count()
        if not self._save_tmo:
            return  # Don't save timeout errors, directly return

        mtmo_id = self.stat.mutant_timeout_count() - 1

        mtmo_dir = self.mtmo_dir / f'{mtmo_id}'
        print(f'> Ooops: {mut_id}/{mtmo_id}, '
              f'only mutant timed-out, save to {mtmo_dir}, from {test_res.mutant_dir}')

        # Move directory of reference and mutant to mtmo_dir
        mutant_dir = self._remake_ref_dir_and_return_mutant_dir(ref_file, mtmo_dir, test_res.mutant_dir)
        # Save mutation error message data to separate file
        (mutant_dir / self.mut_fname).write_text(test_res.mutation_msg)


class JvmNoLongerAliveError(Exception):

    def __init__(self, jvm):
        self.jvm = jvm


class AxExecutor(MprExecutor[Path, TestResult]):

    def __init__(self, jvm: JavaVM, ax: Artemis, num_mutation: int, run_timeout: int):
        self.jvm = jvm
        self.ax = ax
        self.num_mutation = num_mutation
        self.run_timeout = run_timeout

    def __call__(self, ref_id: int, ref_file: Path) -> Optional[TestResult]:
        return run_test(ref_file, self.jvm, self.ax, self.num_mutation, self.run_timeout)

    def should_early_exit(self, ref_id: int, ref_file: Path) -> bool:
        return not self.jvm.is_alive()


class AxHandler(MprHandler[TestResult]):

    def __init__(self, writer: TestResultWriter):
        self.writer = writer

    def __call__(self, test_res: Optional[TestResult]):
        self.writer.append(test_res)


def main(conf_path: Path):
    script_check(conf_path.exists(), f'Conf does not exist: {conf_path}')

    conf = read_conf(conf_path)

    # Initialize seed right after reading configs
    random.seed(conf['rand_seed'])

    # Create required components from configs
    java = create_java_from_conf(conf['java'])
    jvm = create_jvm_from_conf(conf['jvm'], java)
    java_gen = create_generator_from_conf(conf['generator'])
    artemis = create_artemis_from_conf(conf['artemis'], java)

    # Create a manager for managing process-shared objects
    manager = Manager()

    # Create a writer for writing result in a single process
    stat = ProcSharedWriterStat(manager)
    writer = TestResultWriter(conf['out_dir'], stat)
    writer.set_save_timeouts(conf['save_timeouts'])

    num_proc = conf['num_proc']
    num_mutation = conf['num_mutation']
    prog_timeout = conf['prog_timeout']

    mprunner = MultiProcRunner(num_proc, java_gen,
                               AxExecutor(jvm, artemis, num_mutation, prog_timeout),
                               AxHandler(writer))

    _, elapsed = exec_time(mprunner.run, manager)

    # TODO Figure out how to save the data if stopped abnormally
    if mprunner.is_stopped_normally():
        print(f'Found {stat.diff_count()}/{stat.mut_count()} differences in {format_time(elapsed)}')
        print(f'- {stat.ref_count()} generated references')
        print(f'- {stat.mut_count()} generated mutants')
        print(f'- {stat.mutation_failure_count()} mutation failures')
        print(f'- {stat.compilation_failure_count()} mutant compilation failures')
        print(f'- {stat.mutant_timeout_count()} mutant timeouts ({prog_timeout}s)')
        print(f'- {stat.timeout_count()} all timeouts ({prog_timeout}s)')
    else:
        print('Exited abnormally')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print(f'usage: {sys.argv[0]} <conf_yaml>')
        exit(1)
    main(Path(sys.argv[1]))
