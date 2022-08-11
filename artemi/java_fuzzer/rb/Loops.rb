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
#  Statements that imply control transfer
#
# Authors: Mohammad R. Haghighat, Dmitry Khukhro, Andrey Yakovlev
#===============================================================================

#----------------------------------------------------------
# Java* Fuzzer test generator
#
# Modifications 2017-2018: Nina Rinskaya (Azul Systems)
#----------------------------------------------------------

# FOR statement
class ForLoopStmt < Statement
    attr_reader :inductionVar      # induction var of this loop
    attr_reader :maxVal      # maxVal of induction var of this loop
    attr_reader :initVal      # initVal of induction var of this loop
    attr_reader :step     # step of induction var of this loop
    attr_reader :inMainTest    # true if loop is created in mainTest
    attr_reader :p_unknown_loop_limit # true if loop will have unknown at compile time limit

    def initialize(cont, par)
        #dputs("For loop creation!",true,10)
        super(cont, par, true, true)
        if (stmtsRemainder() < 2 or loopNesting() >= $conf.max_loop_depth)
            @emptyFlag = true

            return
        end
        @inMainTest = cont.method.mainTestFlag
        @context = Context.new(cont, Con::STMT, cont.method) #Experimental
        @inductionVar = Var.new(@context, $conf.p_ind_var_type.getRand())
        @inductionVar.inductionVarFlag = true
        @step = $conf.for_step.getRand()
        @p_unknown_loop_limit = prob($conf.p_unknown_loop_limit)
        indVars = ForLoopStmt.inductionVarsList(@parent)
        @maxVal = ForLoopStmt.indVarMaxValue(@parent, indVars, self)
        @initVal = (prob($conf.p_triang) and indVars.select() { |var| var != @maxVal and var != @inductionVar }.size > 0 ) ? wrand(indVars.select() { |var| var != @maxVal}) : (@maxVal.instance_of?(Var) ? 1 : mrand(@maxVal/$conf.start_frac) + 1)
            
        @unknown_zero_var=Var.new(@context, 'int');
        @unknown_zero_assn = Expression.new(self, 'int', 0, 'assign', {'op'=>Operator.get('=', nil, 'integral_assn'), 'vals'=>[ExpScal.new(self, @unknown_zero_var, 1, Exp::DEST), "FuzzerUtils.UnknownZero"]}, Exp::DEST)

        cond =(@step > 0 ? "<"  : ">")
        cond = ((prob($conf.p_inequality_in_loop_condition) and (@step == 1 or @step == -1) and !@initVal.instance_of?(Var) and !@maxVal.instance_of?(Var)) ? "!=" : cond) 
        if (@p_unknown_loop_limit)
            unknown_expr = Expression.new(@parent, $conf.types.getRand(TSET_INTEGRAL + ['float']), 3)
            unknown_expr = (prob(50) ? ExpIntLit.new(self, '1') : unknown_expr) # we want half of expression to be as simple 1*x, where x = FuzzerUtils.UnknownZero, to make sure all opts are applied 
            unknown_expr_zero = ExpBinOper.new(self, unknown_expr, "*", ExpScal.new(self, @unknown_zero_var))
            if prob(50) # adding unknown expression equal to zero either to init val or max val
                if @initVal.instance_of?(Var)       
                    @initVal=ExpBinOper.new(self, ExpScal.new(self, @initVal),'+',  unknown_expr_zero);
                elsif @initVal.instance_of?(ExpBinOper)
                    @initVal=ExpBinOper.new(self, @initVal, '+',  unknown_expr_zero);
                else
                    @initVal=ExpBinOper.new(self,  ExpIntLit.new(self, @initVal.to_s), '+', unknown_expr_zero);
                end
            else
                if @maxVal.instance_of?(Var)
                    @maxVal=ExpBinOper.new(self, ExpScal.new(self, @maxVal),'+', unknown_expr_zero)
                elsif @maxVal.instance_of?(ExpBinOper) 
                    @maxVal=ExpBinOper.new(self, @maxVal,'+', unknown_expr_zero)
                else
                    @maxVal=ExpBinOper.new(self,  ExpIntLit.new(self, @maxVal.to_s), '+', unknown_expr_zero)
                end

            end

        end

        @aioob_assn=nil
        if ((TryStmt.isCaught?("ArrayIndexOutOfBoundsException", @parent) and prob(80)) or prob($conf.p_loop_iter_num_gt_max_size)) # if ArrayIndexOutOfBoundsException is caught, will exceed max array index in only 80% of cases
            # we want to iterate from negative numbers sometimes: i = initVal - max_size; i < xx; i++
            if prob(20)
               if @initVal.instance_of?(Var)       
                    @initVal=ExpBinOper.new(self, ExpScal.new(self, @initVal),'-',  ExpIntLit.new(self, ($conf.max_size).to_s));
                elsif @initVal.instance_of?(ExpBinOper) 
                    @initVal=ExpBinOper.new(self, @initVal,'-', ExpIntLit.new(self, ($conf.max_size).to_s));
                else
                    @initVal=ExpBinOper.new(self,  ExpIntLit.new(self, @initVal.to_s), '-',  ExpIntLit.new(self, ($conf.max_size).to_s));
                end

            else
                if @maxVal.instance_of?(Var)       
                    @maxVal=ExpBinOper.new(self, ExpScal.new(self, @maxVal),'+',  ExpIntLit.new(self, ($conf.max_size).to_s));
                elsif @maxVal.instance_of?(ExpBinOper) 
                    @maxVal=ExpBinOper.new(self, @maxVal,'+', ExpIntLit.new(self, ($conf.max_size).to_s));
                else
                    @maxVal=ExpBinOper.new(self,  ExpIntLit.new(self, @maxVal.to_s), '+',  ExpIntLit.new(self, ($conf.max_size).to_s));
                end
            end
            #sometimes we want to intentionally start with upper bound and end up with lower bound: in case of < or > we would have unreached loop, thus, avoiding this case, in case of != infinite loop
            #that's why we will always throw AIOOBException here
            # try {
            #     for (i = i6; i != 0; i++) {
            #        i5=iArr[i]; <-- i will exceed array size because loop condition is never met
            #        ...
            #     }
            #} catch (ArrayIndexOutOfBoundsException e) {}
            if (prob($conf.p_guaranteed_AIIOB_in_infinite_loop_with_inequality) and cond == "!=" and TryStmt.isCaught?("ArrayIndexOutOfBoundsException", @parent))
                tmp1 = @initVal
                @initVal = @maxVal
                @maxVal = tmp1
                if prob(50) # read
                    dest = Expression.new(self, 'int', 1, $conf.exp_kind.getRand(['scalar', 'array']), nil, Exp::DEST)
                    val  = Expression.new(self, 'int', 1, 'array', nil)
                    val.operands[0] = ExpScal.new(self, @inductionVar, 1, Exp::CAST) # ArrayIndexOutOfBoundsException
                    val.operands[0].setType('int')
                else # write
                    dest = Expression.new(self, 'int', 1, 'array', nil, Exp::DEST)
                    dest.operands[0] = ExpScal.new(self, @inductionVar,  1, Exp::CAST) # ArrayIndexOutOfBoundsException
                    dest.operands[0].setType('int')
                    _exp_kind = (prob(80) ? 'scalar' : $conf.exp_kind.getRand(['scalar', 'array']))
                    val  = Expression.new(self, 'int', 1, _exp_kind)
                end
                @aioob_assn = Expression.new(self, 'int', 0, 'assign', {'op'=>Operator.get('=', nil, 'integral_assn'), 'vals'=>[dest, val]})
            end

        end

        if @step < 0
            tmp = @initVal
            @initVal = @maxVal
            @maxVal = tmp
        end

        compare_with = ((@maxVal.instance_of?(Var) or @maxVal.instance_of?(ExpBinOper) or @maxVal.instance_of?(Expression)) ? @maxVal  : @maxVal.to_s )        
        if (prob(80))
            # "normal" condition: i = 1; i < 70; i++
            @condition = ExpBinOper.new(self, @inductionVar, cond,  compare_with)
        else # condition: i = 1; 70 > i; i++ 
            if (cond == "<")
                cond = ">"
            elsif (cond ==">")
                cond = "<"
            end
            @condition = ExpBinOper.new(self, compare_with, cond, @inductionVar)
        end
        @nestedStmts["body"] = genStmtSeq($conf.max_loop_stmts, false) # at least 1 stmt

    end

    #------------------------------------------------------
    def gen
        
        res=""
        unknown_zero_assn_s=""
        ivar = @inductionVar.gen()
                
        if @p_unknown_loop_limit
            unknown_zero_assn_s = @unknown_zero_assn.gen()
            if ((@initVal.instance_of?(Var) or @initVal.instance_of?(ExpBinOper)))# and typeGT?(@initVal.type, @inductionVar.type))
                init = @initVal.gen() 
                init = "(" + @inductionVar.type + ")"  + "(" + init + ")"
            else
                init = @initVal.to_s
            end
            res  = res + ln(unknown_zero_assn_s + " ; ")
        elsif (@initVal.instance_of?(Var) or @initVal.instance_of?(ExpBinOper))
            init = @initVal.gen()
            init = "(" + @inductionVar.type + ")" + "(" + init + ")" if typeGT?(@initVal.type, @inductionVar.type)
        else
            init = @initVal.to_s 
        end
        inc  = ((@step ==  1) ? "++" : " += " +    @step.to_s) if @step > 0
        inc  = ((@step == -1) ? "--" : " -= " + (-@step).to_s) if @step < 0
        res1 = "for (" + ivar + " = " + init + "; "
        res1 += @condition.gen()
        res1 += "; "
        res1  = ln(res1 + ((((inc == '++') or (inc == '--')) and prob(50)) ? inc + ivar : ivar + inc) + ") {")
        shift(1)
        res1 += @context.genDeclarations() # Experimental
        if (@aioob_assn != nil)
            res1 += AssignmentStmt.new(@context, self, @aioob_assn).gen()
            if (prob(50)) # sometimes we want no other statements to be inside this loop except for this array assignment that will trow AIOOB
                shift(-1)
                return res + res1 + ln("}")
            end

        end
        res1 += @nestedStmts["body"].collect{|st| st.gen()}.join()
        shift(-1)
        res + res1 + ln("}")
    end

    #------------------------------------------------------
    # returns list of loop induction vars in stmt hierarchy up from the given stmt
    def ForLoopStmt.inductionVarsList(stmt)
        res = []
        while (stmt)
            res << stmt.inductionVar if stmt.instance_of?(ForLoopStmt) or stmt.instance_of?(WhileDoStmt) #or stmt.instance_of?(EnhancedForStmt)
            stmt = stmt.parent
        end
        res
        end

    def ForLoopStmt.isInsideTryCatchArrayIndexOutOfBoundsException(stmt)
        res = false

        while (stmt)
            if (stmt.instance_of?(TryStmt) and stmt.exception == "ArrayIndexOutOfBoundsException")
                return true
            end
            stmt = stmt.parent
        end
        res
        end
#------------------------------------------------------
# returns random max value for a For or While or Do loop
def ForLoopStmt.indVarMaxValue(par_stmt, indVars=nil, this_loop=nil)
    outerIterationsCount = outerIterations(par_stmt)
    if !this_loop.inMainTest # we need to limit the number of iterations in mrthods that might be invoked in long running loops 
        maxNestedSize = $conf.max_nested_size_not_mainTest
    else 
        maxNestedSize = $conf.max_nested_size
    end
        
    indVars = ForLoopStmt.inductionVarsList(par_stmt) unless indVars
    if (indVars.size > 0 and prob($conf.p_triang)) # in case we use induction var of outer loop we might end up with $conf.max_size
        if outerIterationsCount * $conf.max_size < maxNestedSize
            res  = wrand(indVars)
        else # we are close to or exceed maxNestedSize, should limit the number of iterations
            res = (maxNestedSize / outerIterationsCount).to_i + 1;
        end
            
    elsif indVars.size == 0 && outerIterationsCount == 1# this is top loop
        res = mrand($conf.max_size - 2 - ($conf.max_size * $conf.min_size_fraction).to_i) + ($conf.max_size * $conf.min_size_fraction).to_i + 1
    else
        if outerIterationsCount * $conf.max_size < maxNestedSize
            res = mrand($conf.max_size - 2 - ($conf.max_size * $conf.min_size_fraction).to_i) + ($conf.max_size * $conf.min_size_fraction).to_i + 1
        else # we are close to or exceed maxNestedSize, should limit the number of iterations
            res = (maxNestedSize / outerIterationsCount).to_i + 1;
        end

    end
    res
    end

#returns the number of iterations in outer loops
def ForLoopStmt.outerIterations(par_stmt)
        count = 1
        stmt = par_stmt
        while (stmt)
            if (stmt.instance_of?(ForLoopStmt) || stmt.instance_of?(WhileDoStmt) || stmt.instance_of?(EnhancedForStmt))
                indVars = ForLoopStmt.inductionVarsList(stmt)
                if stmt.instance_of?(EnhancedForStmt)
                    count *= $conf.max_size
                else #For,While)
                    if (stmt.maxVal.instance_of?(Var) or stmt.initVal.instance_of?(Var) or stmt.maxVal.instance_of?(ExpBinOper) or stmt.initVal.instance_of?(ExpBinOper) or (stmt.instance_of?(ForLoopStmt) and stmt.p_unknown_loop_limit)) # lower or upper bound is unknown
                        count *= $conf.max_size
                    else 
                        tmp = ((stmt.maxVal - stmt.initVal) / stmt.step).to_i.abs
                        count *= (tmp > 1 ? tmp : 1)
                    end
                end
            end
            stmt = stmt.parent
        end
        count
        end


end

#===============================================================================
# WHILE and DO statements
class WhileDoStmt < Statement
    attr_reader :inductionVar      # induction var of this loop
    attr_reader :maxVal      # maxVal of induction var of this loop
    attr_reader :initVal      # initVal of induction var of this loop
    attr_reader :step     # step of induction var of this loop
    attr_reader :inMainTest # true if loop is created in mainTest method

    def initialize(cont, par)
        #dputs("While loop creation!",true,10)
        super(cont, par, true, true)
        if (stmtsRemainder() < 2 or loopNesting() >= $conf.max_loop_depth)
            @emptyFlag = true
            return
        end
        @inMainTest = cont.method.mainTestFlag
        @context = Context.new(cont, Con::STMT, cont.method) #Experimental
        @kind = prob(50) ? 'while' : 'do'
        @inductionVar = Var.new(@context, $conf.p_ind_var_type.getRand())
        @inductionVar.inductionVarFlag = true
        @step = $conf.for_step.getRand()
        @maxVal = ForLoopStmt.indVarMaxValue(@parent, nil, self)
        @initVal = @inductionVar.init_value
        @nestedStmts["body"] = genStmtSeq($conf.max_loop_stmts, false) # at least 1 stmt
    end

    def gen
        if @maxVal.instance_of?(Var)
            max = @maxVal.gen()
            max = "(" + @inductionVar.type + ")" + max if typeGT?(@maxVal.type, @inductionVar.type)
        else
            max = @maxVal.to_s
        end
        ivar = @inductionVar.gen()
        @initVal = @inductionVar.init_value
        inc  = (@step ==  1) ? "++" + ivar : "(" + ivar + " += " +    @step.to_s + ")" if @step > 0
        inc  = (@step == -1) ? "--" + ivar : "(" + ivar + " -= " + (-@step).to_s + ")" if @step < 0
        cond = @step > 0 ? " < " + max : " > 0"
        res  = ln(ivar + " = " + (@step > 0 ? '1' : max) + ";")
        res += ln((@kind == 'do' ? "do" : "while (" + inc + cond + ")") + " {")
        shift(1)
        res += @context.genDeclarations() # Experimental
        res += @nestedStmts["body"].collect{|st| st.gen()}.join()
        shift(-1)
        return res + ln("}") if @kind == 'while'
        res + ln("} while (" + inc + cond + ");")
    end
end

#===============================================================================
# Enhanced FOR statement
class EnhancedForStmt < Statement
    attr_reader :targetArr
    attr_reader :inMainTest # true if loop is created in mainTest method
    attr_reader :inductionVar # inductionVar

    def initialize(cont, par)
        #dputs("Enhanced For loop creation!",true,10)
        super(cont, par, true, true)
# since we can't limit enhanced for size easily, we will not generate it if iterations count is too large
        @inMainTest = cont.method.mainTestFlag
        @context = Context.new(cont, Con::STMT, cont.method)
        @inductionVar = Var.new(@context, $conf.types.getRand(TSET_ARITH), Nam::LOC)
#      @inductionVar.inductionVarFlag = true
        @targetArr = @context.getArr(100, @inductionVar.type, 1, false, nil, true)
        if (stmtsRemainder() < 2 or loopNesting() >= $conf.max_loop_depth or (!@inMainTest and ForLoopStmt.outerIterations(par)*$conf.max_size > $conf.max_nested_size_not_mainTest) or (@inMainTest and ForLoopStmt.outerIterations(par)*$conf.max_size > $conf.max_nested_size) )
            
            @emptyFlag = true
            return
        end
        @nestedStmts["body"] = genStmtSeq($conf.max_loop_stmts, false) # at least 1 stmt
    end

    def gen
        res = ln("for (" + @inductionVar.type + " " + @inductionVar.gen() + " : " + @targetArr.name + ") {")
        shift(1)
        res += @context.genDeclarations() # Experimental
        res += @nestedStmts["body"].collect{|st| st.gen()}.join()
        shift(-1)
        res + ln("}")
    end
end
