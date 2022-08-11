from pathlib import Path


FAILURE_FNAME = 'mutation_err.txt'
FAILURES_DIR = Path('./failures')

failures = []

for failure_dir in FAILURES_DIR.iterdir():
    if not failure_dir.is_dir(): continue
    for mutant_dir in failure_dir.iterdir():
        if not mutant_dir.is_dir(): continue
        failure_file = mutant_dir / FAILURE_FNAME
        if not failure_file.is_file(): continue
        failures.append(failure_file)


def read_exception(file):
    content = file.read_text().split('\n')
    for ind, line in enumerate(content):
        if line.startswith('Exception in thread "main"'):
            return '\n'.join(content[ind:])
    return '\n'.join(content)


uniq_failures = []
uniq_exceptions = set()

for failure in failures:
    excp = read_exception(failure)
    if excp in uniq_exceptions: continue
    uniq_failures.append(failure)
    uniq_exceptions.add(excp)

for failure in uniq_failures:
    excp = read_exception(failure)
    print(failure.absolute())
    print(excp)
    print('------------------------------------------------')
print(len(uniq_failures))
