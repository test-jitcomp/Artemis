# Copyright (C) 2016 Intel Corporation
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
#  Code for provoking vectorization optimizations
#
# Authors: Mohammad R. Haghighat, Dmitry Khukhro, Andrey Yakovlev
#==========================================================

#===============================================================================
# Vectorizable statement - assignment that vectorization optimization can be applied to
# Templates (where i is induction variable):
# n += i
# n += i + <scalar>
# n += i * <scalar>
# n += i | <scalar>
# n += i - <scalar>
# n += i ^ <scalar>
# n += i * c1 + c2 - c3
# n += <literal> + i * i (also strength reduction is applicable)
# Reduced (non-)accumulated output?
# arr.elem in left or right part?
class VectStmt < AssignmentStmt

    def initialize(cont, par)
        ivars = ForLoopStmt.inductionVarsList(par)
        if ivars.empty?() # no induction vars
            @emptyFlag = true
            return
        end
        super(cont, par, 0) # create an instance w/o any expression
        indVar = ivars[0] # induction var of nearest loop
        assnOp = Operator.get("+=")
        left = Expression.new(self, $conf.types.getRand(TSET_INTEGRAL + ['float']), 1, 'scalar', nil, Exp::DEST)
        case rand(4)
        when 0 # n += i
            right = ExpScal.new(self, indVar, 1)
        when 1 # n += i <op> <scalar>
            op = Operator.get(wrand(['+', '-', '*', '|', '^']), 'infix',nil,left.resType)
            op = Operator.get(wrand(['+', '-', '*', '|', '^']), 'infix') if !op
            scal = Expression.new(self, $conf.types.getRand(TSET_INTEGRAL + ['float']), 2, 'scalar')
            right = ExpBinOper.new(self, indVar, op, scal, 1)
        when 2 # n += i * c1 + c2 - c3
            scal1 = Expression.new(self, $conf.types.getRand(TSET_INTEGRAL + ['float']), 4, 'scalar')
            scal2 = Expression.new(self, $conf.types.getRand(TSET_INTEGRAL + ['float']), 3, 'scalar')
            scal3 = Expression.new(self, $conf.types.getRand(TSET_INTEGRAL + ['float']), 2, 'scalar')
            right = ExpBinOper.new(self, indVar, '*', scal1, 3)
            right = ExpBinOper.new(self, right, '+', scal2, 2)
            right = ExpBinOper.new(self, right, '-', scal3, 1)
        when 3 # n += [<literal> +] i * i
            right = ExpBinOper.new(self, indVar, '*', indVar, 2)
            if prob(50)
                lit = Expression.new(self, $conf.types.getRand(TSET_INTEGRAL + ['float']), 2, 'literal')
                right = ExpBinOper.new(self, lit, '+', right, 2)
            end
        else
            error("VectStmt.initialize: unexpected value", true)
        end
        assignment = Expression.new(self, left.resType, 0, 'assign', {'op'=>assnOp, 'vals'=>[left, right]})
        super(cont, par, assignment)
    end
end
