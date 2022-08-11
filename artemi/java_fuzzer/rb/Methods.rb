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
#  Method declarations, invocations, return statements
#
# Authors: Mohammad R. Haghighat, Dmitry Khukhro, Andrey Yakovlev
#==========================================================

#----------------------------------------------------------
# Java* Fuzzer test generator
#
# Modifications 2017-2018: Nina Rinskaya (Azul Systems)
#----------------------------------------------------------

STAT_INT_FLD_NAME = 'statIntField' # for inlinable methods
INL_METH_FLAG = 'addStaticInlMethods'
require 'Basics.rb'

#===============================================================================
# Java Method representation
class JavaMethod
    attr_reader :methClass, :name, :type, :args, :resFieldName, :static, :genFlag, :fictive, :constructorFlag, :runFlag, :small, :overrideFlag
    attr_reader :context    # this method's context
    attr_reader :mainFlag   # method 'main', contains only 'mainTest' invocations in a loop
    attr_reader :mainTestFlag   # method 'mainTest' which is called from method 'main' (only) in a loop and calls other generated methods
    attr_reader :rootStmt   # fake stmt for storing method's body top level statements
    attr_reader :statements # this method's statements list
    attr_accessor :numStmtsToGen # how many statements remain to generate for this method

    def initialize(methClass, type=nil, mainFlag=false, mainTestFlag=false, static=true, constructor=false, outer_callers=[], inner_callees=[], fictive=false, run=false, small=false)
        @constructorFlag=constructor
        @runFlag=run
        @small=small
        $run_methods = $run_methods+1 if @runFlag
        $globalContext.methodCallers[self]=[self]
        $globalContext.method=self if (mainTestFlag || mainFlag)
        @genFlag = false
        @overrideFlag=false
        @fictive=fictive
        @methClass = methClass
        @methClass.context.method = self if constructor
        @methClass.setConstructor(self) if constructor
        @mainTestFlag = mainTestFlag
        @mainFlag = mainFlag
#@static= (mainTestFlag || mainFlag) ? true : static
        @static= ( mainFlag) ? true : static
        @static= ( mainTestFlag) ? false : static
        @numStmtsToGen = ($conf.max_stmts / ((mainTestFlag || mainFlag) ? 1 : 2)).to_i unless fictive
        @numStmtsToGen = 2 if @small
        @context = Context.new(methClass.context, Con::METH, self)
        @type = type
        @type = '' if @constructorFlag
        if @mainTestFlag
            @name = 'mainTest'
            @args = [Arr.new(@context, 1, 'String', Nam::ARG | Nam::LOC | Nam::AUX)]
#outer_callers.each{|outer_caller| @context.addMethodCaller(outer_caller,self) unless outer_caller.nil?}
#dputs "calling addMethodCaller when creating method mainTest"
            inner_callees.each{|inner_callee| @context.addMethodCaller(self,inner_callee) unless inner_callee.nil?}
        elsif @mainFlag
            @name = 'main'
            @args = [Arr.new(@context, 1, 'String', Nam::ARG | Nam::LOC | Nam::AUX)]
#           outer_callers.each{|outer_caller| @context.addMethodCaller(outer_caller,self) unless outer_caller.nil?}
#            dputs "calling addMethodCaller when creating method main"
            inner_callees.each{|inner_callee| @context.addMethodCaller(self,inner_callee) unless inner_callee.nil?}
#TODO: do we need outer_callers, inner_callees to be added for main?
        else
            overriden_method=nil
            if prob($conf.p_method_override) and @methClass.extendsClass != nil and !@runFlag and !@static and !@constructorFlag
                matching=[]
                clsTmp = @methClass
                while ! clsTmp.extendsClass.nil?
                    clsTmp = clsTmp.extendsClass
                    matching.concat( clsTmp.methList.find_all { |meth| (( (meth.type == @type) or (@type == nil)) and (meth.static == false) and !meth.constructorFlag) })
                end
               
                overriden_method=wrand(matching) if matching.size > 0
            end
            if overriden_method !=nil
                alreadyOverriden = (@methClass.methList.find_all { |meth| meth.name == overriden_method.name }.size > 0)
            end
            if overriden_method !=nil and !alreadyOverriden
                    @name=overriden_method.name
                    @args = []
                    @args=overriden_method.args
                    @overrideFlag=true
                    @type = overriden_method.type
            else # not overriding parent methods
                @type = $conf.types.getRand(nil,['Array']) unless type
                @name = @methClass.context.genUniqueName((@small ? 'Small' : '')+'Meth', @type) unless @constructorFlag or @runFlag
                @name = @methClass.name if @constructorFlag
                @name = 'run' if @runFlag
                @args = []
                @overrideFlag=false
                if !@constructorFlag and !@runFlag #TODO - constructors with arguments
                    excl_types=[]
                    excl_types << 'Array' #not an actual Java type
                    excl_types << 'Object' if $conf.allow_object_args == 0
                    rand($conf.max_args + 1).times {
                        arg_type= $conf.types.getRand(nil,excl_types)
                        @args << Var.new(@context, arg_type, Nam::ARG | Nam::LOC)
                    }
                end

            end 
            if !@constructorFlag            
               outer_callers.each{|outer_caller| @context.addMethodCaller(outer_caller,self) unless (outer_caller.nil?)} 
#               inner_callees.each{|inner_callee| @context.addMethodCaller(self,inner_callee) unless (inner_callee.nil?)} 
            end

            @resFieldName = @name + '_check_sum' unless fictive
            @methClass.addClassMembers(['public static long ' + @resFieldName + ' = 0;']) unless fictive
        end
        @rootStmt = Statement.new(@context, nil, true) unless fictive  # fake stmt for storing method's body statements
        @rootStmt.nestedStmts['body'] = @rootStmt.genStmtSeq(@context.method.numStmtsToGen, false) unless fictive || mainFlag
    end

    #------------------------------------------------------
    def registerCaller(meth)
        #@callers << meth
        @context.addMethodCaller(meth,self)
    end

    #------------------------------------------------------
    # determine if given method is direct or indirect caller for this one
    def caller?(meth)
        return @context.caller?(meth,self)
    end

    #------------------------------------------------------
    # generate storing check sum of a method in the field and return if needed
    def genEnding
        return ln(@resFieldName + ' += ' + @context.genMethCheckSum() + ';') if @type == 'void'
        res  = ln('long meth_res = ' + @context.genMethCheckSum() + ';')
        res += ln(@resFieldName + ' += meth_res;')
        return res + ln('return meth_res % 2 > 0;') if @type == 'boolean'
        return res + ln('return;') if @constructorFlag
        res += ln('return (' + @type + ')meth_res;')
    end

    #------------------------------------------------------
    # generate this method's declaration
    def gen
        @genFlag = true
        res = ln((@overrideFlag ? '@Override ' : '') +  'public '+ (@static ? 'static ' : '') + @type + ' ' + @name + '(' +
        @args.collect {|var| (var.className.nil? ? var.type : var.className.name) + (var.instance_of?(Arr) ? '[]' * var.ndim : '') + ' ' + var.name}.join(', ') +
        ') {') + "\n"
        shift(1)
        res+=ln('if ('+@args[0].name+'.length > 0) FuzzerUtils.seed('+rand(100000000).to_s+' + Long.parseLong('+@args[0].name+'[0]));') if @mainTestFlag and $conf.outer_control
        res+=ln('instanceCount++;') if @constructorFlag
        res += @context.genDeclarations() if !@mainFlag # no declarations should be generated for main method
        @rootStmt.nestedStmts['body'].each {|st| res += st.gen()} if !@mainFlag # no statements should be generated for main method
        res += ln('FuzzerUtils.joinThreads();') if @mainTestFlag && $run_methods>0
        res += (@mainTestFlag ? @context.genResPrint() + @methClass.context.genResPrint() : '')
        res += (!(@mainTestFlag||@mainFlag) ? genEnding() : '')
        glob = (@mainTestFlag ? @methClass.genGlobCheckSums() : '')
#main method:
        if @mainFlag            
            res += ln("try {")
            shift(1)
            res +=  ln(@methClass.name + " _instance = new " +  @methClass.name + "();")
            res +=  ln("for (int i = 0; i < " + $conf.mainTest_calls_num.to_s + "; i++ ) {")
            shift(1)
            res += ln("_instance." + @methClass.methMainTest.name + "(" + @args[0].name + ");")
            shift(-1)
            res += ln("}")
            if ($conf.time_sleep_complete_tier1 > 0)
                res += ln("try {") + ln("Thread.sleep(" + $conf.time_sleep_complete_tier1.to_s + ");") + ln(" } catch (InterruptedException ie) {") 
                shift(1)
                res += ln("ie.printStackTrace();") 
                shift(-1)
                res += ln("}")
            end
            if $conf.mainTest_calls_num_tier2 > 0
                res +=  ln("for (int i = 0; i < " + $conf.mainTest_calls_num_tier2.to_s + "; i++ ) {")

                shift(1)
                res += ln("_instance." + @methClass.methMainTest.name + "(" + @args[0].name + ");")
                shift(-1)
                res = res + ln("}")
            end
            shift(-1)
            res += ln(" } catch (Exception ex) {")
            shift(1)
            res += ln("FuzzerUtils.out.println(ex.getClass().getCanonicalName());")
            shift(-1)
            res += ln(" }")

        end
      
        res += "\n" + glob unless glob.empty?

        shift(-1)
        res + ln("}")
    end
end

#===============================================================================

# generate definition of static inlinable methods
def addStaticInlMethods
    @method.methClass.addClassMembers(
    ['static int ' + STAT_INT_FLD_NAME + ' = 3;',
        'static void statSet(int value) {' + STAT_INT_FLD_NAME + ' = value;}',
        'static int  statGet() {return ' + STAT_INT_FLD_NAME + ';}'],
    INL_METH_FLAG)
end

# Generate inlinable invocation of the static set method
def rInlSetInvocation
    addStaticInlMethods()
    return tab + 'statSet((int)(' + rExp + '));\n'
end

# Generate inlinable invocation of the static get method
def rInlGetInvocation
    addStaticInlMethods()
    return 'statGet()'
end

# Generate invocation of a method; type=nil means any type but void
def rInvocation(type=nil)
    meth = @method.methClass.getMethod(@method, type)
    return '' if !meth
    meth.registerCaller(@method)
    res = meth.name + '('
    meth.args.each {|arg|
        res += '(' + arg.type + ')(' + rExp + '), '
    }
    res = res[0..-3] if meth.args.size > 0
    return res + ')'
end

# Generate standalone invocation of a method
def rInvocationStmt
    res = rInvocation(prob(85) ? 'void' : nil)
    return '' if res.empty?
    return tab + res + ';\n'
end

#===============================================================================
# method invocation expression
class ExpInvocation < Expression
    attr_reader :method, :prefix

    # invocation of a method; type=nil means any type but void
    def initialize(stmt, type, depth, meth=nil)
        return if loopDepth(stmt) > $conf.exp_invoc_loop_depth
        @method = meth
# we want to avoid creating fields in the current class that would result in cyclic inheritance with method caller's class
# we create fields only for non-static methods, so no need for ths check in case we call getMethod for static method (it will not return instance method then)
        goodClassList =[]
        if !stmt.context.method.static
            $globalContext.classList.each {|c| 
                goodClassList << c if JavaClass.noCyclicInheritanceCheck_?(stmt.context.method.methClass, c) || stmt.context.method.methClass == c 
            }
        else 
            goodClassList=$globalContext.classList
        end
        cls = wrand(goodClassList)
#was:        @method = stmt.context.method.methClass.getMethod(stmt.context.method, type) if @method.nil?
        if @method.nil? and $globalContext.getCallersHashDepth() < $conf.max_callers_chain
            @method = cls.getMethod(stmt.context.method, type)
        end
        if @method.nil?
            return
        end
        @method.registerCaller(stmt.context.method)
        vals = []
        @method.args.each {|arg|
            vals << Expression.new(stmt, arg.type, depth+1, nil, nil, Exp::CAST, arg.className)
        }
        super(stmt, @method.type, depth, 'invoc', {'op'=>nil, 'vals'=>vals})
        @prefix=''
        cont=@context
        while cont.class_.nil?
            cont=cont.parent
        end
        if @method.methClass != cont.class_
            if @method.static
                @prefix=@method.methClass.name+'.'
            else
                var=@context.getVar($conf.p_var_reuse, 'Object', false, @method.methClass, true)
                @prefix=var.name+'.'
            end
        end
    end

    def gen
        #failed to generate ExpInvocation (method is nil)
        return '' if @method.nil? #TODO: understand why we get nil sometimes 
        res = @prefix+@method.name + '('
        @operands.each {|exp| res += exp.gen() + ', '}
        res = res[0..-3] if @operands.size > 0
        res + ')' 
    end
end

#===============================================================================
# method invocation statement
class NewThreadStmt < Statement

    def initialize(cont, par)
        super(cont, par)
        var=cont.getVar($conf.p_var_reuse, 'Object', false, nil, true, nil, false)
        met=var.className.getRunMethod(cont.getContMethod())
        #@emptyFlag = true unless @invocExpr.kind == 'invoc'
        @invocExpr='FuzzerUtils.runThread('+var.gen()+')'
    end

    def gen
        ln(@invocExpr + ';')
    end
end

#===============================================================================
# method invocation statement
class InvocationStmt < Statement
    attr_reader :invocExpr

    def initialize(cont, par, expr=nil)
        super(cont, par)
        if (expr)
            expr.parentStmt = self
            @invocExpr = expr
        else
            @invocExpr = ExpInvocation.new(self, (prob(90) ? 'void' : nil), 0)
            
        end
        @emptyFlag = true unless @invocExpr.kind == 'invoc'
    end

    def gen
        ln(@invocExpr.gen() + ";")
    end
end

#===============================================================================
# return statement
class ReturnStmt < Statement

    def initialize(cont, par, withinIf=true)
        super(cont, par)
        @withinIf = withinIf
        if @context.method.mainTestFlag or @context.method.mainFlag or !(prob($conf.p_return) or @withinIf)
            @emptyFlag = true
        end
        @condition = ExpBinOper.new(self, Expression.new(self, 'int', 0, 'scalar'), '!=', '0') if @withinIf
    end

    #------------------------------------------------------
    # generate return statement; withinIf=true means return under condition within If statement
    def gen
        res = ''
        res += ln('if (' + @condition.gen() + ') {') if @withinIf
        shift(1) if @withinIf
        checkSum = @context.method.context.genMethCheckSum()
        if @context.method.type == 'void'
            res += ln(@context.method.resFieldName + ' += ' + checkSum + ';') +
            ln('return;')
        elsif @context.method.type == 'boolean'
            res += ln('return ((int)(' + checkSum + ')) % 2 > 0;')
        else
            res += ln('return (' + @context.method.type + ')(' + checkSum + ');') if !@context.method.type==''
            res += ln('return;') if @context.method.type==''
        end
        shift(-1) if @withinIf
        res += ln('}') if @withinIf
        return res
    end
end

#===============================================================================
# conditional method invocation statement
class CondInvocStmt < InvocationStmt
    def initialize(cont, par, expr=nil)
        super(cont, par)
    end

    def gen
        ln('if (FuzzerUtils.seed % '+$conf.outer_control_prob.to_s+
               ' == '+rand($conf.outer_control_prob).to_s+') '+@invocExpr.gen() + ';')
    end
end


#===============================================================================
# Small method invocation statement in loop
class SmallMethStmt < Statement

    def initialize(cont, par)
        super(cont, par)
        met=cont.method.methClass.getSmallMethod(cont.getContMethod())
        @invocExpr=ExpInvocation.new(self, nil, 0, met)
    end

    def gen
        prefix=''
        if $conf.outer_control
            prefix=ln('if (FuzzerUtils.seed % '+$conf.outer_control_prob.to_s+
                          ' == '+rand($conf.outer_control_prob).to_s+')')
            shift(1)
        end
         loop_depth = 0
         stmt = self
         while (stmt)
            loop_depth += 1 if stmt.loopFlag
            stmt = stmt.parent
         end

        loop_length=rand($conf.max_small_meth_calls-$conf.min_small_meth_calls)+$conf.min_small_meth_calls
        loop_length = (loop_length*($conf.max_size**loop_depth) < $conf.max_nested_size ? loop_length : $conf.max_nested_size/($conf.max_size**loop_depth))

        loop_length=(loop_length > 0 ? loop_length : 1)
        res=prefix+ln('for (int smallinvoc=0; smallinvoc<'+ 
                loop_length.to_s+
               '; smallinvoc++) '+@invocExpr.gen() + ';')
        if $conf.outer_control
            shift(-1)
        end
        return res
    end
end
