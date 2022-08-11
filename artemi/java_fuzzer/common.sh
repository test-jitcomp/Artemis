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

[ -z "${JAVA_HOME}" ] && echo "Error: JAVA_HOME not set" && exit 1

export REFERENCE_JAVA_HOME="${REFERENCE_JAVA_HOME:-"${JAVA_HOME}"}"

export JAVAC="${JAVAC:-${REFERENCE_JAVA_HOME}/bin/javac}"
export JAVAC_OPTS=${JAVAC_OPTS:-""}

export JAVA_REFERENCE="${JAVA_REFERENCE:-${REFERENCE_JAVA_HOME}/bin/java}"
export JAVA_REFERENCE_OPTS=${JAVA_REFERENCE_OPTS:-"-Xmx1G"}

export TESTED_JAVA_HOME="${TESTED_JAVA_HOME:-"${JAVA_HOME}"}"
export JAVA_UNDER_TEST="${JAVA_UNDER_TEST:-${TESTED_JAVA_HOME}/bin/java}"
export JAVA_UNDER_TEST_OPTS=${JAVA_UNDER_TEST_OPTS:-"-Xmx1G"}

export SAVE_PASSED="false"

RUN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

TEST_NAME=Test
export TIME_OUT="${TIME_OUT:-300}"
export REF_TIME_OUT="${REF_TIME_OUT:-$(( TIME_OUT ))}"

export IGNORE_DEBUG_OUTPUT_PATTERNS='.*CompilerOracle.*'
#--------------------------------------------------------------------------------
# Print error message and exit
function Err {
    echo "Error: $1"
    exit 1
}
#--------------------------------------------------------------------------------
# Set timeout
function SetTimeout {
    TIME_OUT=$1
}

function Check_Java() {
    JAVA_EXEC=$1
    if ! ${JAVA_EXEC} -version ; then
        Err "Failed to run java at ${JAVA_EXEC}"
    fi
}

#--------------------------------------------------------------------------------
# Run class Test on specified JVM (reference or tested)
function RunJava() {
    _TIME_OUT=$1
    shift
    VM=$1
    shift
    VM_ARGS=${*%${!#}} # all arguments except the last one
    _TEST_NAME=${@:$#} # last argument
    TEST_LOCATION=$(pwd)
    timeout -s 9 $_TIME_OUT ${VM} ${VM_ARGS} -cp ${TEST_LOCATION} ${_TEST_NAME}
    RunJava_res=$?
    if [ $RunJava_res -eq 124 ]; then
        echo "TIME_OUT!"
    fi
    return $RunJava_res

}
[ -n "${JAVA_REFERENCE}" ] && Check_Java ${JAVA_REFERENCE}
Check_Java ${JAVA_UNDER_TEST}

