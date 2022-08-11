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
# Run the Fuzzer tool through rt.sh script in multiple processes.
# Parameters:
#    -NP <int>  - number of processes to launch (default: 5)
#    -NT <int>  - number of tests to generate and run
#    -R <path>  - path to a dir for storing results
#    -P <str>   - string for forming test name prefix like this: <str>$<number of process>-.
#                 For example, "-P a" leads to prefixes a1-, a2-, ... (default: n|o|"")
#    -A <rest>  - the rest of arguments are passed to rt.sh
#--------------------------------------------------------------------------------


#RUN_SCRIPT="bash rt.sh"
#RB_UPDATE="-u rb"
SUMMARY_NAME=summary.txt
TRACE_FILE=trace

pref="r"
num_of_proc=5
tests_count=-1
res_dir_arg="mrt_unknown"
rt_args=""

while [ "$1" != "" ]; do
    case $1 in
	-P)	 pref=$2
	     shift;;
	-R)	 res_dir_arg=$2
		 shift;;
	-NP) num_of_proc=$2
	     shift;;
	-NT) tests_count=$2
	     shift;;
	-A)  shift
	     rt_args=$@
	     break;;
	*)
	    echo Unexpected argument: $1
        exit;;
    esac
    shift
done

#-------------------------------------------------------------------------------- Prepare for the run
CURR_DIR=$(pwd) # current dir
RUN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )" # Fuzzer scripts dir
source ${RUN_DIR}/common.sh
#export FUZZER_ROOT_DIR
#export RUN_DIR
RUN_SCRIPT="bash ${RUN_DIR}/rt.sh"

# Transform results dir path to absolute path
if [[ "${res_dir_arg}" = /* ]]; then
    res_dir=${res_dir_arg}
else
    res_dir=`readlink -f ${CURR_DIR}/${res_dir_arg}`
fi

[[ -d $res_dir ]] || ( mkdir -p $res_dir && chmod -R 775 $res_dir )
res_file="$pref-$SUMMARY_NAME"

echo $tests_count tests will be run in each of $num_of_proc processes 
echo Prefix: $pref Res dir: $res_dir Arguments: $rt_args
#read -p "Continue?(y/n): " ans
#if [ "$ans" != "y" ]; then
#    echo "The run is cancelled"
#    exit
#fi

#-------------------------------------------------------------------------------- Run the tool
start_time=`date`
host=`uname -n`
echo "Host:     $host
Tests:    $num_of_proc x $tests_count
Args:     $rt_args

Started  at: $start_time

" >> $res_dir/$res_file
pids=""
iters=0
while [ $iters != $num_of_proc ]; do
    let iters=iters+1
    $RUN_SCRIPT $tests_count -r $res_dir -f $res_file -p ${pref}${iters}- -kd $rt_args &
    pids="$pids $!"
done
trap 'kill $pids' SIGINT SIGTERM
wait

pids=""

touch "$res_dir/$TRACE_FILE"
chmod 777 "$res_dir/$TRACE_FILE"
echo "$pref runs complete" >> $res_dir/$TRACE_FILE

end_time=`date`
echo All the test runs are complete: $num_of_proc x $tests_count, Args: $rt_args
echo Results are in $res_dir
echo -e "\n\nStarted  at: $start_time"
echo -e "\nFinished at: $end_time\n" | tee -a $res_dir/$res_file

#-------------------------------------------------------------------------------- Send notification
host=`uname -n`
echo "All the test runs are complete.
Results: $res_dir, $res_file

`cat $res_dir/$res_file`" 

rm -f `find "$res_dir" -name core`
