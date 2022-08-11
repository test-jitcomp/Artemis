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
#==========================================================

#----------------------------------------------------------
# Java* Fuzzer test generator
#
# Modifications 2017-2018: Nina Rinskaya (Azul Systems)
#----------------------------------------------------------
#

# IF statement
class IfStmt < Statement

    def initialize(cont, par)
        super(cont, par, true)
        if (stmtsRemainder() < 2)
            @emptyFlag = true
            return
        end
        @nestedStmts["if"] = genStmtSeq($conf.max_if_stmts)
        wrand([0,0,0,0,1,1,2]).times { |i| # additional if's
            @nestedStmts["elif"+i.to_s] = genStmtSeq($conf.max_if_stmts)
        }
        @conds = Hash.new()
        @nestedStmts.keys.each do |part|
            @conds[part] = Expression.new(self, "boolean")
        end
        @nestedStmts["else"] = genStmtSeq($conf.max_el_stmts) if prob($conf.p_else)
        retNum = 0
        @nestedStmts.keys.each do |key| # add return
            if !(ret = ReturnStmt.new(cont, self, false)).emptyFlag
                retNum += 1
                @nestedStmts[key] << ret
            end
        end
        @nestedStmts["if"].pop if @nestedStmts["else"] and retNum >= @nestedStmts.keys.size
    end

    def gen
        res = ""
        (["if"] + @nestedStmts.keys.grep(/elif/).sort + @nestedStmts.keys.grep(/else/)).each do |key|
            kw = (key[/elif/] ? "else if" : key)
            res += ln((key == "if" ? "" : "} ") + kw + (key == "else" ? "" : " (" + @conds[key].gen() + ")") + " {")
            shift(1)
            res += @nestedStmts[key].collect{|st| st.gen()}.join()
            shift(-1)
        end
        res + ln("}")
    end
end

#===============================================================================
# SWITCH statement
# - Packed or sparse (random)
# - order of cases
# - <=64 or >64 + switch value = induction var
# - break or no break
# - default or no default
class SwitchStmt < Statement

    def initialize(cont, par)
        super(cont, par, true, false)
        if (stmtsRemainder() < 3)
            @emptyFlag = true
            return
        end
        @needDefault = prob(50)
        iVarsList = ForLoopStmt.inductionVarsList(@parent)
        caseBig = ((!iVarsList.empty?) and prob($conf.p_big_switch))
        packed = prob($conf.p_packed_switch)
        step = packed ? 1 : 5
        minValue = mrand(128)
        @caseCount = caseBig ? 70 : wrand([1,1,2,2,2,3,4,5,6,7,8,9,10])
        totalAlts = @caseCount + (@needDefault ? 1 : 0)
        @caseValues = []
        if packed
            @caseCount.times {|i| @caseValues << (minValue + i)}
        else
            vals = (1..@caseCount * step).to_a
            @caseCount.times {|i|
                val = wrand(vals)
                @caseValues << (minValue + val)
                vals -= [val]
            }
        end
        if caseBig or (!iVarsList.empty? and prob(70))
            value = ExpScal.new(self, wrand(iVarsList), 1)
            if !caseBig
                value = Expression.new(self, value.resType, 1, 'oper', {'op'=>Operator.get('%'), 'vals'=>[value,
                    ExpIntLit.new(self, @caseCount.to_s, 2)]})
            end
        else
            value = Expression.indExp(self, 1, @caseCount.to_s)
        end
        if !packed
            value = Expression.new(self, value.resType, 2, 'oper', {'op'=>Operator.get('*'), 'vals'=>[value,
                ExpIntLit.new(self, step.to_s, 3)]})
        end
        if minValue > 0
            value = Expression.new(self, value.resType, 2, 'oper', {'op'=>Operator.get('+', 'infix'), 'vals'=>[value,
                ExpIntLit.new(self, minValue.to_s, 3)]})
        end
        @valueExpr = value
        numBreaks = 0
        @caseCount.times {|i|
            @nestedStmts[i.to_s] = (prob($conf.p_switch_empty_case) ? [] : genStmtSeq($conf.max_if_stmts))
            if (prob(75) and @nestedStmts[i.to_s].size > 0)
                @nestedStmts[i.to_s] << BreakStmt.new(cont, self, false)
                numBreaks += 1
            end
        }
        @nestedStmts["default"] = genStmtSeq($conf.max_el_stmts) if @needDefault
    end

    def gen
        val = typeGT?(@valueExpr.resType, "int") ? "(int)(" + @valueExpr.gen() + ")" : @valueExpr.gen()
        res = ln("switch (" + val + ") {")
        @caseValues.each_index {|i|
            res += ln("case " + @caseValues[i].to_s + ":")
            shift(1)
            res += @nestedStmts[i.to_s].collect{|st| st.gen()}.join()
            shift(-1)

        }
        if @needDefault
            res += ln("default:")
            shift(1)
            res += @nestedStmts["default"].collect{|st| st.gen()}.join()
            shift(-1)
        end
        res + ln("}")
    end
end

#===============================================================================
# CONTINUE statement inside an if statement
class ContinueStmt < Statement

    def initialize(cont, par)
        super(cont, par, false, false)
        if (loopNesting() == 0)
            @emptyFlag = true
            return
        end
        @condExpr = Expression.new(self, "boolean")
    end

    def gen
        ln("if (" + @condExpr.gen() + ") continue;")
    end
end

#===============================================================================
# BREAK statement inside an if statement
class BreakStmt < Statement

    def initialize(cont, par, underIf=true)
        super(cont, par, false, false)
        if (loopNesting() == 0 and !withinSwitch?())
            @emptyFlag = true
            return
        end
        @condExpr = nil
        @condExpr = Expression.new(self, "boolean") if underIf
    end

    def withinSwitch?
        return true if @parent and @parent.instance_of?(SwitchStmt)
        @parent and @parent.withinSwitch?()
    end

    def gen
        cond = ""
        cond = "if (" + @condExpr.gen() + ") " unless @condExpr.nil?
        ln(cond + "break;")
    end
end
