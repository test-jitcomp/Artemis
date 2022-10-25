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

from pathlib import Path


FAILURE_FNAME = 'compilation_err.txt'
FAILURES_DIR = Path('./failures')

failures = []

for failure_dir in FAILURES_DIR.iterdir():
    if not failure_dir.is_dir(): continue
    for mutant_dir in failure_dir.iterdir():
        if not mutant_dir.is_dir(): continue
        failure_file = mutant_dir / FAILURE_FNAME
        if not failure_file.is_file(): continue
        failures.append(failure_file)


def read_error(file):
    content = file.read_text().split('\n')
    content = content[:-1]  # skip N errors
    errors, errbuf = [], None
    for line in content:
        line += '\n'
        if line.startswith('/') and 'error: ' in line:
            if errbuf is not None:
                errors.append(errbuf.strip())
            errbuf = line[line.index('error:'):]
        else:
            if errbuf is not None:
                errbuf += line
    if errbuf is not None:
        errors.append(errbuf.strip())
    if len(errors) != 0:
        return '\n'.join(errors)
    else:
        return '\n'.join(content)


uniq_failures = []
uniq_errors = set()

for failure in failures:
    err = read_error(failure)
    if err in uniq_errors: continue
    uniq_failures.append(failure)
    uniq_errors.add(err)

for failure in uniq_failures:
    err = read_error(failure)
    print(failure.absolute())
    print(err)
    print('------------------------------------------------')
print(len(uniq_failures))
