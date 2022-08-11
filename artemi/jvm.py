import tempfile
from abc import abstractmethod
from pathlib import Path
from subprocess import TimeoutExpired
from typing import Optional, List
from uuid import uuid4 as uuid

from utils import Command, CommandResult, script_check


#
# JavaVM
#

class JvmCompileResult:

    def __init__(self, class_dir, clazz, err_msg):
        # class_dir: dir of the compiled .class file
        # clazz: class name of the compiled .java file, None means compilation failure
        # err_msg: if clazz is None, this is the error message
        self.class_dir = class_dir
        self.clazz = clazz
        self.err_msg = err_msg


class JavaVM:

    FORCED_NONE = -1
    FORCED_INT = 0
    FORCED_JIT = 1

    def __init__(self):
        self.default_opts = []

    def set_default_opts(self, opts: List[str]):
        self.default_opts = opts

    @abstractmethod
    def compile(self,
                java_file: Path,
                classpath: List[str] = None,
                timeout: int = 10) -> JvmCompileResult:
        pass

    @abstractmethod
    def run(self,
            compile_result: JvmCompileResult,
            main_args: str = '',
            do_force: int = FORCED_NONE,
            jvm_opts: str = '',
            timeout: int = 10) -> CommandResult:
        pass

    @abstractmethod
    def is_alive(self) -> bool:
        pass

    @abstractmethod
    def __str__(self):
        pass


#
# HotSpot toolchain
#


HotSpotCompileResult = JvmCompileResult


class HotSpot(JavaVM):

    def __init__(self, java_home: Path):
        super(HotSpot, self).__init__()
        self.home = java_home
        self.javac = java_home / 'bin' / 'javac'
        self.java = java_home / 'bin' / 'java'
        script_check(self.javac.exists(), f'Command `javac` does not exist in JAVA_HOME: {java_home}')
        script_check(self.java.exists(), f'Command `java` does not exist in JAVA_HOME: {java_home}')
        self.classpath = []

    def set_default_classpath(self, classpath: List[str]):
        self.classpath = classpath

    def compile(self,
                java_file: Path,
                classpath: Optional[List[str]] = None,
                timeout: int = 10) -> HotSpotCompileResult:
        class_dir = java_file.parent.absolute()
        if classpath is not None:
            classpath.append(str(class_dir))
        else:
            classpath = [str(class_dir)]
        classpath = classpath + self.classpath
        cp_opt = ":".join(classpath)
        java_file_path = java_file.absolute()
        result = Command.run(f'{self.javac}'
                             f' -cp {cp_opt}'
                             f' {java_file_path}',
                             timeout=timeout)
        if result.retcode != 0:
            return HotSpotCompileResult(None, None, err_msg=result.output)
        java_class = java_file.stem
        return HotSpotCompileResult(class_dir, java_class, None)

    def run(self,
            compile_result: JvmCompileResult,
            main_args: str = '',
            do_force: int = JavaVM.FORCED_NONE,
            jvm_opts: str = '',
            timeout: int = 10) -> CommandResult:
        hs_opts = f'-cp {compile_result.class_dir}'
        if do_force == JavaVM.FORCED_INT:
            hs_opts += ' -Xint'
        elif do_force == JavaVM.FORCED_JIT:
            hs_opts += ' -Xcomp'
        elif do_force == JavaVM.FORCED_NONE:
            pass
        else:
            script_check(False, f"Unsupported force option: {do_force}")
        hs_opts += f' {jvm_opts}'
        hs_opts += f' {" ".join(self.default_opts)}'

        # noinspection PyUnreachableCode
        if False:
            # Post-process VM options to avoid clash
            if do_force == JavaVM.FORCED_INT:
                hs_opts = hs_opts                                 \
                    .replace('-XX:+UseCompiler', '')              \
                    .replace('-XX:+UseLoopCounter', '')           \
                    .replace('-XX:+AlwaysCompileLoopMethods', '') \
                    .replace('-XX:+UseOnStackReplacement', '')
            elif do_force == JavaVM.FORCED_JIT:
                hs_opts = hs_opts                                 \
                    .replace('-XX:+UseInterpreter', '')           \
                    .replace('-XX:+BackgroundCompilation', '')    \
                    .replace('-XX:+ClipInlining', '')

        return Command.run(f'{self.java}'
                           f' {hs_opts}'
                           f' {compile_result.clazz}'
                           f' {main_args}',
                           timeout=timeout)

    def jar_run(self,
                jar_path: Path,
                main_class: Optional[str],
                main_args: str = '',
                jvm_opts: str = '',
                timeout: int = 10) -> CommandResult:
        jvm_opts += f' {" ".join(self.default_opts)}'
        if main_class is None:
            return Command.run(f'{self.java}'
                               f' -jar {jar_path}'
                               f' {jvm_opts}'
                               f' {main_args}',
                               timeout=timeout)
        else:
            return Command.run(f'{self.java}'
                               f' -cp {jar_path}'
                               f' {jvm_opts}'
                               f' {main_class}'
                               f' {main_args}',
                               timeout=timeout)

    def is_alive(self) -> bool:
        return True

    def __str__(self):
        return f'hotspot:{self.home}'


#
# Java toolchain
#

Java = HotSpot


#
# Dalvik or Android Runtime toolchain
#

class ArtCompileResult(JvmCompileResult):

    def __init__(self, jar, clazz, err_msg):
        super(ArtCompileResult, self).__init__(None if jar is None else jar.parent, clazz, err_msg)
        # jar: compiled .dex file, packed into a .jar
        # clazz: class name of the compiled .java file, None means compilation failure
        # err_msg: if clazz is None, this is the error message
        self.jar = jar
        self.clazz = clazz
        self.err_msg = err_msg


class AbstractArt(JavaVM):

    def __init__(self, java: Java):
        super(AbstractArt, self).__init__()
        self.java = java
        self.min_api = 1

    @property
    @abstractmethod
    def d8(self):
        pass

    def set_min_api(self, api_level: int):
        self.min_api = api_level

    def compile(self,
                java_file: Path,
                classpath: List[str] = None,
                timeout: int = 10) -> ArtCompileResult:
        # Compile java_file using javac
        res = self.java.compile(java_file, classpath, timeout=timeout)
        if res.clazz is None:
            return ArtCompileResult(None, None, res.err_msg)
        clazz = res.clazz
        class_dir = res.class_dir
        # Compile everything (.class, .dex) inside class_dir using d8
        jar = class_dir / 'test.jar'
        classes = [str(file.absolute())
                   for file in class_dir.iterdir()
                   if file.suffix == '.class' or file.suffix == '.dex']
        res = Command.run(f'{self.d8}'
                          f' --output {jar.absolute()}'
                          f' --min-api {self.min_api}'
                          f' {" ".join(classes)}',
                          timeout=timeout)
        if res.retcode != 0:
            return ArtCompileResult(None, None, res.output)
        return ArtCompileResult(jar, clazz, None)

    @abstractmethod
    def run(self,
            compile_result: JvmCompileResult,
            main_args: str = '',
            do_force: int = JavaVM.FORCED_NONE,
            jvm_opts: str = '',
            timeout: int = 10) -> CommandResult:
        pass


class TargetArt(AbstractArt):

    _WORK_DIR = '/sdcard/ax.art'

    def __init__(self, android_home: Path, build_tools: str, java: Java):
        super(TargetArt, self).__init__(java)
        self.home = android_home
        self.build_tools = build_tools
        self._d8 = android_home / 'build-tools' / build_tools / 'd8'
        script_check(self._d8.exists(), f'Command `d8` does not exist in build tools: {build_tools}')
        self.adb = android_home / 'platform-tools' / 'adb'
        script_check(self.adb.exists(), f'Command `adb` does not exist in ANDROID_HOME: {android_home}')
        self.app_process = False
        self.serial = None

    @property
    def d8(self):
        return self._d8

    def connect(self, serial: str):
        self.serial = serial
        res = Command.run(f'{self.adb} -s {self.serial} shell '
                          f'mkdir -p {self._WORK_DIR}')
        script_check(res.retcode == 0, f'Failed to connect to android device: {res.output}')

    def enable_app_process(self, enable: bool):
        self.app_process = enable

    def run(self,
            compile_result: ArtCompileResult,
            main_args: str = '',
            do_force: int = AbstractArt.FORCED_NONE,
            jvm_opts: str = '',
            timeout: int = 10) -> CommandResult:
        # Push to the device as a unique name to avoid data race
        jar_name = f'{uuid()}.jar'
        on_device_jar_path = f'{self._WORK_DIR}/{jar_name}'
        trace_name = f'{uuid()}.txt'
        on_device_trace_path = f'{self._WORK_DIR}/{trace_name}'

        res = Command.run(f'{self.adb} -s {self.serial} push'
                          f' {compile_result.jar}'
                          f' {on_device_jar_path}')
        if res.retcode != 0:
            return res

        # Parse ART's vm options
        art_opts = f"-cp {on_device_jar_path}"
        if do_force == self.FORCED_JIT:
            art_opts += ' -Xjitthreshold:0'
        elif do_force == self.FORCED_INT:
            art_opts += ' -Xint'
        elif do_force == self.FORCED_NONE:
            pass
        else:
            script_check(False, f"Unsupported force option: {do_force}")
        art_opts += f' {" ".join(self.default_opts)}'

        # Determine which binary to use: dalvikvm or app_process
        if not self.app_process:
            art_cmd = f'dalvikvm {art_opts}'
        else:
            art_cmd = f'app_process {art_opts} {self._WORK_DIR}'

        try:
            # Run the pushed jar using art
            res = Command.run(f'{self.adb} -s {self.serial} shell {art_cmd}'
                              f' {compile_result.clazz} {main_args}',
                              timeout=timeout)
        finally:
            # Remove the pushed jar to reduce space
            tmp_res = Command.run(f'{self.adb} -s {self.serial} shell'
                                  f' rm -rf {on_device_jar_path} {on_device_trace_path}')
            script_check(tmp_res.retcode == 0,
                         f'Fail to remove {jar_name}/{trace_name} on device: {tmp_res.output}')

        return res

    def is_connected(self):
        return self.serial is not None

    def is_alive(self):
        if not self.is_connected():
            return False  # Art is not connected to any Android device
        try:
            res = Command.run(f'{self.adb} devices')
            if res.retcode != 0 or f'{self.serial}\tdevice' not in res.output:
                return False  # The device is unexpectedly killed
            res = Command.run(f'{self.adb} -s {self.serial} shell'
                              f' touch {self._WORK_DIR}/.ax.art.aliveness')
            if res.retcode != 0:
                return False  # The device does not work as normal, unexpected behavior
        except TimeoutExpired:
            return False  # Either adb-server and cannot restart or the devices does not respond
        return True

    def __str__(self):
        return f'art:target:{self.serial}' if self.is_connected() else f'art:target:unconnected'


class HostArt(AbstractArt):

    def __init__(self, host_home: Path, java: Java):
        super(HostArt, self).__init__(java)
        self.host_home = host_home
        self.art = host_home / 'host' / 'linux-x86' / 'bin' / 'art'
        script_check(self.art.exists(), f'Command `art` does not exist in host home: {host_home}')
        self._d8 = host_home / 'host' / 'linux-x86' / 'bin' / 'd8'
        script_check(self._d8.exists(), f'Command `d8` does not exist in host home: {host_home}')
        self._libart = host_home / 'host' / 'common' / 'obj' / 'JAVA_LIBRARIES' /\
            'core-libart-hostdex_intermediates' / 'classes.jar'
        self._openjdk = host_home / 'host' / 'common' / 'obj' / 'JAVA_LIBRARIES' /\
            'core-oj-hostdex_intermediates' / 'classes.jar'

    @property
    def d8(self):
        return self._d8

    def compile(self,
                java_file: Path,
                classpath: List[str] = None,
                timeout: int = 10) -> ArtCompileResult:
        if classpath is None:
            classpath = [str(self._libart), str(self._openjdk)]
        else:
            classpath.append(str(self._libart))
            classpath.append(str(self._openjdk))
        return super(HostArt, self).compile(java_file, classpath, timeout)

    def run(self,
            compile_result: ArtCompileResult,
            main_args: str = '',
            do_force: int = AbstractArt.FORCED_NONE,
            jvm_opts: str = '',
            timeout: int = 10) -> CommandResult:
        # Parse ART's vm options
        art_opts = f"-cp {compile_result.jar.absolute()}"
        if do_force == self.FORCED_JIT:
            art_opts += ' -Xjitthreshold:0'
        elif do_force == self.FORCED_INT:
            art_opts += ' -Xint'
        elif do_force == self.FORCED_NONE:
            pass
        else:
            script_check(False, f"Unsupported force option: {do_force}")
        art_opts += f' {jvm_opts}'
        art_opts += f' {" ".join(self.default_opts)}'
        # Run using the art command with dex2oat (AOT) compilation
        # Hack: host-art produce logs of "pre-compiled image not found" that we don't need
        # $ANDROID_LOG_TAGS controls the logging of ART. This by default is '*:w'. We reset
        # as '*:f' to log only fatal logs (otherwise '*:s' to supress all outputs like logcat).
        # Also, $ANDROID_DATA controls the profiling/logging android data directory, let's
        # use the parent directory instead of the script directory.
        data_dir = Path(tempfile.mkdtemp(prefix=str(compile_result.jar.parent / 'android-data-')))
        script_check(data_dir.exists(), f"Failed to create android-data directory: {data_dir}")
        return Command.run(f'env '
                           f' ANDROID_LOG_TAGS=*:f'
                           f' ANDROID_DATA={data_dir.absolute()}'
                           f' {self.art} --64 --no-compile'
                           f' --'
                           f' {art_opts}'
                           f' {compile_result.clazz} {main_args}',
                           timeout=timeout)

    def is_alive(self):
        return True

    def __str__(self):
        return f'art:host:{self.host_home}'


#
# OpenJ9 toolchain
#

class OpenJ9(HotSpot):

    def __init__(self, java_home: Path):
        super(OpenJ9, self).__init__(java_home)

    def run(self,
            compile_result: JvmCompileResult,
            main_args: str = '',
            do_force: int = JavaVM.FORCED_NONE,
            jvm_opts: str = '',
            timeout: int = 10) -> CommandResult:
        j9_opts = f'-cp {compile_result.class_dir}'
        if do_force == JavaVM.FORCED_INT:
            j9_opts += ' -Xint'
        elif do_force == JavaVM.FORCED_JIT:
            j9_opts += ' -Xjit:count=0'
        elif do_force == JavaVM.FORCED_NONE:
            pass
        else:
            script_check(False, f"Unsupported force option: {do_force}")
        j9_opts += f' {jvm_opts}'
        j9_opts += f' {" ".join(self.default_opts)}'

        return Command.run(f'{self.java}'
                           f' {j9_opts}'
                           f' {compile_result.clazz}'
                           f' {main_args}',
                           timeout=timeout)

    def is_alive(self) -> bool:
        return True

    def __str__(self):
        return f'openj9:{self.home}'


#
# Graal toolchain
#
class Graal(HotSpot):

    def run(self,
            compile_result: JvmCompileResult,
            main_args: str = '',
            do_force: int = JavaVM.FORCED_NONE,
            jvm_opts: str = '',
            timeout: int = 10) -> CommandResult:
        # Graal's forced JIT is a bit slow
        if do_force == JavaVM.FORCED_JIT:
            timeout = timeout * 2
        return super(Graal, self).run(
            compile_result,
            main_args,
            do_force,
            jvm_opts,
            timeout)

    def is_alive(self) -> bool:
        return True

    def __str__(self):
        return f'graal:{self.home}'
