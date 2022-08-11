import itertools
import random
import sys
from pathlib import Path
from subprocess import TimeoutExpired
from yaml import safe_load as yaml_load

from artemi import \
    Command, \
    script_check, \
    parse_conf, \
    check_conf_type, \
    check_conf_dir, \
    parse_java_conf, \
    parse_generator_conf, \
    parse_artemis_conf, \
    create_java_from_conf, \
    create_artemis_from_conf, \
    create_generator_from_conf


def save_failure(ref_dir, mu_dir, failure_dir):
    res = Command.mkdir(failure_dir, can_exist=True)
    script_check(res.retcode == 0, f'Cannot mkdir for {failure_dir.absolute()}: {res.output}')

    for f in ref_dir.iterdir():
        if f.suffix != '.java': continue
        res = Command.copy(f, failure_dir)
        script_check(res.retcode == 0, f'Cannot copy {f} to {failure_dir}: {res.output}')

    res = Command.copy(mu_dir, failure_dir, is_dir=True)
    script_check(res.retcode == 0, f'Cannot copy {mu_dir} to {failure_dir}: {res.output}')


def read_conf(conf_path: Path):
    conf_obj = parse_conf('', yaml_load(conf_path.open(encoding='utf-8')))

    check_conf_type('.prog_timeout', conf_obj['prog_timeout'], int)
    check_conf_type('.rand_seed', conf_obj['rand_seed'], int)
    check_conf_type('.num_mutation', conf_obj['num_mutation'], int)
    check_conf_type('.stop_limit', conf_obj['stop_limit'], int)

    check_conf_type('.out_dir', conf_obj['out_dir'], str)
    conf_obj['out_dir'] = check_conf_dir('.out_dir', conf_obj['out_dir'])

    check_conf_type('.java', conf_obj['java'], dict)
    conf_obj['java'] = parse_java_conf('.java', conf_obj['java'])

    check_conf_type('.generator', conf_obj['generator'], dict)
    conf_obj['generator'] = parse_generator_conf('.generator', conf_obj['generator'])

    check_conf_type('.artemis', conf_obj['artemis'], dict)
    conf_obj['artemis'] = parse_artemis_conf('.artemis', conf_obj['artemis'])

    return conf_obj


UNIQ_COMP_SCRIPT = Path('uniq_compi_err.py')
UNIQ_MUT_SCRIPT = Path('uniq_mut_err.py')
ARTEMIS_WORKDIR = 'mutants'
ARTEMIS_OUT_FNAME = 'mutation.txt'
ARTEMIS_ERR_OUT_FNAME = 'mutation_err.txt'
ARTEMIS_MUTANT_COMP_ERR_OUT_FMAME = 'compilation_err.txt'


def main(conf_path: Path):
    conf = read_conf(conf_path)

    random.seed(conf['rand_seed'])
    java = create_java_from_conf(conf['java'])
    java_gen = create_generator_from_conf(conf['generator'])
    ax = create_artemis_from_conf(conf['artemis'], java)

    out_dir = conf['out_dir']
    out_dir.mkdir(exist_ok=True)

    stop_limit = conf['stop_limit']
    prog_timeout = conf['prog_timeout']
    num_mutation = conf['num_mutation']

    diff_file = out_dir / 'diffs.txt'
    failures_dir = out_dir / 'failures'
    failures_dir.mkdir(exist_ok=True)
    with diff_file.open('w') as _: pass  # clear existing data

    diff_cnt = 0
    failure_cnt = 0

    for index, ref_file in zip(itertools.count(start=1, step=1), java_gen):
        if index == stop_limit: break

        ref_dir = ref_file.parent
        print(f"\n[{index}] Test artemis using {ref_file.absolute()}")

        res = java.compile(ref_file)
        script_check(res.clazz is not None, f"Failed to compile ref Java: {ref_file}: {res.err_msg}")
        try:
            print(f"Running reference in JVM...")
            ref_res = java.run(res, timeout=prog_timeout)
        except TimeoutExpired:
            print("Timeout: run reference in JVM")
            print("Continue to next")
            continue

        ax_dir: Path = ref_dir / ARTEMIS_WORKDIR
        res = Command.mkdir(ax_dir, can_exist=True)
        script_check(res.retcode == 0, f'Cannot mkdir for {ax_dir.absolute()}: {res.output}')

        for i in range(num_mutation):
            seed = random.randint(0, 0xFFFFFFFF)
            print(f"-----------------------")
            print(f"{i} New test with seed: {seed}")

            mu_dir = ax_dir / str(i)
            res = Command.mkdir(mu_dir, can_exist=True)
            script_check(res.retcode == 0, f'Cannot mkdir for {mu_dir.absolute()}: {res.output}')

            res = ax.mutate(ref_file, out_dir=mu_dir, seed=seed)
            if res.mutant is None:
                ax_out_name = ARTEMIS_ERR_OUT_FNAME
                print(f'Failed to generate mutant, save error message to {ax_out_name}')
            else:
                ax_out_name = ARTEMIS_OUT_FNAME
                print(f'Generated mutant, save output to {ax_out_name}')
            with (mu_dir / ax_out_name).open('w') as f:
                f.write(res.output)
            if ax_out_name == ARTEMIS_ERR_OUT_FNAME:
                failure_dir = failures_dir / str(failure_cnt)
                print(f"Copy this failure to {failure_dir}")
                save_failure(ref_dir, mu_dir, failure_dir)
                failure_cnt += 1
                print(f'Continue to next')
                continue

            mu_file = res.mutant

            # Copy every other java thing to mu_dir
            for f in ref_dir.iterdir():
                if f.suffix != '.java': continue
                if f.name == mu_file.name: continue
                res = Command.copy(f, mu_dir)
                script_check(res.retcode == 0, f'Cannot copy {f} to {mu_dir}: {res.output}')

            res = java.compile(mu_file)
            if res.clazz is None:
                print(f"Failed to compile mutant, save error message to {ARTEMIS_MUTANT_COMP_ERR_OUT_FMAME}")
                with (mu_dir / ARTEMIS_MUTANT_COMP_ERR_OUT_FMAME).open('w') as f:
                    f.write(res.err_msg)
                failure_dir = failures_dir / str(failure_cnt)
                print(f"Copy this failure to {failure_dir}")
                save_failure(ref_dir, mu_dir, failure_dir)
                failure_cnt += 1
                print(f'Continue to next')
                continue

            try:
                print(f"Running mutant in JVM...")
                mu_res = java.run(res, timeout=prog_timeout*2)
            except TimeoutExpired:
                print("Timeout: run mutant in JVM")
                print(f'Continue to next')
                continue

            if ref_res.retcode != mu_res.retcode or ref_res.output != mu_res.output:
                print(f"AHA: Additional findings, found difference: {diff_cnt}")
                diff_cnt += 1
                with diff_file.open('a') as f:
                    f.write("-------------\n")
                    f.write(f"reference: {ref_file.absolute()}\n")
                    f.write(f"mutant: {mu_file.absolute()}\n")
            else:
                print("AHA: Pass")

    res = Command.copy(UNIQ_COMP_SCRIPT, out_dir)
    script_check(res.retcode == 0, f'Cannot copy {UNIQ_COMP_SCRIPT} to {out_dir}: {res.output}')
    res = Command.copy(UNIQ_MUT_SCRIPT, out_dir)
    script_check(res.retcode == 0, f'Cannot copy {UNIQ_MUT_SCRIPT} to {out_dir}: {res.output}')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print(f'usage: {sys.argv[0]} <conf_yaml>')
        exit(1)
    main(Path(sys.argv[1]))
