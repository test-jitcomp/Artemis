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
#  Classes for Java statements representation
#
# Authors: Mohammad R. Haghighat, Dmitry Khukhro, Andrey Yakovlev
#----------------------------------------------------------

#----------------------------------------------------------
# Java* Fuzzer test generator
#
# Modifications 2017-2018: Nina Rinskaya (Azul Systems)
#----------------------------------------------------------

OP_LIST = []

def defOperators
    $conf.operators.keys.each {|category|
        $conf.operators[category].values.each {|op|
            if category == 'relational'
                OP_LIST << Operator.new(op, category, TSET_BOOL,     'infix',   TSET_ARITH, TSET_ARITH)
            elsif op == '!'
                OP_LIST << Operator.new(op, category, TSET_BOOL,     'prefix',  TSET_BOOL)
            elsif category == 'boolean'
                OP_LIST << Operator.new(op, category, TSET_BOOL,     'infix',   TSET_BOOL, TSET_BOOL)
            elsif op == '~'
                OP_LIST << Operator.new(op, category, TSET_INTEGRAL, 'prefix',  TSET_INTEGRAL)
            elsif category == 'integral'
                OP_LIST << Operator.new(op, category, TSET_INTEGRAL, 'infix',   TSET_INTEGRAL, TSET_INTEGRAL)
            elsif category == 'arith'
                OP_LIST << Operator.new(op, category, TSET_ARITH,    'infix',   TSET_ARITH, TSET_ARITH)
            elsif category == 'uarith'
                OP_LIST << Operator.new(op, category, TSET_ARITH,    'prefix',  TSET_ARITH)
            elsif category == 'indecrem_pre'
                OP_LIST << Operator.new(op, category, TSET_ARITH,    'prefix',  TSET_ARITH_VAR)
            elsif category == 'indecrem_post'
                OP_LIST << Operator.new(op, category, TSET_ARITH,    'postfix', TSET_ARITH_VAR)
            elsif category == 'boolean_assn'
                OP_LIST << Operator.new(op, category, TSET_BOOL,     'infix',   TSET_BOOL_VAR, TSET_BOOL)
            elsif category == 'integral_assn'
                OP_LIST << Operator.new(op, category, TSET_INTEGRAL, 'infix',   TSET_INTEGRAL_VAR, TSET_INTEGRAL)
            elsif category == 'arith_assn'
                OP_LIST << Operator.new(op, category, TSET_ARITH,    'infix',   TSET_ARITH_VAR, TSET_ARITH)
            elsif category == 'object_assn'
                OP_LIST << Operator.new(op, category, TSET_OBJECT,   'infix',   TSET_OBJECT_VAR, TSET_OBJECT)
            elsif category == 'array_assn'
                OP_LIST << Operator.new(op, category, TSET_ARRAY,   'infix',   TSET_ARRAY_VAR, TSET_ARRAY)
            else
                error("defOperators: unknown category", true)
            end
        }
    }
end

#===============================================================================
# Operator
class Operator
    attr_reader :sign, :category, :resTypeSet, :kind, :operand1TypeSet, :operand2TypeSet

    def initialize(str, cat, resTypeSet, kind, op1TypeSet, op2TypeSet=nil)
        @sign = str
        @category = cat
        @resTypeSet = resTypeSet
        @kind = kind
        @operand1TypeSet = op1TypeSet
        @operand2TypeSet = op2TypeSet
    end

    def Operator.get(sign, kind=nil, cat=nil, type=nil)
        OP_LIST.detect {|op| op.sign == sign and (kind ? op.kind == kind : true) and (cat ? op.category == cat : true) and (type ? op.operand1TypeSet.member?(type) : true)}
    end
end

module Exp # expression attributes
    DEST  = 1  # destination in assignment
    FINAL = 2  # no changes are allowed
    CAST  = 4  # cast to given type
    AIND  = 8  # arbitrary index in array element
    NOTNULL = 512 # expression cannot be null
end

#===============================================================================
# expression
class Expression
    attr_accessor :parentStmt, :className
    attr_reader :kind, :flags, :operands, :resType, :type

    def flag(val)
        (@flags & val) != 0
    end

    def setFlag(val)
        @flags |= val
    end

    def setResType(val)
        @resType=val
    end

    def setType(val)
        @type=val
    end

    def transfer(expr, op, depth)
        if !expr.flag(Exp::FINAL) and op and ['/', '%', '/=', '%='].member?(op.sign) and
        (expr.kind != 'literal' or !expr.gen()[/[1-9]/]) and
        !TryStmt.isCaught?("ArithmeticException", @parentStmt)
            oper=Operator.get('|', 'infix','integral', 'long')
            expr1=expr.dup
            opers=[expr1,Expression.new(@parentStmt, 'long', depth, 'scalar', {'op'=>nil, 'vals'=>['1']}, 0)]
            expr2=Expression.new(@parentStmt, 'long', depth, 'oper', {'op'=>oper, 'vals'=>opers, 'rt'=>'long'}, 0)#Exp::CAST)
            expr2.operands[0].setType('long')
            expr2.operands[0].setFlag(Exp::CAST)
            return expr2
        end
        if op and ['/', '%', '/=', '%='].member?(op.sign) and
            expr.kind == 'literal' and
            (expr.type == 'int' or expr.type == 'long' or expr.type == 'short')
            !TryStmt.isCaught?("ArithmeticException", @parentStmt)
            a = expr.gen
            if a.to_i.to_s(2)[-[a.to_i.to_s(2).length,8].min..-1].to_i==0
               expr.operands[0] = (a.to_i+1).to_s + (expr.type=='long' ? 'L' : '')
               puts "// CONVERTED "+a.to_s+" to "+expr.operands[0]
            end
        end
        return expr
    end

    #------------------------------------------------------
    def initialize(stmt, type=nil, depth=0, kind=nil, elements=nil, flags=0, className=nil)
        @parentStmt = stmt
        @context = stmt.context
        excl_types=nil
        excl_types=['Object'] if $globalContext.classList.size()>=$conf.max_classes
        @type = (type ? type : $conf.types.getRand(nil,excl_types))
        @kind = kind
        @flags = flags
        notnullflag=(flag?(Exp::NOTNULL) ? Exp::NOTNULL : 0)
        @resType = @type
        if (elements) # expression already defined
            @operands = elements['vals']
            @operator = elements['op']
            @resType  = elements['rt'] if elements['rt']
            return
        end
        @operands = []
        @operator = nil
        @className = className
        @kind = $conf.exp_kind.getRand((depth > $conf.max_exp_depth or $globalContext.getCallersHashDepth() >=$conf.max_callers_chain ) ?
        ['literal', 'scalar', 'field'] : nil,excl()) unless kind
        if ['invoc', 'libinvoc'].member?(@kind)
            inv = (@kind == 'invoc' ? ExpInvocation : ExpLibInvoc).new(@parentStmt, @type, depth)
            if inv.method  # method for invocation found
                @operands << inv
                return
            else  # no method of this type and max count of methods is reached
                @kind = 'scalar'
            end
        end
        if @type=='Array' #(Sub)array assignment
            arrndim = wrand([1,1,1, rand($conf.max_arr_dim)+1]) if @className.nil?
            if prob($conf.p_big_array) and arrndim==0
                arrsize = rand($conf.max_big_array)+$conf.min_big_array
            else
                arrsize=0
            end
            @className=ArrCh.new($conf.types.getRand(nil,['Array']),arrndim,arrsize) if @className.nil?
        end
        case @kind
        when 'literal'
            @operands << rLiteral(@type,@className,@context,flag?(Exp::NOTNULL))
            @resType = 'int' if ['short', 'byte'].member?(@type)
        when 'scalar'
            @operands << @context.getVar(@parentStmt.pVarReuse, @type, flag?(Exp::DEST), @className, flag?(Exp::NOTNULL)) if type!='Array'
            if type=='Array'
                @operands << @context.getArr(@parentStmt.pVarReuse, @type, 0, flag?(Exp::NOTNULL), @className)
                if @className.dim<@operands[0].ndim
                    (@operands[0].ndim-@className.dim).times{ |i| @operands<<singleArrElem(depth)}
                end
            end
            @className = @operands[0].className if (@type=='Object') and @className.nil?
            @flags=@flags|Exp::NOTNULL if @operands[0].flag?(Nam::NOTNULL)
            notnullflag=(flag?(Exp::NOTNULL) ? Exp::NOTNULL : 0)
        when 'arrname'
            @operands << @context.getArr(@parentStmt.pVarReuse, @type, 0, flag?(Exp::NOTNULL))
        when 'array'
            arrElem(depth)
            @flags=@flags|Exp::NOTNULL if @operator.flag?(Nam::NOTNULL)
            notnullflag=(flag?(Exp::NOTNULL) ? Exp::NOTNULL : 0)
        when 'oper'
            operResult(depth)
        when 'assign'
            opCat = $conf.op_cats.getRand(OP_TYPES[@type]['assn'])
            @operator = Operator.get($conf.operators[opCat].getRand(), nil, opCat)
            @operands << Expression.new(@parentStmt, @type, depth+1, $conf.exp_kind.getRand(['scalar', 'array'],excl()), nil, Exp::DEST|notnullflag, @className)
            @flags=@flags|Exp::NOTNULL if @operands[0].flag?(Exp::NOTNULL)
            notnullflag=(flag?(Exp::NOTNULL) ? Exp::NOTNULL : 0)
            @className = @operands[0].className if @type=='Object' and @className.nil?
            @operands << Expression.new(@parentStmt, $conf.types.getRand(@operator.operand2TypeSet,excl()), depth+1, nil, nil, notnullflag, @className)
            @operands[1] = transfer(@operands[1],@operator,depth+1) if ['/', '%', '/=', '%='].member?(@operator.sign)
        when 'cond'
        when 'inlinvoc'
        else
            error("Expression.new: kind = " + @kind, true)
        end
    end

    #------------------------------------------------------
    # Form an excluding array ['array'] if class is set
    def excl
        ret=[]
        if !@className.nil?
            ret<<'array'
            ret<<'invoc'
            ret<<'libinvoc'
        end
        if @type=='Array'
            ret<<'array'
            ret<<'invoc'
            ret<<'libinvoc'
        end
        return ret
    end

    #------------------------------------------------------
    # form applying an operator to its operands
    def operResult(depth)
        notnullflag=(flag?(Exp::NOTNULL) ? Exp::NOTNULL : 0)
        opCat = $conf.op_cats.getRand(OP_TYPES[@type]['oper'] + OP_TYPES[@type]['assn'])
        @operator = Operator.get($conf.operators[opCat].getRand(), nil, opCat)
        if ['boolean_var', 'integral_var', 'arith_var', 'object_var', 'array_var'].member?(@operator.operand1TypeSet[0])
            @operands << Expression.new(@parentStmt, @type, depth+1, $conf.exp_kind.getRand(['scalar', 'array'],excl()), nil, Exp::DEST|notnullflag, @className)
        else
            @operands << Expression.new(@parentStmt, $conf.types.getRand(@operator.operand1TypeSet), depth+1,
            @operator.kind == 'prefix' ? $conf.exp_kind.getRand(nil, ['literal']+excl()) : nil, nil, notnullflag, @className)
        end
        @flags=@flags|Exp::NOTNULL if operands[0].flag?(Exp::NOTNULL)
        notnullflag=(flag?(Exp::NOTNULL) ? Exp::NOTNULL : 0)
        @resType = @operands[0].resType if TSET_ARITH.member?(@type)
        @className = @operands[0].className if @type=='Object' and @className.nil?
        if @operator.kind == 'infix'
            @operands << Expression.new(@parentStmt, $conf.types.getRand(@operator.operand2TypeSet), depth+1,
            @operands[0].kind == 'literal' ? $conf.exp_kind.getRand(nil, ['literal']+excl()) : nil, nil, notnullflag, @className)
            @resType = @operands[1].resType if TSET_ARITH.member?(@type) and typeGT?(@operands[1].resType, @resType)
            @operands[1] = transfer(@operands[1],@operator,depth+1) if ['/', '%', '/=', '%='].member?(@operator.sign)#############
        end
        # float and double will be casted to long for integral operators:
        @resType = 'long' unless @operator.resTypeSet.member?(@resType)
        @resType = 'int' if TSET_ARITH.member?(@resType) and typeGT?('int', @resType)
    end

    #------------------------------------------------------
    # form index expression
    def Expression.indExp(stmt, depth, divisor)
        Expression.new(stmt, 'int', depth, 'oper',
        {'op'=>Operator.get('%'), 'vals'=>[Expression.new(stmt, 'int', depth+1, 'oper',
            {'op'=>Operator.get('>>>'), 'vals'=>[Expression.new(stmt, 'int', depth+2, nil, nil,
            Exp::CAST|Exp::FINAL),
            ExpIntLit.new(stmt, '1', depth)]}),
            ExpScal.new(stmt, divisor, depth, Exp::FINAL)]})
    end

    #------------------------------------------------------
    # form array element
    def arrElem(depth)
        @operator = @context.getArr(@parentStmt.pVarReuse, @type, 0, flag?(Exp::NOTNULL))
        iVarsList = ForLoopStmt.inductionVarsList(@parentStmt)
        @operator.ndim.times {|i|
            if flag(Exp::AIND)
                @operands << Expression.new(@parentStmt, 'int', depth+1, nil, nil, Exp::CAST)
                next
            end
            if @operator.type == 'Object' and @operator.size != 0
                @operands << Expression.indExp(@parentStmt, depth+1, @operator.size.to_s)
                next
            end
            if (indKind = $conf.ind_kinds.getRand()) == 'any' or iVarsList.empty?
                @operands << Expression.indExp(@parentStmt, depth+1, MAX_TRIPNM)
                next
            end
            ind_var = wrand(iVarsList)
            if indKind == '0'
                kind = 'scalar'
                op = nil
                vals = [ind_var]
            else
                kind = 'oper'
                op = Operator.get(indKind == '-1' ? '-' : '+', 'infix')
                vals = [ExpScal.new(@parentStmt, ind_var, depth+1),
                    ExpIntLit.new(@parentStmt, '1', depth+1)]
            end
            rtype = typeGT?(ind_var.type, 'int') ? ind_var.type : 'int'
            @operands << Expression.new(@parentStmt, 'int', depth+1, kind, {'op'=>op, 'vals'=>vals, 'rt'=>rtype}, Exp::CAST)
        }
    end

    # form single index expression
    def singleArrElem(depth)
        iVarsList = ForLoopStmt.inductionVarsList(@parentStmt)
        if flag(Exp::AIND)
            ret=Expression.new(@parentStmt, 'int', depth+1, nil, nil, Exp::CAST)
            #dputs "Generated a single index "+(ret.instance_of?(String) ? "str! "+ret : ret.gen()+" type = "+ret.type+"; resType = "+ret.resType)
            return ret
        end
        if (indKind = $conf.ind_kinds.getRand()) == 'any' or iVarsList.empty?
            if @type == 'Object' # and @size != 0
                ret = Expression.indExp(@parentStmt, depth+1, @size.to_s)
            else
                ret=Expression.indExp(@parentStmt, depth+1, MAX_TRIPNM)
            end
            #dputs "Generated a single index "+(ret.instance_of?(String) ? "str! "+ret : ret.gen()+" type = "+ret.type+"; resType = "+ret.resType)
            return ret
        end
        ind_var = wrand(iVarsList)
        if indKind == '0'
            kind = 'scalar'
            op = nil
            vals = [ind_var]
        else
            kind = 'oper'
            op = Operator.get(indKind == '-1' ? '-' : '+', 'infix')
            vals = [ExpScal.new(@parentStmt, ind_var, depth+1),
                ExpIntLit.new(@parentStmt, '1', depth+1)]
        end
        rtype = typeGT?(ind_var.type, 'int') ? ind_var.type : 'int'
        ret=Expression.new(@parentStmt, 'int', depth+1, kind, {'op'=>op, 'vals'=>vals, 'rt'=>rtype}, Exp::CAST)
        #dputs "Generated a single index "+(ret.instance_of?(String) ? "str! "+ret : ret.gen()+" type = "+ret.type+"; resType = "+ret.resType)+"; indKind = "+indKind.to_s
        return ret
    end

    #------------------------------------------------------
    def genVal(expr, op=nil)
        res = expr.gen()
        res = "(" + res + ")" if ['oper', 'assign', 'cond'].member?(expr.kind)
        return res ##############
        return res unless !expr.flag(Exp::FINAL) and op and ['/', '%', '/=', '%='].member?(op.sign) and
        (expr.kind != 'literal' or !res[/[1-9]/]) and
        !TryStmt.isCaught?("ArithmeticException", @parentStmt)
        typeConv = TSET_INTEGRAL.member?(expr.resType) ? "" : "(long)"
        "(" + typeConv + res + " | 1)"
    end

    #------------------------------------------------------
    # returns fixed numeric literal in case it is a value in /= or %= assignments
    # and it is cast to zero. Otherwise, returns identical expression
    def properNumeric(ops)
        return ops[1] unless ops[1].kind == 'literal' and ['float','double'].member?(ops[1].resType) and
        TSET_INTEGRAL.member?(ops[0].resType) and ops[1].operands[0][/^-?0\./]
        ops[1].operands[0].gsub!(/0\./, '1.')
        #return ops[1]
    end

    #------------------------------------------------------
    # generate string representation of this expression
    def gen
        res = ""
        case @kind
        when 'literal'
            res = @operands[0]
        when 'scalar', 'arrname'
            if @className.nil? or @type!='Array'
                res = (@operands[0].instance_of?(String) ? @operands[0] : @operands[0].gen())
            else
                res=@operands[0].gen()
                #@operands[1..-1].each{ |op| res+="["+(typeGT?(op.resType,op.type) ? "(int)" : "")+op.gen()+"]"}
                @operands[1..-1].each{ |op| res+="["+(op.instance_of?(String) ? op : op.gen())+"]"}
            end
        when 'array'
            res = @operator.name + "[" + @operands.collect{|exp| exp.gen()}.join("][") + "]"
        when 'oper', 'assign'
            res += @operator.sign if @operator.kind == 'prefix'
            res += '(long)' unless @operands[0].flag(Exp::DEST | Exp::CAST) or
                                   @operator.operand1TypeSet.member?(@operands[0].resType) # casting in integral op
            res += (@operands[0].instance_of?(String) ? @operands[0] : genVal(@operands[0]))
            res += @operator.sign if @operator.kind == 'postfix'
            res += " " + @operator.sign + " " if @operator.kind == 'infix'
            if !@operands[0].instance_of?(String) and !@operands[1].instance_of?(String)
                if ( ['integral_assn', 'arith_assn'].member?(@operator.category) and
                     typeGT?(@operands[1].resType, @operands[0].resType) )
                    res += '(' + @operands[0].resType + ')' # casting in assignment
                    properNumeric(@operands)
                elsif @operator.kind == 'infix' and !@operator.operand2TypeSet.member?(@operands[1].resType) and
                      !@operands[1].flag(Exp::CAST)
                    res += '(long)' # casting in integral operations
                end
            end
            res += (@operands[1].instance_of?(String) ? @operands[1] : genVal(@operands[1], @operator)) if @operator.kind == 'infix'
        when 'cond'
        when 'inlinvoc'
        when 'invoc', 'libinvoc'
            res = @operands[0].gen()
        else
            error("Expression.gen: kind = " + @kind, true)
        end
        if flag(Exp::CAST) and typeGT?(@resType, @type)
            res = "(" + @type +")(" + res + ")"
        end
        res
    end

    def flag?(fl)
        (@flags & fl) > 0
    end
end # class Expression

#===============================================================================
# expressions of various specific sorts

class ExpIntLit < Expression # integer literal

    def initialize(stmt, val, depth=0, flags=0)
        super(stmt, 'int', depth, 'literal', {'op'=>nil, 'vals'=>[val]}, flags)
    end
end

class ExpScal < Expression # scalar of any type

    def initialize(stmt, val, depth=0, flags=0)
        super(stmt, (val.instance_of?(String) ? 'int' : val.type),
        depth, 'scalar', {'op'=>nil, 'vals'=>[val]}, flags)
    end
end

class ExpBinOper < Expression # binary operator applied to two given operands

    def initialize(stmt, value1, op, value2, depth=0, flags=0)
        op = Operator.get(op, 'infix') if op.instance_of?(String)
        value1 = ExpScal.new(stmt, value1, depth+1, flags) unless value1.kind_of?(Expression)
        value2 = ExpScal.new(stmt, value2, depth+1, flags) unless value2.kind_of?(Expression)
        super(stmt, value1.resType, depth, 'oper', {'op'=>op, 'vals'=>[value1, value2]}, flags)
    end
end

#===============================================================================
# generic Java statement
class Statement
    attr_reader :context       # context of this statement
    attr_reader :parent        # statement that includes this one
    attr_accessor :compoundFlag  # if it can include other statements
    attr_reader :loopFlag      # if it is a loop of some type
    attr_reader :emptyFlag     # if this statement should not have been created
    attr_reader :pVarReuse     # probability of reusing a var within this statement
    attr_accessor :nestedStmts # list of nested statements

    #------------------------------------------------------
    def initialize(cont, par, compFlag=false, loopFlag=false)
        @context = cont
        @parent = par
        @compoundFlag = compFlag
        @loopFlag = loopFlag
        @emptyFlag = false
        @nestedStmts = Hash.new(nil) if compFlag
        @pVarReuse = $conf.p_var_reuse
    end

    #------------------------------------------------------
    # randomly pick a statement nested in this one; return the statement's object
    def pickNested
        stmt = self
        loopDepth = 0
        while (stmt)
            loopDepth += 1 if stmt.loopFlag
            stmt = stmt.parent
        end
        @context.method.numStmtsToGen -= 1
        normStmtList = $conf.stmt_list.keys.collect {|cl|
            [cl]*(loopDepth > 0 ? ($conf.stmt_list[cl][1]/($conf.stmt_list[cl][2]**loopDepth)).to_i :
            $conf.stmt_list[cl][0])
        }.flatten
        if $run_methods >= $conf.max_threads || !(@context.getContMethod().mainTestFlag || @context.getContMethod().mainFlag) #@context.getContMethod().runFlag
            normStmtList.delete(NewThreadStmt)
        end
        unless (@context.getContMethod().mainTestFlag || @context.getContMethod().mainFlag) #@context.getContMethod().runFlag
            normStmtList.delete(SmallMethStmt)
        end
        if $globalContext.getCallersHashDepth() >=$conf.max_callers_chain
 normStmtList.delete(InvocationStmt)
 normStmtList.delete(SmallMethStmt)
 normStmtList.delete(CondInvocStmt)
        end
        ds = ""
        ds += normStmtList.collect{ |s| s.name + ", " }.join()
        while true
            stmtClass = wrand(normStmtList)
            stmt = stmtClass.new(@context, self)
            break unless stmt.emptyFlag
        end
        stmt
    end

    #------------------------------------------------------
    def stmtsRemainder
        @context.method.numStmtsToGen <= 0 ? 0 : @context.method.numStmtsToGen
    end

    #------------------------------------------------------
    # create a random ordered sequence of nested statements for this one; return array of stmts
    def genStmtSeq(maxNumStmts, canBeEmpty=true)
        n = (prob($conf.p_empty_seq) ? 0 : rand(maxNumStmts) + 1)
        n = 1 if n == 0 and !canBeEmpty
        res = []
        n.times {
            s = pickNested()
            res << s
            break if stmtsRemainder() <= 0
        }
res1 = ""
res1 += res.collect{|st| st.gen()}.join()
#       dputs "genStmtSeq:  \n/*" + res1 + "*/\n"
        return res
    end

    #------------------------------------------------------
    def loopNesting
        nesting = 0
        stmt = @parent
        while (stmt)
            nesting += 1 if stmt.loopFlag
            stmt = stmt.parent
        end
        nesting
    end
end

#===============================================================================
# Assignment statement
class AssignmentStmt < Statement

    def initialize(cont, par, expr=nil)
        super(cont, par)
        if expr and expr.instance_of?(Expression)
            expr.parentStmt = self
            @assnExpr = expr
        elsif !expr
            @assnExpr = Expression.new(self, $conf.types.getRand(), 0, 'assign')
            # else: expr is not nil but not an Expression - need the instance w/o any expression (like in Vectorization)
        end
    end

    def gen
        ln(@assnExpr.gen() + ";")
    end
end

#===============================================================================
#  IntDivStmt represents a random test for integer division optimization
#  when faster 8-bit and 16-bit signed divisions are used for 32-bit integers
class IntDivStmt < Statement

    def initialize(cont, par)
        super(cont, par, true)
        if (loopNesting() > 3)  # no try-catch in nested loops to work around Android's slowness at exception handling (ABIT-1214, ABIT-888)
            @emptyFlag = true
            return
        end
        @nestedStmts["body"] = []
        3.times {
            case rand(3)
            when 0
                op1 = ExpIntLit.new(self, specIntLit(), 2)
                op2 = Expression.new(self, 'int', 2, $conf.exp_kind.getRand(['scalar', 'array']), nil, Exp::FINAL)
            when 1
                op1 = Expression.new(self, 'int', 2, $conf.exp_kind.getRand(['scalar', 'array']))
                op2 = ExpIntLit.new(self, specIntLit(1), 2)
            when 2
                op1 = Expression.new(self, 'int', 2, $conf.exp_kind.getRand(['scalar', 'array']))
                op2 = Expression.new(self, 'int', 2, $conf.exp_kind.getRand(['scalar', 'array']), nil, Exp::FINAL)
            end
            dest = Expression.new(self, 'int', 1, $conf.exp_kind.getRand(['scalar', 'array']), nil, Exp::DEST)
            oper = $conf.operators['arith'].getRand(['/','%'])
            div  = Expression.new(self, 'int', 1, 'oper', {'op'=>Operator.get(oper, nil, 'arith'), 'vals'=>[op1, op2]})
            assn = Expression.new(self, 'int', 0, 'assign', {'op'=>Operator.get('=', nil, 'integral_assn'), 'vals'=>[dest, div]})
            @nestedStmts["body"] << AssignmentStmt.new(cont, self, assn)
        }
    end

    def gen
        #res   = ln("// Test integer division optimization")
        res  = ln("try {")
        shift(1)
        res += @nestedStmts["body"].collect{|st| st.gen()}.join()
        shift(-1)
        res + ln("} catch (ArithmeticException a_e) {}")
    end

    def specIntLit(add=0)
        case rand(5)
        when 0..1
            v = rand(0x100)      # 8-bit int
        when 2..3
            v = rand(0x10000)    # 16-bit int
        else
            v = rand(0x80000000) # 32-bit int
        end
        v = (v + add) * (2 * rand(2) - 1) # multiply by -1 or +1
        return v.to_s
    end
end
