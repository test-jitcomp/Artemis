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

#==========================================================
#  Java* Fuzzer for Android*
#  Try-catch blocks and throwing exceptions
#
# Authors: Mohammad R. Haghighat, Dmitry Khukhro, Andrey Yakovlev
#==========================================================

#----------------------------------------------------------
# Java* Fuzzer test generator
#
# Modifications 2017-2018: Nina Rinskaya (Azul Systems)
#----------------------------------------------------------

#----------------------------------------------------------------------------
# ArrayIndexOutOfBoundsException - read/write from/to array
# NegativeArraySizeException - Negative array size when creating
# NullPointerException - synchronized(object=null), get/write field, invoke method of object=null,
#                        read/write/length from/to/of array=null
# ClassDefNotFoundException?- Class failed to resolve during filled-new-array
# ArithmeticException - / by zero
#----------------------------------------------------------------------------
$USER_DEF_EXC = "UserDefinedException"
$EXC_LIST = ["ArithmeticException", "ArrayIndexOutOfBoundsException", "NegativeArraySizeException",
    "NullPointerException", $USER_DEF_EXC]
$TEST_CLASS_NAME = "TestClass"

class TryStmt < Statement
    attr_reader :exception

    def initialize(cont, par)
        super(cont, par, true, false)
        if (stmtsRemainder() < 4 or
               loopNesting() > 3)  # limiting depth of nested loops with try-catch
            @emptyFlag = true
            return
        end
        @exception = wrand($EXC_LIST)
        @nestedStmts["block"] = genStmtSeq($conf.max_try_stmts, false)
        @nestedStmts["catch"] = [AfterTryStmt.new(cont, par, "catch", @exception)]
        addExc = prob(15) ? wrand($EXC_LIST - [@exception]) : ""
        @nestedStmts["add_catch"] = [AfterTryStmt.new(cont, par, "catch", addExc)] unless addExc.empty?
        @nestedStmts["finally"] = [AfterTryStmt.new(cont, par, "finally")] if prob(30)
        if [@exception, addExc].member?($USER_DEF_EXC) and !(defined? $excAdded)
            $auxClasses += "class " + $USER_DEF_EXC + " extends RuntimeException {\n"
            $auxClasses += "    public int field;\n"
            $auxClasses += "}\n"
            $excAdded = true
        end
    end

    def gen
        res = ln("try {")
        shift(1)
        res += @nestedStmts["block"].collect{|st| st.gen()}.join()
        shift(-1)
        res += ln("}")
        add_flag = @nestedStmts.has_key?("add_catch")
        if add_flag and prob(50)
            res += @nestedStmts["add_catch"][0].gen()
            add_flag = false
        end
        res += @nestedStmts["catch"][0].gen()
        if add_flag
            res += @nestedStmts["add_catch"][0].gen()
        end
        if @nestedStmts.has_key?("finally")
            res += @nestedStmts["finally"][0].gen()
        end
        res
    end

    #------------------------------------------------------
    # returns true if given exception is caught in statement hierarchy starting with stmt
    def TryStmt.isCaught?(exception, stmt)
        while (stmt)
            return true if stmt.instance_of?(TryStmt) and (stmt.exception == exception or
            (stmt.nestedStmts.has_key?("add_catch") and stmt.nestedStmts["add_catch"][0].excName == exception))
            stmt = stmt.parent
        end
        false
    end
end

#===============================================================================
# exception processing unit: catch or finally block
# Arguments: kind - either "catch" or "finally"; exc - name of exception for catch
class AfterTryStmt < Statement
    attr_reader :excName

    def initialize(cont, par, kind, exc=nil)
        super(cont, par, true, false)
        @kind = kind
        @excName = exc
        @nestedStmts["block"] = genStmtSeq($conf.max_el_stmts)
    end

    def gen
        res = ln("catch (" + @excName + " " + @context.genUniqueName("exc") + ") {") if @kind == "catch"
        res = ln("finally {") if @kind == "finally"
        shift(1)
        res += @nestedStmts["block"].collect{|st| st.gen()}.join()
        shift(-1)
        res + ln("}")
    end
end

#===============================================================================
# a statement that causes throwing one of select exceptions
class ExcStmt < Statement

    def initialize(cont, par)
        super(cont, par, true)
        if ((exceptions = excList()).empty?)
            @emptyFlag = true
            return
        end
        exc = wrand(exceptions)
        case exc
        when "ArithmeticException"
            var = Var.new(cont, "int", Nam::NULL)
            divdr = ExpScal.new(self, var, 1)
            dest = Expression.new(self, 'int', 1, $conf.exp_kind.getRand(['scalar', 'array']), nil, Exp::DEST)
            if prob(30)
                val  = Expression.new(self, 'int', 2)
            else
                val = Expression.new(self, 'int', 2, $conf.exp_kind.getRand(['scalar', 'array']))
            end
            oper = $conf.operators['arith'].getRand(['/','%'])
            div  = Expression.new(self, (typeGT?(val.resType, 'int') ? val.resType : 'int'),
            1, 'oper', {'op'=>Operator.get(oper, nil, 'arith'), 'vals'=>[val, divdr]}, Exp::CAST)
            assn = Expression.new(self, 'int', 0, 'assign', {'op'=>Operator.get('=', nil, 'integral_assn'), 'vals'=>[dest, div]})
            @nestedStmts["stmts"] = [AssignmentStmt.new(cont, self, assn)]
        when "ArrayIndexOutOfBoundsException"
            if prob(50) # read
                dest = Expression.new(self, 'int', 1, $conf.exp_kind.getRand(['scalar', 'array']), nil, Exp::DEST)
                val  = Expression.new(self, 'int', 1, 'array', nil, Exp::AIND)
            else # write
                dest = Expression.new(self, 'int', 1, 'array', nil, Exp::DEST|Exp::AIND)
                val  = Expression.new(self, 'int', 1)
            end
            assn = Expression.new(self, 'int', 0, 'assign', {'op'=>Operator.get('=', nil, 'integral_assn'), 'vals'=>[dest, val]})
            @nestedStmts["stmts"] = [AssignmentStmt.new(cont, self, assn)]
        when "NegativeArraySizeException"
            var = Expression.new(self, 'int', 1, 'scalar', nil, Exp::DEST)
            assn = Expression.new(self, 'int', 0, 'assign', {'op'=>Operator.get('=', nil, 'integral_assn'), 'vals'=>[var, "-10"]})
            @nestedStmts["stmts"] = [AssignmentStmt.new(cont, self, assn)]
            dest = Expression.new(self, 'int', 1, 'arrname', nil, Exp::DEST)
            @nestedStmts["stmts"] << (dest.gen() + " = " + dest.operands[0].gen_new(var.gen()) + ";")
        when "NullPointerException"
            @nestedStmts["stmts"] = NPException()
        when $USER_DEF_EXC
            @nestedStmts["stmts"] = ["if ((" + Expression.new(self, "int").gen() +
                ") < " + $conf.max_num.to_s + ") throw new " + $USER_DEF_EXC + "();"]
        else
            error("ExcStmt.initialize: exc = " + exc, true)
        end
    end

    #------------------------------------------------------
    # throw NullPointerException
    def NPException
        caseCode = rand(2) # object, array
        stmt = []
        if caseCode == 0 # object
            if ! (defined? $classAdded)
                $auxClasses += "class "+$TEST_CLASS_NAME+" {\n"
                $auxClasses += "    public int field;\n"
                $auxClasses += "    public void meth() {field = 1;}\n"
                $auxClasses += "}\n"
                $classAdded = true
            end
            objName = @context.genUniqueName("var", $TEST_CLASS_NAME)
            stmt << ($TEST_CLASS_NAME + " " + objName + " = null;")
        elsif # array
        #objName = Expression.new(self, 'int', 1, 'arrname', nil, Exp::DEST).gen()
        #stmt << (objName + " = null;")
        objName = Arr.new(@context, 1, "int", Nam::NULL).name
        end
        case caseCode*10 + rand(4)
        when  0 # synchronized(object)
            stmt  << "synchronized(" + objName + ") {"
            stmt << AssignmentStmt.new(@context, self)
            stmt  << "}"
        when  1 # get field
            var = Expression.new(self, 'int', 1, 'scalar', nil, Exp::DEST)
            stmt  << var.gen() + " = " + objName + ".field;"
        when  2 # write field
            stmt  << objName + ".field = 3;"
        when  3 # invoke method
            stmt  << objName + ".meth();"
        when 10, 11 # read array
            var = Expression.new(self, 'int', 1, 'scalar', nil, Exp::DEST)
            stmt  << var.gen() + " = " + objName + "[1];"
        when 12 # write array
            stmt  << objName + "[2] = 3;"
        when 13 # length
            var = Expression.new(self, 'int', 1, 'scalar', nil, Exp::DEST)
            stmt  << var.gen() + " = " + objName + ".length;"
        end
        return stmt
    end

    #------------------------------------------------------
    def excList
        res = []
        stmt = @parent
        while (stmt)
            if stmt.instance_of?(TryStmt)
                res << stmt.exception
                res << stmt.nestedStmts["add_catch"].excName if stmt.nestedStmts.has_key?("add_catch")
            end
            stmt = stmt.parent
        end
        res
    end

    #------------------------------------------------------
    def gen
        res = ""
        @nestedStmts["stmts"].each do |stmt|
            res += stmt.instance_of?(String) ? ln(stmt) : stmt.gen()
        end
        res
    end
end
