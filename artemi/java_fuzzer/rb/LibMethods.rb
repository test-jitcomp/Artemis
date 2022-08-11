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
#  Library method invocations
#
# Authors: Mohammad R. Haghighat, Dmitry Khukhro, Andrey Yakovlev
#==========================================================

class LibMeth
    attr_reader :name, :type, :argTypes, :import, :objType

    def initialize(name, type, argTypes=nil, import=nil, objType=nil)
        @name     = name
        @type     = type
        @argTypes = argTypes
        @import   = import
        @objType  = objType
    end
end # class LibMeth

#------------------------------------------------------
def defLibMethods
    $libMethList = [
        LibMeth.new("Math.abs", "int", ["int"]),
        LibMeth.new("Math.abs", "long", ["long"]),
        LibMeth.new("Math.abs", "float", ["float"]),
        LibMeth.new("Math.abs", "double", ["double"]),
        LibMeth.new("Math.max", "int", ["int", "int"]),
        LibMeth.new("Math.max", "long", ["long", "long"]),
        LibMeth.new("Math.min", "int", ["int", "int"]),
        LibMeth.new("Math.min", "long", ["long", "long"]),
        LibMeth.new("Math.sqrt", "double", ["double"]),
        #        LibMeth.new("Double.doubleToRawLongBits", "long", ["double"]), # problem with raw bits for NaN
        LibMeth.new("Double.longBitsToDouble", "double", ["long"]),
        #        LibMeth.new("Float.floatToRawIntBits", "int", ["float"]),
        LibMeth.new("Float.intBitsToFloat", "float", ["int"]),
        LibMeth.new("Integer.reverseBytes", "int", ["int"]),
        LibMeth.new("Long.reverseBytes", "long", ["long"]),
        LibMeth.new("Short.reverseBytes", "short", ["short"])
    ]
end

#===============================================================================
# lib method invocation expression
class ExpLibInvoc < Expression
    attr_reader :method

    # invocation of a method; type=nil means any type but void
    def initialize(stmt, type, depth)
        #return if loopDepth(stmt) > 1
        @method = wrand($libMethList.find_all {|meth| meth.type == type})
        return unless @method
        vals = []
        @method.argTypes.each {|t|
            vals << Expression.new(stmt, t, depth+1, nil, nil, Exp::CAST)
        }
        super(stmt, @method.type, depth, 'libinvoc', {'op'=>nil, 'vals'=>vals})
    end

    #------------------------------------------------------
    def gen
        @method.import.each {|imp| $imports[imp] += 1} if @method.import
        res = @method.name + "("
        @operands.each {|exp| res += exp.gen() + ", "}
        res = res[0..-3] if @operands.size > 0
        res + ")"
    end
end

#===============================================================================
# lib method invocation statement
class LibInvocStmt < Statement

    def initialize(cont, par, expr=nil)
        super(cont, par)
        if (expr)
            expr.parentStmt = self
            @invocExpr = expr
        else
            @invocExpr = ExpLibInvoc.new(self, (prob(99) ? "void" : nil), 0)
        end
        @emptyFlag = true unless @invocExpr.kind == 'libinvoc'
    end

    def gen
        ln(@invocExpr.gen() + ";")
    end
end
