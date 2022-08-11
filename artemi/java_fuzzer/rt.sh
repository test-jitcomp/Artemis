#!/bin/bash

#--------------------------------------------------------------------------
#
# Copyright (C) 2016 Intel Corporation
# Modifications copyright (C) 2017-2018 Azul Systems
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#--------------------------------------------------------------------------

#----------------------------------------------------------
# Java* Fuzzer test generator
#
# Modifications 2017-2018: Nina Rinskaya (Azul Systems), Ivan Popov (Azul Systems)
#----------------------------------------------------------

#--------------------------------------------------------------------------------
# Run the Fuzzer tool to generate a series of tests; compile each of them with javac,
# run the test by reference java and java under test and compare results.
# Parameter:
#    -r <path>    - directory to put results into
#    -p <prefix>  - prefix to add to the test dir names
#    -kd          - keep old dirs with failures alive if they exist - don't require removing them
#    -v <path>    - verify build with existing tests rather than generate new ones,
#                   if file "config.sh" found in this directory, it is sourcsd to redefine variables,
#                   such as source_subdir, timeout, limit_core_size
#    -f <path>    - save summary of the run to the given file in the directory with results
#    -sp          - save the tests that passed (they are removed by default)
#    -arg <arg>   - pass an arbitrary argument to VM
#    -conf <file> - Pass config file to Fuzzer
#    -o           - Pass -o option to Fuzzer and control the execution from outside
#    -g           - Generate only (don't not test)
#    <integer>    - number of tests to generate
#--------------------------------------------------------------------------------

CURR_DIR=$(pwd) # current dir
RUN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )" # Fuzzer scripts dir

source ${RUN_DIR}/common.sh

RUBY_CODE_DIR="$RUN_DIR/rb"

run_sh_file=run.sh
FILES_OF_INTEREST="*.java *.class rt_cmd rt_out* rt_err* core* tmp src ${run_sh_file} hs_err*"
FILES_OF_INTEREST_PASSED="$FILES_OF_INTEREST"

#IGNORE_DEBUG_OUTPUT_PATTERNS="^VM option "

conf_file=$RUN_DIR/config.yml
results_csv_file=test-results.csv

#--------------------------------------------------------------------------------
function Save_passed_res {
    test_dir_path=$res_path/$1/$prefix$2
    mkdir -p $test_dir_path
    chmod 775 "$res_path/$1" 2>&1 >> /dev/null
    chmod -R 775 "$test_dir_path" 2>&1 >> /dev/null
    cp -r $FILES_OF_INTEREST_PASSED $test_dir_path > /dev/null 2>&1
    [[ "$verify" != "y" ]] && [[ -n "$3" ]] && echo "    $3!"
}

#--------------------------------------------------------------------------------
function Save_res {
    test_dir_path=$res_path/$1/$prefix$2
    mkdir -p $test_dir_path
    chmod 775 "$res_path/$1" 2>&1 >> /dev/null
    chmod -R 775 "$test_dir_path" 2>&1 >> /dev/null
    cp -r $FILES_OF_INTEREST $test_dir_path > /dev/null 2>&1
    [[ "$verify" != "y" ]] && echo "    $3!"
}

#--------------------------------------------------------------------------------
# returns: 0 - test complete, 1 - no JIT, 2 - VerifyError, 3 - Java crash, 4 - should never been returned, 5 - OOM, 6 - Interpreter/Java timeout, 7 - reference failures: NPE during class initialization, java.lang.StackOverflowError, OutOfMemory, ExceptionInInitializerError, reference Java exit with non-zero exit code
function Run_test {
    gc_opts=""

    if [ "$verify" != "y" ]; then
        RunJava ${REF_TIME_OUT} ${JAVA_REFERENCE} ${JAVA_REFERENCE_OPTS} ${test_name} >>rt_out_ref 2>>rt_err_ref
        ref_res_code=$?
        if  grep 'Killed' rt_out_ref rt_err_ref | grep 'TIME_OUT' &>/dev/null ; then
            Save_res ref_hangs $1 "Reference Java Timeout"
            let ref_timeouts=ref_timeouts+1
           return 6
        elif grep 'java.lang.StackOverflowError' rt_out_ref rt_err_ref &> /dev/null; then # StackOverflowError - too complex dependencies
            Save_res ref_failures $1 "Reference StackOverflowError"
            let ref_failures=ref_failures+1
            return 7
        elif grep 'OutOfMemory' rt_out_ref rt_err_ref &> /dev/null; then
            Save_res ref_failures $1 "Reference OutOfMemory"
            let ref_failures=ref_failures+1
            return 7
        elif grep 'java.lang.ExceptionInInitializerError' rt_out_ref rt_err_ref &> /dev/null; then # NPE during class initialization - too complex dependencies
            Save_res ref_failures $1 "ExceptionInInitializerError"
            let ref_failures=ref_failures+1
            return 7
        elif grep "fatal error" rt_out_ref rt_err_ref > /dev/null ; then # Reference Java crash
            Save_res ref_crashes $1 "Reference Java crash"
            let ref_crashes=ref_crashes+1
            return 3
        elif [ "$ref_res_code" != "0" ]; then

            Save_res ref_failures $1 "Reference Java Failure"
            let ref_failures=ref_failures+1
            return 7
        fi
    fi

    echo "#!/bin/sh" >${run_sh_file}
    echo "export RESULTS_DIR=\${RESULTS_DIR:-\".\"}" >>${run_sh_file}
    echo "export TEST_DIR=\"\$(dirname \$(readlink -f \"\$0\"))\"" >>${run_sh_file}
    echo "export JAVA_UNDER_TEST=\"\${JAVA_UNDER_TEST:-\"${JAVA_UNDER_TEST}\"}\""  >>${run_sh_file}
    echo "export JAVA_UNDER_TEST_OPTS=\"\${JAVA_UNDER_TEST_OPTS:-\"${JAVA_UNDER_TEST_OPTS} ${add_opts}\"}\""  >>${run_sh_file}
    echo "\${JAVA_UNDER_TEST} \${JAVA_UNDER_TEST_OPTS} \${JAVA_ADD_OPTS} -cp \${TEST_DIR} ${test_name} >\${RESULTS_DIR}/rt_out_rerun 2>\${RESULTS_DIR}/rt_err_rerun" >>${run_sh_file}
    echo "diff -I \"${IGNORE_DEBUG_OUTPUT_PATTERNS}\" \${TEST_DIR}/rt_out_ref \${RESULTS_DIR}/rt_out_rerun"  >>${run_sh_file}
    echo "res_out=\$?" >>${run_sh_file}
    echo "diff -I \"${IGNORE_DEBUG_OUTPUT_PATTERNS}\" \${TEST_DIR}/rt_err_ref \${RESULTS_DIR}/rt_err_rerun" >>  ${run_sh_file}
    echo "res_err=\$?" >>${run_sh_file}
    echo "exit \$(( res_out + res_err))" >>${run_sh_file}
    chmod 777  ${run_sh_file}
    start_time=$(date +%s)
    RunJava ${TIME_OUT} ${JAVA_UNDER_TEST} ${JAVA_UNDER_TEST_OPTS} ${add_opts} ${test_name} >>rt_out 2>>rt_err
    res_code=$?

    end_time=$(date +%s)
    (( run_time = end_time - start_time ))
    [[ "$run_time" -gt 0 ]] || run_time=1
    (( all_time = all_time + run_time ))

    if  grep 'Killed' rt_out rt_err | grep 'TIME_OUT' &>/dev/null ; then
        Save_res hangs $1 Timeout
        let timeouts=timeouts+1
        return 124
    elif egrep "Internal Error|unexpected error has been detected by Java Runtime Environment" rt_out rt_err > /dev/null; then # VM/compiler crash
        Save_res crashes $1 Crash
        let crashes=crashes+1
    elif [[ "$MM" == "true" &&  "$res_code" == "0" ]] ; then
        [[ "$SAVE_PASSED" = "true" ]] && Save_passed_res passes $1
        let passes=passes+1
        return 0 # Passed
    elif diff -I "${IGNORE_DEBUG_OUTPUT_PATTERNS}" rt_out_ref rt_out >rt_out.diff 2>&1 && diff -I "${IGNORE_DEBUG_OUTPUT_PATTERNS}" rt_err_ref rt_err >rt_err.diff 2>&1; then
        [[ "$SAVE_PASSED" = "true" ]] && Save_passed_res passes $1
        let passes=passes+1
        return 0 # Passed
    else
        Save_res fails $1 Failed
        let fails=fails+1
    fi
    return 4 # the test did not pass
}

#--------------------------------------------------------------------------------
function Evaluate_run_result() {
    run_res=$1
    test_dir_name=$2
    res_path=$3
    results_csv_file=$4
    case "${run_res}" in
           0)
             echo "Test ${test_dir_name} PASSED"
             echo "TEST;${test_dir_name};PASSED;${run_time};" >>"${res_path}/${results_csv_file}"
             ;;
          3)
             echo "Test ${test_dir_name} REF_FAILURE"
             echo "TEST;${test_dir_name};REF_FAILURE;${run_time};" >>"${res_path}/${results_csv_file}"
             ;;
           4)
             echo "Test ${test_dir_name} FAILED"
             echo "TEST;${test_dir_name};FAILED;${run_time};" >>"${res_path}/${results_csv_file}"
             ;;
           6)
             echo "Test ${test_dir_name} REF_FAILURE"
             echo "TEST;${test_dir_name};REF_FAILURE;${run_time};" >>"${res_path}/${results_csv_file}"
             ;;
           7)
             echo "Test ${test_dir_name} REF_FAILURE"
             echo "TEST;${test_dir_name};REF_FAILURE;${run_time};" >>"${res_path}/${results_csv_file}"
             ;;
           124)
             echo "Test ${test_dir_name} TIMEOUT"
             echo "TEST;${test_dir_name};TIMEOUT;${run_time};" >>"${res_path}/${results_csv_file}"
             ;;
           *)
             echo "Test ${test_dir_name} ERROR"
             echo "TEST;${test_dir_name};ERROR;${run_time};" >>"${res_path}/${results_csv_file}"
             ;;
        esac


}
#--------------------------------------------------------------------------------
function Finish {
    cd ..
#    echo "Delete working dir: $work_dir"
    rm -rf $work_dir
    [[ "$1" = "" ]] || Err "$1"
    echo "done"
    exit
}
#--------------------------------------------------------------------------------
verify=n
res_dir=""
res_file=""
keep_dirs=n
prefix=""
test_name=Test
total=-1
add_opts=""
limit_core_size=0
outer_control=""
run_time=0
all_time=0
generate_only="false"

if [[ -n "${SOURCE_TEST_DIR}" ]]; then
    verify=y
    source_dir="${SOURCE_TEST_DIR}"
fi

if [[ -n "${RESULTS_TEST_DIR}" ]]; then
    res_dir="${RESULTS_TEST_DIR}"
fi

while [ "$1" != "" ]; do
    case $1 in
	-v)
#		[[ -d $2 ]] || Err "no directory to verify: $2"
	    verify=y
		source_dir=$2
	    shift;;
	-r)
	    res_dir=$2
		shift;;
	-kd) keep_dirs=y;;
	-p)
	    prefix=$2
	    shift;;
	-arg) add_opts="$add_opts $2"
         shift;;
	-f)  res_file=$2
		 shift;;
	-conf) conf_file=$2
	    shift;;
	-sp) SAVE_PASSED="true";;
	-g) generate_only="true";;
    -o) outer_control="-o";;
	*)   total=$1;;
    esac
    shift
done
#--------------------------------------------------------------------------------

passes=0
crashes=0
fails=0
timeouts=0
invalids=0
ref_failures=0
ref_crashes=0
ref_timeouts=0

#res_path=$FILER_DIR/$res_dir
res_path=$res_dir

if [[ "${res_dir}" = /* ]]; then
    res_path=${res_dir}
else
    res_path=`readlink -f ${CURR_DIR}/${res_dir}`
fi

if [ -d $res_path/hangs -o -d $res_path/crashes -o -d $res_path/fails -o -d $res_path/errors -o -d $res_path/ref_failures -o -d $res_path/invalids -o -d $res_path/ref_hangs -o -d $res_path/ref_crashes ]; then
    if [ "$keep_dirs" = "n" ]; then
        Err "rt.sh: remove dirs: hangs, crashes, fails and errors from $res_path"
    fi
fi

echo --------------------------------------
echo Java under test: ${JAVA_UNDER_TEST}
echo Java under test options: ${JAVA_UNDER_TEST_OPTS} $add_opts
echo --------------------------------------

echo "Results dir: ${res_path}"

[[ -n "${WORK_DIR}" ]] && prefix_="$(cd ${WORK_DIR}; pwd)" || prefix_="${TMPDIR:-/tmp}"

#TODO:
[[ -d /export/ram ]] && prefix_="/export/ram/tmp"
mkdir -p $prefix_ &> /dev/null
work_dir=$(mktemp -d "fuzzer.tmp.XXXXXXXXXX")

echo "Working dir: $work_dir"
cd $work_dir

trap Finish 1 2 3 6 14 15

if [ "$verify" == "y" ]; then

    # Transform source path to absolute path
    if [[ "${source_dir}" != /* ]]; then
        source_dir=`readlink -f ${CURR_DIR}/${source_dir}`
    fi

    # source config file if found
    config_file="${source_dir}/config.sh"
    if [[ -f "${config_file}" ]]; then
        echo "Use config: ${config_file}"
        source "${config_file}"
    fi

    # optionally add configured subdir
    if [[ -n "$source_subdir" ]]; then
        source_dir="${source_dir}/${source_subdir}"
    fi
    if [[ ! -d ${source_dir} ]] ; then
        Finish "$0: directory to run tests from (specified after -v option) not found: ${source_dir}"
    fi
    echo "Source dir: ${source_dir}"

    mkdir -p "${res_path}"
    echo "LEGEND;Test ID;Results;Seconds" >"${res_path}/${results_csv_file}"

    ulimit -S -c ${limit_core_size}
    for dir in `ls -d $source_dir/*/`; do
        test_dir_name="$(basename $dir)"
        rm -f $FILES_OF_INTEREST > /dev/null 2>&1
        cp $dir/*.java $dir/*.class $dir/rt_out_ref $dir/rt_err_ref .

        Run_test ${test_dir_name}
        run_res=$?
        Evaluate_run_result ${run_res} ${test_dir_name} ${res_path} ${results_csv_file}

            done

    {
        echo "TOTAL;"$((passes+fails+timeouts+crashes+ref_failures+ref_crashes+ref_timeouts))";"
        echo "PASSES;$passes;"
        echo "CRASHES;$crashes;"
        echo "FAILURES;$fails;"
        echo "TIMEOUTS;$timeouts;"
        echo "REFERENCE FAILURES;"$((ref_failures+ref_crashes+ref_timeouts))";"
        echo "SECONDS;$all_time;"
    } >>"${res_path}/${results_csv_file}"

    echo "TOTAL "$((passes+fails+timeouts+crashes+ref_failures+ref_crashes+ref_timeouts))" "
    echo "PASSES $passes "
    echo "CRASHES $crashes "
    echo "FAILURES $fails "
    echo "TIMEOUTS $timeouts "
    echo "REFERENCE FAILURES "$((ref_failures+ref_crashes+ref_timeouts))" "
    echo "SECONDS $all_time "

    Finish
fi

if [ $total -lt -1 ]; then
    Finish "invalid number of iterations: $total"
elif
    [ $total -eq -1 ] ; then
    echo "NUMBER OF TESTS TO BE GENERATED: UNLIMITED, TILL THE SCRIPT IS KILLED"
else
    echo "NUMBER OF TESTS TO BE GENERATED: ${total}"
fi

ulimit -S -c ${limit_core_size}

mkdir -p ${res_path}
echo "LEGEND;Test ID;Results;Seconds" >"${res_path}/${results_csv_file}"
iters=0
fails_in_a_row=0
statistics_string=""
while [ $iters != $total ]; do
    problem=no
    let iters=iters+1
    let perc=$iters*100/$total
    if [[ ${total} -gt 0 ]] ; then
        statistics_string="- ${perc}%/$total"
    fi
    rm -f $FILES_OF_INTEREST > /dev/null 2>&1
        echo "ruby -I$RUBY_CODE_DIR $RUBY_CODE_DIR/Fuzzer.rb -f $conf_file" >> rt_cmd
        ruby -I$RUBY_CODE_DIR $RUBY_CODE_DIR/Fuzzer.rb -f $conf_file > $test_name.java
        ruby_res=$?
        if [ $ruby_res -ne 0 ]; then
            # Debug only:
            #Save_res invalids $iters "Invalid Java test generated, e.g., contains cycled call chain"
            echo "Invalid Java test generated, e.g., contains cycled call chain"
            let invalids=invalids+1
            run_res=1
            let iters=iters-1
            continue
        fi
        lines=`cat $test_name.java | wc -l | xargs`
        if [[ "${generate_only}" == "true" ]] ; then
            Save_res generated ${prefix}${iters} "Test generated"
            echo "$prefix$iters ($lines lines) [$iters valid tests generated, $invalids incorrect tests ${statistics_string}"
           continue
        fi
        cp $RUBY_CODE_DIR/FuzzerUtils*.java .
        ${JAVAC} ${JAVAC_OPTS} $test_name.java
        if [ $? -ne 0 ]; then
            Save_res invalids $iters "Invalid Java test generated: failed to compile with javac"
            let invalids=invalids+1
            run_res=1
        else
            Run_test $iters
            run_res=$?
            if [ $run_res -eq 3 ]; then  # invalid test run: Reference Java crash
                problem="Reference Java crash"
            elif [ $run_res -eq 6 ]; then  # invalid test run: Timeout
                problem="Reference Java timeout"
            elif [ $run_res -eq 7 ]; then  # other reference java failure
                problem="Reference Java failure"
            fi
        fi
        Evaluate_run_result ${run_res} ${prefix}${iters} ${res_path} ${results_csv_file}
    if [ $run_res -eq 0 ]; then
        fails_in_a_row=0
    else
        let fails_in_a_row=fails_in_a_row+1
        [[ $fails_in_a_row -eq 100 ]] && Finish "100 failures in a row"
    fi
    if [ "$problem" != "no" ]; then
        echo "$prefix$iters ($lines lines) [$passes passed, $crashes crashes, $fails fails, $timeouts hangs, $invalids incorrect tests, $((ref_failures+ref_crashes+ref_timeouts )) Reference Java failures] ${statistics_string}"
#        let iters=iters-1
    else
        if [ ${total} -gt 0 ] ; then
            echo "$prefix$iters ($lines lines) [$passes passed, $crashes crashes, $fails fails, $timeouts hangs, $invalids incorrect tests, $((ref_failures+ref_crashes+ref_timeouts )) Reference Java failures] ${statistics_string}"
        fi
    fi
done

if [ "$res_file" != "" ]; then
    echo "$prefix $total: $passes passed, $crashes crashes, $fails fails, $timeouts hangs, $invalids incorrect tests, $((ref_failures+ref_crashes+ref_timeouts )) Reference Java failures" >>$res_path/$res_file
fi

Finish

