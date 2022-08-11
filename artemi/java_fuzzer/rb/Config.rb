# Copyright (C) 2016 Intel Corporation
# Modifications copyright (C) 2017-2018 Azul Systems
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#----------------------------------------------------------
#  Java* Fuzzer for Android*
#  Configuration of the tool
#
# Authors: Mohammad R. Haghighat, Dmitry Khukhro, Andrey Yakovlev
#----------------------------------------------------------

#----------------------------------------------------------
# Java* Fuzzer test generator
#
# Modifications 2017-2018: Nina Rinskaya (Azul Systems), Ivan Popov (Azul Systems)
#----------------------------------------------------------

require 'getoptlong'
require 'yaml'

# GLOBAL CONSTANTS
TOOL_VERSION = "1.0.001"
TABN = 4
SUPER       = 'FuzzerUtils'
MAX_TRIPNM  = 'N'
INITC       = 'init'
CHECKC      = 'checkSum'
RES         = 'res'
LETTER = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
    'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z']
DIGIT = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9']
KW = [CHECKC, INITC, MAX_TRIPNM, RES, 'main',
    'abstract',   'continue',   'for',          'new',         'switch',
    'assert',     'default',    'if',           'package',     'synchronized',
    'boolean',    'do',         'goto',         'private',     'this',
    'break',      'double',     'implements',   'protected',   'throw',
    'byte',       'else',       'import',       'public',      'throws',
    'case',       'enum',       'instanceof',   'return',      'transient',
    'catch',      'extends',    'int',          'short',       'try',
    'char',       'final',      'interface',    'static',      'void',
    'class',      'finally',    'long',         'strictfp',    'volatile',
    'const',      'float',      'native',       'super',       'while',
    'Object',     'new']
TSET_BOOL        = ['boolean']
TSET_STRING      = ['String']
TSET_BOOL_VAR    = ['boolean_var']
TSET_INTEGRAL_VAR= ['integral_var']
TSET_ARITH_VAR   = ['arith_var']
TSET_INTEGRAL    = ['byte', 'short', 'int', 'long']
TSET_ARITH       = TSET_INTEGRAL + ['char', 'float', 'double']
TSET_OBJECT     = ['Object']
TSET_OBJECT_VAR     = ['object_var']
TSET_ARRAY     = ['Array']
TSET_ARRAY_VAR     = ['array_var']
ARITH_CATS = ['arith', 'uarith', 'indecrem_pre', 'indecrem_post']
INTEG_CATS = ['integral'] + ARITH_CATS
OBJECT_CATS = ['Object']
ARRAY_CATS = ['Array']
OP_TYPES   = {'boolean' => {'oper'=>['relational', 'boolean'], 'assn'=>['boolean_assn']},
    'byte'    => {'oper'=>INTEG_CATS, 'assn'=>['integral_assn', 'arith_assn']},
    'char'    => {'oper'=>INTEG_CATS, 'assn'=>['integral_assn', 'arith_assn']},
    'short'   => {'oper'=>INTEG_CATS, 'assn'=>['integral_assn', 'arith_assn']},
    'int'     => {'oper'=>INTEG_CATS, 'assn'=>['integral_assn', 'arith_assn']},
    'long'    => {'oper'=>INTEG_CATS, 'assn'=>['integral_assn', 'arith_assn']},
    'float'   => {'oper'=>ARITH_CATS, 'assn'=>['arith_assn']},
    'double'  => {'oper'=>ARITH_CATS, 'assn'=>['arith_assn']},
    'Object' => {'oper'=>OBJECT_CATS, 'assn'=>['object_assn']},
    'Array' => {'oper'=>ARRAY_CATS, 'assn'=>['array_assn']}}

def typeGT?(type1, type2)
    type_weight = {'Array'=>0, 'Object'=>0, 'boolean'=>0, 'String'=>0, 'byte'=>1, 'char'=>2, 'short'=>3, 'int'=>4, 'long'=>5, 'float'=>6, 'double'=>7}
    type_weight[type1] > type_weight[type2]
end

#===============================================================================
# table: value => probability plus random pick
class ProbTab
    attr_reader :values

    def initialize(tab)
        @values = tab.keys
        @indices = []
        @values.each_index {|ind| @indices += [ind] * tab[@values[ind]]}
    end

    def getRand(subSet=nil, exclSubSet=nil)
        subInd = @indices
        subInd = @indices.find_all{|ind| subSet.member?(@values[ind])} if subSet
        subInd = subInd.find_all{|ind| !exclSubSet.member?(@values[ind])} if exclSubSet
        @values[wrand(subInd)]
    end

    def setValue(key,value)
        index = @indices.find_all{|ind| @values[ind] == key}[0]
        @indices.delete(index)
        value.times{|num| @indices << index}
    end
end

#===============================================================================
# Configuration of the tool: parameters that can be changed dynamically
class Conf
    attr_accessor :mode, :max_size, :max_stmts, :max_arr_dim, :width, :max_shift
    attr_accessor :max_meths, :max_args
    attr_accessor :max_num, :max_exp_depth
    attr_accessor :max_if_stmts, :max_el_stmts, :max_try_stmts, :max_loop_stmts, :max_loop_depth, :start_frac
    attr_accessor :types, :exp_kind, :op_cats, :ind_kinds, :p_invoc_expr, :p_inl_invoc_expr, :operators, :var_types, :max_threads
    attr_accessor :p_empty_seq, :p_else, :p_triang, :p_meth_reuse, :p_return, :p_var_reuse, :p_big_switch,
    :p_packed_switch, :for_step, :p_ind_var_type, :stmt_list, :p_class_reuse, :p_constructor, :p_volatile,
    :p_null_literal, :max_callers_chain, :max_classes, :p_non_static_method, :p_big_array, :max_big_array, :min_big_array, :mainClassName, :package
    attr_accessor :outer_control, :outer_control_prob
    attr_accessor :min_small_meth_calls, :max_small_meth_calls
    attr_accessor :mainTest_calls_num
    attr_accessor :time_sleep_complete_tier1
    attr_accessor :mainTest_calls_num_tier2
    attr_accessor :max_nested_size
    attr_accessor :min_size_fraction # minimal fraction of max_size to be guaranteed (other fraction is random(max_size-min_size_fraction*max_size) )
    attr_accessor :max_nested_size_not_mainTest # max total number of iterations of nested loops in methods called from mainTest
    attr_accessor :exp_invoc_loop_depth # max loop depth for which method invocation in expression can be generated
    attr_accessor :p_extends_class # probability of class extension
    attr_accessor :p_unknown_loop_limit  # probability that for loop has an unknown upper limit (random expression as a loop limit) -> can result in infinite loop
    attr_accessor :p_inequality_in_loop_condition # probability that for loop have != in loop condition
    attr_accessor :p_loop_iter_num_gt_max_size # probability that for loop iterations number will be greater than max arrays size
    attr_accessor :p_switch_empty_case # probability of having a case with empty body
    attr_accessor :p_guaranteed_AIIOB_in_infinite_loop_with_inequality # sometimes we want to intentionally start with upper bound and end up with lower bound: in case of < or > we would have unreached loop, in case of != we will have infinite loop, that's why we will always throw AIOOBException here
    attr_accessor :p_method_override # probability that method in child class will override parent's class with matching signature
    attr_accessor :max_object_array_size # Max object array size
    attr_accessor :allow_object_args # 1: allow objects to be passed as methods arguments, 0: disallow objects to be passed as methods arguments

                  #------------------------------------------------------
    # default values of the generator parameters
    def initialize(parse_args=false)
        # general:
        @mode        = 'default' # the mode of the generator: either 'defaut' or 'MM_extreme' (memory management)
        @mainClassName = 'Test' # Name of a main class
        @package = '' # Add java package name
        @outer_control = true # Ability to setup a random seed for Java code
        @outer_control_prob = 3 # 1 - 100% invocations, 2 - 50% invocations, 3 - 33% invocations, etc.
        @max_size    = 100  # max length of arrays and loops; should not be less than 10
        @max_nested_size = 10000000 # max total number of iterations of nested loops
        @max_nested_size_not_mainTest = 20000 # max total number of iterations of nested loops in methods called from mainTest
        @min_size_fraction = 0.5 # minimal fraction of max_size to be guaranteed (other fraction is random(max_size-min_size_fraction*max_size) )
        @max_stmts   = 15   # generated statements max count
        #@max_nlen    = 3   # max length of a random name
        @max_arr_dim = 2    # array max dimension
        @width       = 120  # width of resulting text
        @max_shift   = 110  # max indentatation of a text line
        @p_big_array = 20   # probability of big array creation
        @min_big_array = 12276 # 12kB for byte type, 24kB for short and char, 48kB for int and float, 96kB for double
        @max_big_array = 1000000 # a random size to add to a big array
        # methods:
        @max_meths   = 10       # max count of methods (excluding main) in a class
        @max_args    = 5       # max count of a method arguments
        @max_classes = 0       # max count of classes. The actual number can be greater due to foreign class fields generation
        @max_threads = 0       # max count of runThread(Runnable obj) usage
        @p_constructor = 0    # probability of non-trivial constructor
        @max_callers_chain = 2 # Maximum chain of methods calling each other (including constructors)
        @p_non_static_method = 50 # probability of non-static method
        @mainTest_calls_num = 20 # number of mainTest method calls, should be adjusted with CompileThreshold
        @time_sleep_complete_tier1 = 5000 # time in milliseconds to sleep after mainTest_calls_num invocations of mainTest to make sure Tier1 compilation is done; 0 if no sleep needed
        @mainTest_calls_num_tier2 = 50 # number of mainTest method calls after sleep, should be adjusted with CompileThreshold (Tier2); 0 if no need to call mainTest again
        @exp_invoc_loop_depth = 10 # max loop depth for which method invocation in expression can be generated
        @p_extends_class =50 # probability of class extension
        @p_method_override = 80 # probability that method in child class will override parent's class with matching signature
        # expressions:
        # MAX_NUM = 100
        # MAX_NUM = 0x80000000 # max int + 1
        @max_num = 0x10000  # 16-bit int + 1 - max int literal
        @max_exp_depth = 3  # max depth of expression recursion
        @p_null_literal = 30 # probability of null literal
        # statements:
        @max_if_stmts   = 4     # max count of statements in if part of if statement
        @max_el_stmts   = 4     # max count of statements in else part of if statement
        @max_try_stmts  = 6     # max count of statements in try
        @max_loop_stmts = 5     # max count of statements in a loop
        @max_loop_depth = 3     # max depth of nested loops
        @start_frac     = 16    # fraction of the max value of induction var for initial value
        @min_small_meth_calls = 100 # minimal number of small method calls
        @max_small_meth_calls = 10000 # maximal number of small method calls
        @p_unknown_loop_limit = 0  # probability that for loop has an unknown upper limit (random expression as a loop limit) -> can result in infinite loop
        @p_inequality_in_loop_condition = 0 # probability that for loop will have != in loop condition
        @p_loop_iter_num_gt_max_size = 0 # probability that for loop iterations number will be greater than max arrays size
        @p_guaranteed_AIIOB_in_infinite_loop_with_inequality = 20 # sometimes we want to intentionally start with upper bound and end up with lower bound: in case of < or > we woul    d have unreached loop, in case of != we will have infinite loop, that's why we will always throw AIOOBException here
        @max_object_array_size = @max_size
        @allow_object_args = 0 # 1: allow objects to be passed as methods arguments, 0: disallow objects to be passed as methods arguments

        # expression probabilities:
        @p_invoc_expr     = 25   # probability of method invocation in expression
        @p_inl_invoc_expr = 2   # probability of inlinable method invocation in expression
        
        # variables:
        @p_volatile = 15 # probability of a variable being volatile
        
        # Var types description:
        # non_static - non-static field of a current class
        # static - static field of a current class
        # local - local variable of a current method
        # static_other - static field of a foreign class, example: Class1.iFld
        # local_other - non-static field of an object of a foreign class, example: Object1.iFld
        # block - the variable is declared in current block (loop)
        @var_types = ProbTab.new({'non_static'=>1, 'static'=>1, 'local'=>10, 'static_other'=>1,'local_other'=>1, 'block'=>3})
        @types = ProbTab.new({'Array'=>2, 'Object'=>0, 'boolean'=>1, 'String'=>0, 'byte'=>1, 'char'=>0, 'short'=>1,
            'int'=>1, 'long'=>1, 'float'=>1, 'double'=>1})
        @exp_kind = ProbTab.new({'literal'=>20, 'scalar'=>8, 'array'=>2, 'field'=>0, 'oper'=>10,
            'assign'=>1, 'cond'=>0, 'inlinvoc'=>0, 'invoc'=>5, 'libinvoc'=>2})
        @op_cats = ProbTab.new({'relational'=>2, 'boolean'=>1, 'integral'=>5, 'arith'=>30,
            'uarith'=>5, 'indecrem_pre'=>3, 'indecrem_post'=>3, 'boolean_assn'=>1,
            'integral_assn'=>1, 'arith_assn'=>2, 'object_assn'=>0, 'array_assn'=>25})
        @ind_kinds = ProbTab.new({'-1'=>12, '0'=>18, '+1'=>12, 'any'=>1})
        @operators = {'relational'   => ProbTab.new({'=='=>1, '!='=>1, '<'=>1, '<='=>1, '>'=>1, '>='=>1}),
            'boolean'      => ProbTab.new({'=='=>1, '!='=>1, '&'=>1, '|'=>1, '^'=>1, '&&'=>1, '||'=>1, '!'=>1}),
            'integral'     => ProbTab.new({'&'=>1, '|'=>1, '^'=>1, '<<'=>1, '>>'=>1, '>>>'=>1, '~'=>1}),
            'arith'        => ProbTab.new({'+'=>1, '-'=>1, '*'=>1, '/'=>1, '%'=>1}),
            'uarith'       => ProbTab.new({'-'=>1}),
            'indecrem_pre' => ProbTab.new({'++'=>1, '--'=>1}),
            'indecrem_post'=> ProbTab.new({'++'=>1, '--'=>1}),
            'boolean_assn' => ProbTab.new({'='=>1}),
            'integral_assn'=> ProbTab.new({'='=>1, '&='=>1, '|='=>1, '^='=>1, '<<='=>1, '>>='=>1, '>>>='=>1}),
            'arith_assn'   => ProbTab.new({'='=>1, '+='=>1, '-='=>1, '*='=>1, '/='=>1, '%='=>1}),
            'object_assn' => ProbTab.new({'='=>1}),
            'array_assn' => ProbTab.new({'='=>1})}
        # statement probabilities:
        @p_empty_seq = 2      # probability of empty sequence of statements
        @p_else = 40          # probability of Else in If statement
        @p_triang = 30        # probability of induction var in For initial value expr
        @p_meth_reuse = 50    # probability of re-using known meth in an invocation
        @p_return = 10        # probability of return statement
        @p_var_reuse = 60     # probability of reusing existing var or arr
        @p_class_reuse = 70   # probability of reusing existing class
        @p_big_switch = 1     # probability of big switch
        @p_packed_switch = 60 # probability of packed switch
        @p_switch_empty_case = 5 # probability of having a case with empty body
        @for_step = ProbTab.new({-3=>1, -2=>1, -1=>4, 1=>32, 2=>1, 3=>1})
        @p_ind_var_type = ProbTab.new({'int'=>20, 'long'=>5, 'float'=>1, 'double'=>1}) # induction var types
        @stmt_list = {
            # class          weight  weight-in-loop   scale-down-factor
            ForLoopStmt     => [24,         12,         1.5],
            WhileDoStmt     => [ 8,          4,         1.5],
            EnhancedForStmt => [ 4,          2,         1.5],
            ContinueStmt    => [ 0,          1,           1],
            BreakStmt       => [ 0,          1,           1],
            IfStmt          => [ 2,          3,         1.5],
            SwitchStmt      => [ 1,          1,           1],
            AssignmentStmt  => [ 1,         40,           1],
            IntDivStmt      => [ 0,          1,           1],
            ReturnStmt      => [ 0,          1,           1],
            TryStmt         => [ 1,          2,           1],
            ExcStmt         => [ 1,          2,           1],
            VectStmt        => [ 0,          8,           1],
            InvocationStmt  => [ 1,          1,           2],
            CondInvocStmt   => [ 10,         1,           2],
            SmallMethStmt   => [ 10,         1,           2],
            NewThreadStmt   => [ 3,          1,           2]
        }
        parseArgs() if parse_args
        if @mode == 'default'
#           @types.setValue('Array', 0)
#           @types.setValue('Object', 0)
            @p_big_array = 0
#            @p_constructor = 0
            @max_threads = 0
        end
        $EXC_LIST.map!{|x| x==$USER_DEF_EXC ? $USER_DEF_EXC+@mainClassName : x}
        $USER_DEF_EXC=$USER_DEF_EXC+@mainClassName
        $TEST_CLASS_NAME = "TestClass"+@mainClassName
    end

    #------------------------------------------------------
    # define acceptable options and parse actual arguments
    def parseArgs
        forceDefaultMode=false
        opts = GetoptLong.new(
        ["-h",   "--help",       GetoptLong::NO_ARGUMENT],
        ["-f",   "--file",       GetoptLong::REQUIRED_ARGUMENT],
        ["--st", "--statements", GetoptLong::REQUIRED_ARGUMENT],
        ["-s",   "--size",       GetoptLong::REQUIRED_ARGUMENT],
        ["-d",   "--default",       GetoptLong::NO_ARGUMENT],
        ["-o",   "--outer-control",       GetoptLong::NO_ARGUMENT],
        ["-p",   "--package",       GetoptLong::REQUIRED_ARGUMENT],
        ["-n",   "--main-class-name", GetoptLong::REQUIRED_ARGUMENT])
        opts.each do |opt, arg|
            case opt
            when "-f"
                parseConfFile(arg)
            when "-s"
                @max_size = arg.to_i()
            when "--st"
                @max_stmts = arg.to_i()
            when "-d"
                forceDefaultMode = true
            when "-o"
                @outer_control = true
            when "-p"
                @package = arg.to_s
            when "-n"
                @mainClassName = arg.to_s
            end
        end
        if ARGV.length > 0
            error("Invalid arguments")
        end
        @mode = 'default' if forceDefaultMode
    end

    def parseConfFile(fileName)
        begin
            vals = YAML.load_file(fileName)
            vals.each { |key, value|
                if key == "operators"
                    value.each { |name, tab|
                        error("Invalid values for the category #{name} in conf file") unless tab.instance_of?(Hash)
                        @operators[name] = ProbTab.new(tab)
                    }
                    next
                end
                if key == "statements"
                    value.each { |name, arr|
                        error("Invalid values for the statement #{name} in conf file") if !arr.instance_of?(Array) or arr.length != 3
                        @stmt_list[Module.const_get(name)] = arr
                    }
                    next
                end
                value = ProbTab.new(value) if value.instance_of?(Hash)
                instance_variable_set("@#{key}", value)
            }
        rescue ArgumentError => e
            error("Could not parse configuration file: #{e.message}")
        rescue NameError => e
            error("Wrong class name: #{e.message}")
        end
    end
end

#---------------------------------------------------------------------
# Probability tables:
VAR_KIND    = ProbTab.new({'scalar'=>4, 'array'=>2, 'field'=>0})
