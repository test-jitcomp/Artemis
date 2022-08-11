import os
import shlex
import signal
import sys
import time
from pathlib import Path
from subprocess import Popen, \
    CompletedProcess, \
    TimeoutExpired, \
    PIPE, \
    STDOUT, \
    CalledProcessError


#
# Common os/sys utilities
#

def sys_arch_is_64():
    return sys.maxsize > 2**32


def sys_os_type():
    return sys.platform


def safe_killpg(pid, sig):
    try: os.killpg(pid, sig)
    except ProcessLookupError:
        pass  # Ignore if there is no such process


# Fix: subprocess.run(cmd) series methods, when timed out, only sends a SIGTERM
# signal to cmd while does not kill cmd's subprocess. We let each command to run
# in a new process group by adding start_new_session flag, and kill the whole
# process group such that all cmd's subprocess are also killed when timed out.
def run_proc(cmd, stdout, stderr, timeout):
    with Popen(cmd, stdout=stdout, stderr=stderr, start_new_session=True) as proc:
        try:
            output, err_msg = proc.communicate(timeout=timeout)
        except:  # Including TimeoutExpired, KeyboardInterrupt, communicate handled that.
            safe_killpg(os.getpgid(proc.pid), signal.SIGKILL)
            # We don't call proc.wait() as .__exit__ does that for us.
            raise
        retcode = proc.poll()
    return CompletedProcess(proc.args, retcode, output, err_msg)


# We add this helper function because signal.strsignal() is added from py3.8
def signal_name(sig):
    return {
        # See `man signal`
        signal.SIGKILL: 'Killed',
        signal.SIGTERM: 'Terminated',
        signal.SIGINT: 'Interrupted',
        signal.SIGQUIT: 'Quits'
    }[sig]


def exec_time(fn, *args, **kwargs):
    begin = time.time()
    result = fn(*args, **kwargs)
    end = time.time()
    return result, (end - begin)


def format_time(delta: int) -> str:
    hour = delta // 3600
    minutes = (delta % 3600) // 60
    seconds = (delta % 3600) % 60
    if hour != 0:
        return f'{hour}h,{minutes}m,{seconds}s ({delta}s)'
    elif minutes != 0:
        return f'{minutes}m,{seconds}s ({delta}s)'
    else:
        return f'{seconds}s ({delta}s)'


#
# Common script utilities
#

class CheckError(Exception): pass


def script_check(pred: bool, msg: str):
    if not pred:
        raise CheckError(msg)


class CommandResult:

    def __init__(self, retcode, output):
        # retcode: return code of the command
        # output: stdout on success of the command, or stderr on failure
        self.retcode = retcode
        self.output = output


class Command:

    @staticmethod
    def run(cmd: str, timeout: int = 5):
        try:
            proc = run_proc(shlex.split(cmd),
                            stdout=PIPE,
                            stderr=STDOUT,
                            timeout=timeout)
            output = proc.stdout
            retcode = proc.returncode
        except CalledProcessError as x:
            output = x.output
            retcode = x.returncode
        if output is not None:
            output = str(output, encoding='utf-8').strip()
        return CommandResult(retcode, output)

    @staticmethod
    def redirected_run(cmd: str, stdout, stderr, timeout: int = 5):
        try:
            proc = run_proc(shlex.split(cmd),
                            stdout=stdout,
                            stderr=stderr,
                            timeout=timeout)
            retcode = proc.returncode
        except CalledProcessError as x:
            retcode = x.returncode
        return CommandResult(retcode, None)

    @staticmethod
    def checked_cmd(cmd: str, *args, **kwargs):
        fn = getattr(Command, cmd)
        res = fn(*args, kwargs)
        script_check(res.retcode == 0, f"Failed to run command Command.{cmd}: {res.output}")

    @classmethod
    def copy(cls, source: Path, target: Path, is_dir: bool = False):
        if is_dir:
            return cls.run(f'cp -r {source.absolute()} {target.absolute()}')
        else:
            return cls.run(f'cp {source.absolute()} {target.absolute()}')

    @classmethod
    def mkdir(cls, target: Path, can_exist: bool = False):
        if can_exist:
            return cls.run(f'mkdir -p {target.absolute()}')
        else:
            return cls.run(f'mkdir {target.absolute()}')

    @classmethod
    def move(cls, source: Path, target: Path):
        return cls.run(f'mv {source.absolute()} {target.absolute()}')

    @classmethod
    def remove(cls, path: Path, is_dir: bool, force: bool):
        args = ''
        if is_dir:
            args += '-r '
        if force:
            args += '-f '
        return cls.run(f'rm {args} {path.absolute()}')


#
# Conf type checking utilities
#

def check_conf_type(key_path: str, val, typ):
    if type(val) != typ:
        raise CheckError(f'{key_path} is not a {typ.__name__}: {val}')
    return val


def check_conf_env_var(key_path: str, var: str) -> str:
    val = os.getenv(var[1:])
    script_check(val is not None, f"{key_path}'s environment variable {var} is not set")
    return val


def check_conf_dir(key_path: str, val: str, should_exist: bool = True) -> Path:
    path = Path(val)
    if should_exist:
        script_check(path.exists(), f'{key_path} does not exist: {val}')
        script_check(path.is_dir(), f'{key_path} is not a directory: {val}')
    return path


def check_conf_file(key_path: str, val: str, should_exist: bool = True) -> Path:
    path = Path(val)
    if should_exist:
        script_check(path.exists(), f'{key_path} does not exist: {val}')
        script_check(path.is_file(), f'{key_path} is not a file: {val}')
    return path


def check_conf_file_or_dir(key_path: str, val: str) -> Path:
    path = Path(val)
    script_check(path.exists(), f'{key_path} does not exist: {val}')
    return path
