import itertools
import signal
import time
from abc import abstractmethod
from multiprocessing import Pool, Manager, Process, TimeoutError
from queue import Empty as QueueIsEmpty
from typing import TypeVar, Generic, Optional

from utils import script_check, signal_name


"""
MultiProcRunner works like MapReduce that it employs:
- 1 generator (embedded in the caller process) to generate items sequentially;
- N executors, each in a single process, to execute generated items and return results;
- 1 handler, in a single process, to handler all executed results.

They collaborated with each other like the following diagram:
------------------------------------------------------------------------------------

                G E N E R A T O R                  [1 generator, required, () => T]
     /        /       |         \         \\
    /        /        |          \         \\
executor executor executor ... executor executor   [N executors, required, T => R]
    \        \        |          /         /
     \        \       |         /         /
                  H A N D L E R                    [1 handler,   optional, R => void]

------------------------------------------------------------------------------------
"""


_T = TypeVar('_T')
_R = TypeVar('_R')


class MprGenerator(Generic[_T]):

    @abstractmethod
    def __next__(self) -> _T:
        pass

    @abstractmethod
    def __iter__(self):
        pass


class MprExecutor(Generic[_T, _R]):

    @abstractmethod
    def __call__(self, i: int, t: _T) -> Optional[_R]:
        pass

    # noinspection PyMethodMayBeStatic,PyUnusedLocal
    def should_early_exit(self, i: int, t: _T) -> bool:
        return False


class MprExecutorEarlyExit(Exception): pass


class MprHandler(Generic[_R]):

    @abstractmethod
    def __call__(self, r: Optional[_R]):
        pass


class KilledByUserSignal(Exception):

    def __init__(self, sig):
        self.sig = sig


class MultiProcRunner(Generic[_T, _R]):
    def __init__(self,
                 num_proc: int,
                 generator: MprGenerator[_T],
                 executor: MprExecutor[_T, _R],
                 handler: Optional[MprHandler[_R]] = None,
                 queue_size=128):
        self.num_proc = num_proc
        self.queue_size = queue_size
        self.generator = generator
        self.executor = executor
        self.handler = handler
        self._norm_stop = False
        self._started = False
        self._stopped = False

    def is_stopped(self):
        return self._started and self._stopped

    def is_stopped_normally(self):
        script_check(self.is_stopped(), "The runner hasn't started or stopped")
        return self._norm_stop

    def run(self, manager):
        if self._started: return
        else: self._started = True

        # Create a writer for writing result in a single process
        if self.handler is not None:
            queue = manager.Queue(maxsize=self.queue_size)
            hproc = Process(target=self._handle_wrapper, args=(queue,))
        else:
            queue = None
            hproc = None

        # Workaround: Pool.*_async() functions never block even if all
        # process workers are handling their tasks. This is because Pool
        # maintains an infinite SimpleQueue instead of a finite Queue,
        # making the SimpleQueue to grow unlimitedly. So in here, we use
        # a semaphore to ensure only limited tasks are submitted.
        pool = Pool(self.num_proc)
        sema = manager.BoundedSemaphore(self.num_proc * 2)

        self._norm_stop = False
        try:
            if hproc is not None:
                hproc.start()

            # Register to catch SIGTERM signal. Note, SIGKILL cannot be caught.
            def abort_by_signals(sig, _):
                raise KilledByUserSignal(sig)
            signal.signal(signal.SIGTERM, abort_by_signals)

            for ind, item in zip(itertools.count(start=1, step=1), self.generator):
                print(f'< Submit: new item (index: {ind})')
                if self.executor.should_early_exit(ind, item):
                    raise MprExecutorEarlyExit()
                sema.acquire()  # Make sure only limited tasks are submitted, otherwise wait
                res = pool.apply_async(self._execute_wrapper,
                                       args=(ind, item, sema, queue))
                # Work around to let the async task to run. Removing
                # the following two lines will make no works to run
                try: res.get(timeout=0)
                except TimeoutError: pass

            print('* Stopped Normally')
            pool.close()

            self._norm_stop = True
        except KeyboardInterrupt:
            print('* Stopped by KeyboardInterrupt')
        except MprExecutorEarlyExit:
            print(f'* Stopped by ExecutorEarlyExit')
        except KilledByUserSignal as e:
            print(f'* Stopped by KilledByUserSignal({signal_name(e.sig)})')
        finally:
            # Exception happens, terminate the pool directly
            if not self._norm_stop:
                pool.terminate()

            # Wait until the pool to exit (either all works to finish
            # when close(), or immediately passed if terminate())
            print('* Joining the pool, this may take some time')
            pool.join()

            if self._norm_stop:
                print('* Give 15s to the handler to handler rest results', end='', flush=True)
                for i in range(15):
                    print('.', end='', flush=True)
                    time.sleep(1)
                print()

            print('* Terminating handler process, this may take some time')
            if hproc is not None:
                hproc.terminate()

        print('* Runner exited')
        self._stopped = True

    def _execute_wrapper(self, index: int, item: _T, sema, queue):
        try:
            print(f'+ Execute: starts to execute item (index: {index})')
            queue.put(self.executor(index, item))
            print(f'> Finished: item (index: {index}) is executed')
        except KeyboardInterrupt:
            pass
        except Exception as e:
            print(f'! Exception: item (index: {index}): {e}')
        finally:
            sema.release()

    def _handle_wrapper(self, queue):
        try:
            while True:
                while not queue.empty():
                    try: self.handler(queue.get(block=False, timeout=0.1))
                    except QueueIsEmpty: break  # add this because empty() is not reliable
                time.sleep(0.5)
        except KeyboardInterrupt: pass
