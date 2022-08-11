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
#  Basic classes
#
# Authors: Mohammad R. Haghighat, Dmitry Khukhro, Andrey Yakovlev
#----------------------------------------------------------

#----------------------------------------------------------
# Java* Fuzzer test generator
#
# Modifications 2017-2018: Nina Rinskaya (Azul Systems), Ivan Popov (Azul Systems)
#----------------------------------------------------------

module Nam # Name attributes
    LOC = 1 # name is localized to given context
    ARG = 2 # name of a method argument
    NULL = 4 # name of var/arr assigned zero/null value for spec cases
    STAT = 8 # name of static field
    AUX = 16 # name of auxiliary variable - should not be used
    CLS = 32 # class member
    PUB = 64 # public member
    FIN = 128 # final member
    SUB = 256 # local field of a local object, should not be declared
    NOTNULL = 512 # Variable must not be null
    BLOCK = 1024 # Block local variable
    VOLATILE = 2048 # Volatile variable
end

# For old versions of Ruby. Hash selection returns an Array, not a Hash. Below is a trick how to get the values of the reduced Hash
class Array

    def values
        inject([]) do |res, val|
            res<<val[1]
            res
        end.flatten
    end
end

# For testing future chain of callers
class Hash

    def smart_copy
        ret={}
        each do |key, value|
            ret[key]=value.clone
        end
        return ret
    end
end

# For selecting an Array
class ArrCh
    attr_accessor :dim, :type, :size

    def initialize(type=nil, dim=0, size=0)
        @type=type
        @dim=dim
        @size=size
    end
end

#===============================================================================
# scalar local variable
class Var
    attr_reader :type, :flags
    attr_reader :init_value
    attr_accessor :prepostFlag, :inductionVarFlag, :currIndVarFlag, :className, :context, :classMember

    def initialize(cont, type="", flags=0, className=nil, name=nil)
        @flags = flags
        @className = className
        @type = type == "" ? $conf.types.getRand(nil, ['Array']) : type
        @flags |= Nam::VOLATILE if prob($conf.p_volatile)
        if cont.kind < Con::CLASS and flag?(Nam::CLS) #and prob($conf.p_stat_field)
            @flags|=Nam::PUB
            until cont.kind >= Con::CLASS
                cont = cont.parent
            end
        end
        @context = cont
        if @className.nil? and @type=='Object'
            @className = $globalContext.getClass($conf.p_class_reuse, @context)
        end
        @name = cont.registerVar(self, flag?(Nam::LOC), name)
        @prepostFlag = false
        @inductionVarFlag = false
        @currIndVarFlag = false
        @init_value = 0
    end

    def flag?(fl)
        (@flags & fl) > 0
    end

    def flags_to_s
        ret=""
        ret+="local " if flag?(Nam::LOC) # name is localized to given context
        ret+="argument " if flag?(Nam::ARG) # name of a method argument
        ret+="null " if flag?(Nam::NULL) # name of var/arr assigned zero/null value for spec cases
        ret+="static " if flag?(Nam::STAT) # name of static field
        ret+="aux " if flag?(Nam::AUX) # name of auxiliary variable - should not be used
        ret+="class " if flag?(Nam::CLS) # class member
        ret+="public " if flag?(Nam::PUB) # public member
        ret+="final " if flag?(Nam::FIN) # final member
        ret+="sub-field " if flag?(Nam::SUB) # local field of a local object, should not be declared
        ret+="not-null " if flag?(Nam::NOTNULL) # Variable must not be null
        ret+="block " if flag?(Nam::BLOCK) # Variable declared in block
        ret+="volatile " if flag?(Nam::VOLATILE) # Variable is volatile
        return ret
    end

    def gen_modifiers()
        ret=""
        ret = "volatile " + ret if flag?(Nam::VOLATILE)
        ret = "final " + ret if flag?(Nam::FIN)
        ret = "static " + ret if flag?(Nam::STAT)
        ret = "public " + ret if flag?(Nam::PUB)
        ret = "private " + ret if !flag?(Nam::PUB)
        return ret
    end

    def gen_decl
        if flag?(Nam::NULL) or !$conf.outer_control or @type=='Object'
            @init_value = (flag?(Nam::NULL) ? (@type == 'Object' ? "null" : "0") : rLiteral(@type, @className, @context, flag?(Nam::NOTNULL)))
            return @name + "=" + @init_value
        else
            @init_value = "FuzzerUtils.next"+@type.capitalize+"()"
            return @name + "=" + @init_value
        end
    end

    def gen_init
        return gen_decl()
    end

    def gen_checkSum
        return case @type
                   when "boolean" then
                       "(" + name + " ? 1 : 0)"
                   when "char" then
                       "(int)" + name
                   when "float" then
                       "Float.floatToIntBits(" + name + ")"
                   when "double" then
                       "Double.doubleToLongBits(" + name + ")"
                   when "Object" then
                       gen_obj_checkSum() #CHECKC+"("+@name+")"
                   else
                       name
               end
    end

    def gen_obj_checkSum
        return SUPER + "." + CHECKC + "("+name+")" #if @className.constructor.nil?
    end

    def gen
        #return @name
        return (flag?(Nam::STAT) ? @context.class_.name+"." : "")+@name #if !$debug
        #return (@flags!=0 ? "/*("+flags_to_s+")*/" : "")+(flag?(Nam::STAT) ? @context.class_.name+"." : "")+@name #if $debug
    end

    def name
        gen()
    end
end

#===============================================================================
# array
class Arr < Var
    attr_reader :ndim, :size

    def initialize(cont, ndim=0, type="", flags=0, name=nil, size=0)
        super(cont, type, flags, nil, name)
        size = rand($conf.max_big_array)+$conf.min_big_array if prob($conf.p_big_array) and ndim==0

        @size = size
        if type == 'Object' and $conf.max_object_array_size != $conf.max_size
            @size=rand($conf.max_object_array_size) + 1
        end
        @ndim = ndim
        @ndim = wrand([1, 1, 1, rand($conf.max_arr_dim)+1]) if ndim == 0 and @size==0
        @ndim = 1 if @size!=0
    end

    def gen_new(dim=MAX_TRIPNM)
        dim=@size if @size!=0
        res = "new " + @type
        @ndim.times { res += "[" + dim.to_s + "]" }
        res
    end

    def gen_decl
        res = @name
        @ndim.times { res += "[]" }
        res + "=" + (flag?(Nam::NULL) ? "null" : gen_new())
    end

    def gen_init
        return "" if flag?(Nam::NULL)
        if !$conf.outer_control or @type=='Object'
            
        SUPER + "." + INITC + "(" + name + ", " +
                (["short", "char", "byte"].member?(@type) ? "("+@type+")" : "") +
                rLiteral(@type, nil, @context, true) + ");"
        else
            
        SUPER + "." + INITC + "(" + name + ", " +
                "FuzzerUtils.next"+@type.capitalize+"()" + ");"
        end
    end

    def gen_checkSum
        return "0" if flag?(Nam::NULL)
        case @type
            when "float", "double" then
                "Double.doubleToLongBits(" + SUPER + "." + CHECKC + "(" + name + "))"
            else
                SUPER + "." + CHECKC + "(" + name + ")"
        end
    end
end

module Con # context attributes
    CLASS = 10 # context of class members definitions
    METH = 5 # method's body context
    BLOCK = 4 # context of a block within a method
    STMT = 3 # context of a statement within a method
    GLOBAL = 20 # global context
end

#===============================================================================
# context of names
class Context
    attr_reader :parent
    attr_reader :kind
    attr_reader :varList
    attr_reader :objList
    attr_reader :classList # list of all classes. For global context only
    attr_reader :methodCallers # Method relationship Hash. For global context only
    attr_reader :arrList
    attr_accessor :method # JavaMethod that contains this context
    attr_accessor :class_ # JavaClass that contains this context
    attr_accessor :ID # Unique contex number
    attr_accessor :ids # id counter

    #------------------------------------------------------
    def initialize(parent, kind, meth, class_=nil, global=false)
        @parent = parent
        @kind = kind
        @varList = []
        @arrList = []
        @objList = {}
        @class_ = class_
        @classList=[] if global
        @methodCallers={} if global
        $debugCnt=0 if global
        @ids=1 if global
        @method = meth
        $globalContext.ids+=1 if !global
        @ID = $globalContext.ids if !global
        @ID = 1 if global
    end

    def classListForSelection(methods=[], revMethods=[])
        return $globalContext.classList.select { |value| !value.headFlag } if (methods.empty? and revMethods.empty?)
        return $globalContext.classList.select { |value| !value.headFlag and
            (value.constructor.nil? or
                methods.inject(true) { |val, met| val&&=(met.nil? || !caller?(value.constructor, met)) } &&
                    revMethods.inject(true) { |val, met| val&&=(met.nil? || !caller?(met, value.constructor)) }) }
    end

    #------------------------------------------------------
    def getContMethod
        #   if @kind==Con::CLASS
        #     return @class.constructor
        #   end
        cont=self
        while cont.method.nil?
            cont=cont.parent
            return nil if cont.nil?
        end
        return cont.method
    end

    #------------------------------------------------------
    def addMethodCaller(caler, callee=nil, hash=nil)
        if callee.nil?
            callee_nil = "true"
            callee=getContMethod()
        else
            callee_nil = "false"
        end
        if caler==callee
            printMethodCallers()
#            error("Fatal! trying to cycle a call chain: callee \""+callee.name+"\" is already a caller of caller \""+caler.name+"\"", true)
    puts "DEBUG: Fatal! trying to cycle a call chain: callee \""+callee.name+"\" is already a caller of caller \""+caler.name+"\", case 1, callee_nil = " + callee_nil
#    return
exit(1)
        end
        return if caler==callee or callee.nil? or caler.nil?
        if caller?(callee, caler)
            printMethodCallers()
#            error("Fatal! trying to cycle a call chain: callee \""+callee.name+"\" is already a caller of caller \""+caler.name+"\"", true)
            puts "DEBUG: Fatal! trying to cycle a call chain: callee \""+callee.name+"\" is already a caller of caller \""+caler.name+"\", case 2"
#            return
            exit(1)
        end
        hash=$globalContext.methodCallers if hash.nil?
        hash[callee]=[] if hash[callee].nil?
        hash[callee]<<caler if !hash[callee].include?(caler)
        hash.each { |tcallee, callers| addMethodCaller(caler, tcallee, hash) if callers.include?(callee) and (hash[tcallee].nil? or !hash[tcallee].include?(caler)) }
        hash[caler].each { |supercaller| addMethodCaller(supercaller, callee, hash) if !hash[callee].include?(supercaller) } if !hash[caler].nil?
    end

    #------------------------------------------------------
    def getCallersHashDepth(callee=nil, hash=nil)
        hash=$globalContext.methodCallers if hash.nil?
        if callee.nil?
            arr=[]
            hash.each { |key, val| arr<<getCallersHashDepth(key, hash) }
            return arr.max
        else
            return 0 if hash[callee].size<=1
            arr=[]
            hash[callee].each { |val| arr<<(val!=callee ? getCallersHashDepth(val, hash) : 0) } # added && !val.constructorFlag
            return arr.max+1
        end
    end

    #------------------------------------------------------
    def canAddCaller?(callee, caler)
        prevLength = getCallersHashDepth()
        hash=$globalContext.methodCallers.smart_copy()
        #added this check (copypaste from addMethodCaller) to return false instead of fatal error in case we have a cyclic call chain
        if callee.nil?
            callee=getContMethod()
       end
       if caler==callee
           printMethodCallers()
           return false
#            error("Fatal! trying to cycle a call chain: callee \""+callee.name+"\" is already a caller of caller \""+caler.name+"\"", true)
      end
       return false if caler==callee or callee.nil? or caler.nil?
       if caller?(callee, caler)
           printMethodCallers()
           return false
#            error("Fatal! trying to cycle a call chain: callee \""+callee.name+"\" is already a caller of caller \""+caler.name+"\"", true)
      end
#end of check (copypaste fron addMethodCaller) to return false instead of fatal error in case we have a cyclic call chain

       addMethodCaller(caler, callee, hash)

        nextLength=prevLength = getCallersHashDepth(nil, hash)
        return (nextLength<=$conf.max_callers_chain or prevLength==nextLength)
    end

    #------------------------------------------------------
    def caller?(caller, callee=nil, hash=nil)
        return false if caller.nil?
        if callee.nil?
            callee=getContMethod()
        end
        hash=$globalContext.methodCallers if hash.nil?
        return false if hash[callee].nil? and caller!=callee
        return (hash[callee].include?(caller) or caller==callee)
    end

    def callers?(caller, callees=nil, hash=nil)
        return false if caller.nil?
        if callees.nil?
            callees=[getContMethod()]
        end
        hash=$globalContext.methodCallers if hash.nil?
        ret=callees.inject(true) { |val, met| val&&=!caller?(caller, met, hash) }
        return !ret
    end

    #------------------------------------------------------
    def fullVarList
        @varList + (@parent ? @parent.fullVarList() : [])
    end

    def fullObjList
        @objList.merge(@parent ? @parent.fullObjList() : {})
    end

    def statVarList
        ret=[]
        met=getContMethod()
        $globalContext.classList.each() do |class_|
            ret+=class_.context.varList.select() { |var| var.flag?(Nam::STAT) and (class_.constructor==met or !caller?(class_.constructor, met)) }
        end
        return ret
    end

    def statObjList
        ret={}
        met=getContMethod()
        $globalContext.classList.each() do |class_|
            class_.context.objList.each() do |cls, vars|
                ret[cls]=[] if ret[cls].nil?
                ret[cls]+=vars.select() { |var|
                    var.flag?(Nam::STAT) and
                        (class_.constructor==met or !caller?(class_.constructor, met)) and
                        (var.className.constructor==met or !caller?(var.className.constructor, met)) and
                        (cls.constructor==met or !caller?(cls.constructor, met))
                }
            end
        end
        return ret
    end

    #------------------------------------------------------
def getVar(reuseProb, type, destFlag, className=nil, notnull=false, metlist=nil, not_block=false)
        var = nil
        met=getContMethod()
        metlist=[] if metlist.nil?
        metlist<<met if !met.nil?
        if prob(reuseProb) or (type=='Object' and $globalContext.classList.size()>=$conf.max_classes)
            if type != 'Object'
                if !met.nil? and met.static
                    vars = (statVarList()+fullVarList()).uniq.find_all { |v| v.type == type and (v.flag?(Nam::NOTNULL) or !notnull) and (not v.flag?(Nam::CLS) or v.flag?(Nam::STAT)) } unless destFlag
                    vars = (statVarList()+fullVarList()).uniq.find_all { |v| v.type == type and (v.flag?(Nam::NOTNULL) or !notnull) and (not v.flag?(Nam::CLS) or v.flag?(Nam::STAT)) and !v.inductionVarFlag and !v.flag?(Nam::NULL) } if destFlag
                else
                    vars = (statVarList()+fullVarList()).uniq.find_all { |v| v.type == type and (v.flag?(Nam::NOTNULL) or !notnull) } unless destFlag
                    vars = (statVarList()+fullVarList()).uniq.find_all { |v| v.type == type and (v.flag?(Nam::NOTNULL) or !notnull) and !v.inductionVarFlag and !v.flag?(Nam::NULL) } if destFlag
                end
            else
                if !destFlag
                    arrs = (statObjList().merge(fullObjList())).select { |cls, v| ((className.nil? and !callers?(cls.constructor, metlist)) or cls==className) }.values.flatten.uniq
                    vars = arrs.find_all { |v| (!v.flag?(Nam::CLS) or v.flag?(Nam::STAT)) and (v.flag?(Nam::NOTNULL) or !notnull) }.flatten if not met.nil? and met.static
                    vars = arrs.find_all { |v| (v.flag?(Nam::NOTNULL) or !notnull) }.flatten if met.nil? or not met.static
                else
                    arrs = (statObjList().merge(fullObjList())).select { |cls, v| ((className.nil? and !callers?(cls.constructor, metlist)) or cls==className) }.values.flatten.uniq
                    vars = arrs.find_all { |v| !v.flag?(Nam::NULL) and (v.flag?(Nam::NOTNULL) or !notnull) } if met.nil? or not met.static
                    vars = arrs.find_all { |v| !v.flag?(Nam::NULL) and (v.flag?(Nam::NOTNULL) or !notnull) and (not v.flag?(Nam::CLS) or v.flag?(Nam::STAT)) } if (not met.nil? and met.static)
                end
            end
            if (met.runFlag and not $conf.mode == 'MM_extreme')
                vars = vars.find_all { |v| !v.flag?(Nam::STAT) and (v.flag?(Nam::BLOCK) or (v.flag?(Nam::LOC))) }
            end
            var = wrand(vars)
        end
        if !var.nil?
            return var
        end
  excl_types=[]
        excl_types+=['block'] if not_block or @kind>=Con::METH
        excl_types+=['non_static'] if (!met.nil? and met.static) or (met.runFlag and not $conf.mode == 'MM_extreme')
        excl_types+=['block', 'local', 'local_other'] if @kind==Con::CLASS
        excl_types+=['local_other', 'static_other'] if $stop_creating_outer_fields or (met.runFlag and not $conf.mode == 'MM_extreme')
        excl_types+=['static'] if met.runFlag

        vtype=$conf.var_types.getRand(nil, excl_types)
        addcaller=true
        flags=(notnull ? Nam::NOTNULL : 0)
        case vtype
            when 'non_static' # generating non-static field of a class
                flags=flags|Nam::CLS|Nam::PUB
                # if creating field of type classname leads to cyclic inheritance then set var to nil and local var of type classname will be created instead (see below)
                if type == 'Object' && !met.nil? && !JavaClass.noCyclicInheritanceCheck?(met.methClass, className)
                    var = nil
                else # no cyclic inheritance, can create field of type classname
                    var = Var.new(met.methClass.context, type, flags, className) if (!met.nil? and !met.static)
                    addMethodCaller(met.methClass.constructor, className.constructor) if className != nil;
                    addcaller=false
                end
            when 'static' # generating static field of a class
                flags=flags|Nam::PUB|Nam::STAT|Nam::CLS
                # if creating field of type classname leads to cyclic inheritance then set var to nil and local var of type classname will be created instead (see below)
                if type == 'Object' && !JavaClass.noCyclicInheritanceCheck?((met.nil? ? getContMethod().methClass : met.methClass), className)
                    var = nil
                else # no cyclic inheritance, can create field of type classname
                    var = Var.new((met.nil? ? self : met.methClass.context), type, flags, className) # self
                    metlist=[met.methClass.constructor]
                    addMethodCaller(met.methClass.constructor, className.constructor) if className != nil;
                    addcaller=false
                end
            when 'local' # generating local var
                flags=flags|(@kind == Con::CLASS ? Nam::PUB|Nam::CLS : 0)
                var = Var.new(self, type, flags, className)
                addMethodCaller(met, className.constructor) if !className.nil?
                addcaller=false
            when 'block'
                flags=flags|Nam::BLOCK
                var = Var.new(self, type, flags, className) 
                addMethodCaller(met, className.constructor) if !className.nil?;
                addcaller=false
            when 'local_other' # generating non-static field in a class other than current
                className=getClass($conf.p_class_reuse, self, metlist) if (className.nil? and type=='Object')
                reverseMetlist=[]
                reverseMetlist<<className.constructor if (!className.nil? and !className.constructor.nil?)
                outerClass = getClass($conf.p_class_reuse, self, metlist, reverseMetlist)
                # if creating field of type classname leads to cyclic inheritance then set var to nil and local var of type classname will be created instead (see below)
                if type == 'Object' && !JavaClass.noCyclicInheritanceCheck?(outerClass, className)
                    var = nil
                    inner = nil
                elsif  type == 'Object' && className == nil # not sure if this branch is ever reached
                    var = nil
                    inner = nil
                else # no cyclic inheritance, can create field of type classname
                    outer=getVar(reuseProb, 'Object', true, outerClass, true, metlist, not_block)
                    flag=(outer.flag?(Nam::BLOCK) ? Nam::BLOCK : 0) | (notnull ? Nam::NOTNULL : 0)
                    flags=flags|Nam::PUB|Nam::CLS
                    inner=Var.new(outerClass.context, type, flags, className)
                    addMethodCaller(outerClass.constructor, inner.className.constructor) if outerClass != nil and className != nil
                    var=Var.new(self, type, Nam::SUB|flag, inner.className, outer.name+"."+inner.name)
                    addMethodCaller(met, outerClass.constructor) if outerClass != nil 
                    addcaller=false

                end
            when 'static_other' # generating static field in a class other than current
                className=getClass($conf.p_class_reuse, self, metlist) if (className.nil? and type=='Object')
                reverseMetlist=[]
                reverseMetlist<<className.constructor if (!className.nil? and !className.constructor.nil?)
                outerClass = getClass($conf.p_class_reuse, self, metlist, reverseMetlist)
                flags=flags|Nam::PUB|Nam::CLS|Nam::STAT
                # if creating field of type classname leads to cyclic inheritance then set var to nil and local var of type classname will be created instead (see below)
                if type == 'Object' && !JavaClass.noCyclicInheritanceCheck?(outerClass, className)
                    var = nil
                elsif type == 'Object' && className == nil # not sure if this branch is ever reached
                    var = nil
                else # no cyclic inheritance, can create field of type classname
                    var=Var.new(outerClass.context, type, flags, className)
                    addMethodCaller(outerClass.constructor, var.className.constructor) if outerClass != nil and className != nil 
                    addcaller=false
                end
            else
                error("getVar: var_type = " + type, true)
        end
        if var.nil? # then creating local var, e.g., if we couldn't create class fields due to cyclic inheritance
            flags = (@kind == Con::CLASS ? Nam::PUB|Nam::STAT|Nam::CLS : 0)|(notnull ? Nam::NOTNULL : 0)
            var = Var.new(self, type, flags, className) if var.nil?
            addMethodCaller(met, className.constructor) if className != nil ;
            addcaller=false
        end
        if type == 'Object'
        metlist.each() { |met| addMethodCaller(met, var.className.constructor, nil) if !var.className.nil? and !var.className.constructor.nil? and !met.nil? and addcaller}
        end
        return var
    end

    def debugPrintClassArray(arr)
        ret=arr.inject("") { |ret, a| ret+=a.name+(a.constructor.nil? ? "() " : "("+a.constructor.name+") ") }
        return ret
    end

    #------------------------------------------------------
    #def getClass(reuseProb,context=nil)
    def getClass(reuseProb, context, metlist=[], reverseMetlist=[])
        if prob(reuseProb) or $globalContext.classList.size()>=$conf.max_classes
            if context.nil? or context.getContMethod().nil?
                klassList=classListForSelection(metlist).find_all { |c| c.extendsClass == nil }
                if klassList.size() > 0
                    return wrand(klassList)
                end
                return wrand(classListForSelection(metlist))
            end
            callee=context.getContMethod()
            tmparr=classListForSelection(metlist, reverseMetlist).select { |a| a.name!=callee.name and (a.constructor.nil? or (!caller?(a.constructor, callee) and $globalContext.canAddCaller?(a.constructor, callee))) and !callee.methClass.childClassList.include?(a)}
            if tmparr.size > 0
                cls=wrand(tmparr)
                metlist=[] if metlist.nil?
                metlist<<callee if !callee.nil?
                return cls
            end
        end
        flag=true
        while flag
            callee=context.getContMethod() if !context.nil?
            metlist=[] if metlist.nil?
            metlist<<callee if !callee.nil?
            cls=JavaClass.new(false, metlist, reverseMetlist)
            if callee.nil? or cls.constructor.nil? or !caller?(cls.constructor, callee)
                return cls
            end
        end
    end

    #------------------------------------------------------
    def fullArrList
        @arrList + (@parent ? @parent.fullArrList() : [])
    end

    #------------------------------------------------------
    def getArr(reuseProb, type, ndim=0, notnull=false, arrch=nil, not_block=false)
        if !arrch.nil?
            type=arrch.type
            ndim=arrch.dim
            ndim=0 if ndim==1 # Trying to select a random-dim array for 1-dim array assignment
            size=arrch.size
        else
          if type == 'Object' 
               size=rand($conf.max_object_array_size) + 1
          end
           if prob($conf.p_big_array) and ndim==0 and  type != 'Object'
                size = rand($conf.max_big_array)+$conf.min_big_array
            else
                size=0
            end
        end
        arr = nil
        met=getContMethod()
        if prob(reuseProb)
            if (!met.nil? and met.static)
                arr = wrand(fullArrList().find_all { |v| (!notnull or v.flag?(Nam::NOTNULL)) and v.type == type and (v.ndim == ndim or ndim == 0) and !v.flag?(Nam::NULL) and (!v.flag?(Nam::CLS) or v.flag?(Nam::STAT)) })
            else
                arr = wrand(fullArrList().find_all { |v| (!notnull or v.flag?(Nam::NOTNULL)) and v.type == type and (v.ndim == ndim or ndim == 0) and !v.flag?(Nam::NULL) })
            end
        end
        return arr if !arr.nil?
        excl_types=[]
        excl_types+=['block'] if not_block or @kind>=Con::METH or $conf.mode == 'default'
        excl_types+=['non_static'] if !met.nil? and met.static or (met.runFlag and not $conf.mode == 'MM_extreme')
        excl_types+=['block', 'local', 'local_other'] if @kind==Con::CLASS
        excl_types+=['local_other', 'static_other'] if $stop_creating_outer_fields or (met.runFlag and not $conf.mode == 'MM_extreme')
        excl_types+=['static'] if met.runFlag
        flag=(notnull ? Nam::NOTNULL : 0)
        vtype=$conf.var_types.getRand(nil, excl_types)
        case vtype
            when 'local'
                arr=Arr.new(self, ndim, type, 0|flag, nil, size)
            when 'block'
                arr=Arr.new(self, ndim, type, Nam::BLOCK|flag, nil, size)
            when 'non_static'
                arr=Arr.new(self, ndim, type, Nam::CLS|Nam::PUB|flag, nil, size)
            when 'static'
                arr=Arr.new(self, ndim, type, Nam::CLS|Nam::PUB|Nam::STAT|flag, nil, size)
            when 'static_other'
                metlist=[]
                metlist<<met if !met.nil?
                outerClass = getClass($conf.p_class_reuse, self, metlist)
                arr=Arr.new(outerClass.context, ndim, type, Nam::STAT|Nam::PUB|Nam::CLS|flag, nil, size)
            when 'local_other'
                metlist=[]
                metlist<<met if !met.nil?
                className=getClass($conf.p_class_reuse, self, metlist) if (className.nil? and type=='Object')
                reverseMetlist=[]
                reverseMetlist<<className.constructor if (!className.nil? and !className.constructor.nil?)
                outerClass = getClass($conf.p_class_reuse, self, metlist, reverseMetlist)
                outer=getVar(reuseProb, 'Object', true, outerClass, true, metlist, not_block)
                metlist<<outer.className.constructor if !outer.className.constructor.nil?
                flag|=(outer.flag?(Nam::BLOCK) ? Nam::BLOCK : 0)
                inner=Arr.new(outerClass.context, ndim, type, Nam::PUB|Nam::CLS|flag, nil, size)
                ndim=inner.ndim
                arr=Arr.new(self, ndim, type, Nam::SUB|flag, outer.name+"."+inner.name, size)
            else
                error("Unknown var_type "+vtype)
        end
        arr=Arr.new(self, ndim, type, flag, nil, size) if arr.nil?
        return arr
    end

    #------------------------------------------------------
    def genUniqueName(sort, type=nil, arr=false)
        return @parent.genUniqueName(sort, type, arr) if @kind<Con::METH
        if type == "String"
            pref = "str"
        elsif type == "byte"
            pref = "by"
        elsif ($conf.types.values + ["void"]).member?(type)
            pref = type[0, 1] # the first letter
        elsif type
            pref = "obj"
        else
            pref = ""
        end
        pref += "Arr" if arr
        pref += sort unless sort == "var"
        pref += "Fld" if sort == "var" and @kind == Con::CLASS
        name = pref
        name += $names[pref].to_s unless $names[pref] == 0
        $names[pref] += 1
        #name+="_"+@ID.to_s
        name
    end

    #------------------------------------------------------
    def registerVar(var, localFlag=false, name=nil)
        if @kind == Con::CLASS or @kind == Con::METH or localFlag or var.flag?(Nam::BLOCK)
            if var.type != 'Object' or var.instance_of?(Arr)
                (var.instance_of?(Var) ? @varList : @arrList) << var unless var.flag?(Nam::AUX)
            else
                var.className=$globalContext.getClass($conf.p_class_reuse, self) if var.className.nil?
                @objList[var.className] = Array.new if @objList[var.className].nil?
                @objList[var.className] << var unless var.flag?(Nam::AUX)
            end
            #sort="var_"+@ID.to_s
            name = genUniqueName("var", var.type, var.instance_of?(Arr)) if name.nil?
            #name = genUniqueName(sort, var.type, var.instance_of?(Arr)) if name.nil?
            #dputs("Var "+name+" registered for the context "+@ID.to_s,true,10)
            return name;
        else
            @parent.registerVar(var, false, name)
        end
    end

    #-----------------------------------------------------------
    #TODO extend
    def registerClass(cls)
        $globalContext.classList << cls
    end

    #------------------------------------------------------
    # declarations of scalar vars and arrays + initializing arrays
    def genDeclarations
        res = ""
        #res += "// Context # "+@ID.to_s+"\n"
        declarations = Hash.new("")
        classDeclarations = []
        debug1="//["
        (@varList + @arrList).each do |var|
            declarations[var.type] += ", " + var.gen_decl() unless ((var.flag?(Nam::ARG) or var.flag?(Nam::CLS) or var.flag?(Nam::SUB)) or var.flag?(Nam::LOC))
            classDeclarations << var.gen_modifiers() + var.type + " " + var.gen_decl() if (var.flag?(Nam::CLS) and !var.flag?(Nam::SUB))
            debug1+=var.name+", "
        end
        debug="//{"
        (@objList).each do |cls, objs|
            debug+=cls.name+"=>["
            objs.each do |obj|
                declarations[cls.name] += ", " + obj.gen_decl() unless (obj.flag?(Nam::ARG) or obj.flag?(Nam::CLS) or obj.flag?(Nam::SUB) or obj.flag?(Nam::LOC))
                classDeclarations << obj.gen_modifiers() + obj.className.name + " " + obj.gen_decl() if (obj.flag?(Nam::CLS) and !obj.flag?(Nam::SUB))
                debug+=obj.name+", "
                debug1+=obj.name+", "
            end
            debug+="], "
        end
        debug+="}"
        debug1+="]"
        declarations.keys.each do |varType|
            res += ln(varType + declarations[varType][1..-1] + ";")
        end
        classDeclarations.each do |decl|
            res += ln(decl + ";")
        end
        if $debug and false
            res += "\n"
            res += debug
            res += "\n"
            res += debug1
        end
        res += "\n" if @kind>=Con::METH
        init = ""
        init_count = 0
        if @kind == Con::CLASS
            init += ln("static {")
            shift(1)
        end
        @arrList.each do |arr|
            init += ln(arr.gen_init()) unless (arr.flag?(Nam::ARG) or (!arr.flag?(Nam::STAT) and @kind==Con::CLASS))
            init_count += 1 unless (arr.flag?(Nam::ARG) or (!arr.flag?(Nam::STAT) and @kind==Con::CLASS))
        end
        if @kind == Con::CLASS
            shift(-1)
            init += ln("}")
        end
        res += init + "\n" if init_count > 0 and @kind>=Con::METH
        res
    end

    #------------------------------------------------------
    # calculating and printing out check sums of scalar vars and arrays
    def genResPrint
        res = "\n"
        names = vals = ""
        count = 0
        (@varList + @arrList + @objList.values().flatten + $globalContext.classList).each do |var|
            next if var.is_a?(JavaClass) and var.name=="Test"
            names += var.name + " "
            vals += ' + "," + ' + var.gen_checkSum()
            if (count += 1) % 3 == 0
                res += ln('FuzzerUtils.out.println("' + names + '= "' + vals[6..-1] + ');')
                names = vals = ""
                count = 0
            end
        end
        res += ln('FuzzerUtils.out.println("' + names + '= "' + vals[6..-1] + ');') if count > 0
        return res
    end

    #------------------------------------------------------
    def genMethCheckSum
        res = (@varList + @arrList + @objList.values().flatten).collect { |var| var.gen_checkSum() }.join(" + ")
        return (res.empty? ? "0" : res)
    end
end

#===============================================================================
# test class representation
class JavaClass
    attr_reader :context
    attr_reader :methList
    attr_reader :headFlag
    attr_reader :isRunnable
    attr_reader :methMain
    attr_reader :methMainTest
    attr_reader :name
    attr_reader :constructor
    attr_reader :classMembers
    attr_reader :num_methods
    attr_reader :extendsClass
    attr_reader :childClassList

    def initialize(headClass, outer_callers=[], inner_callees=[])
        @num_methods = 0
        @isRunnable = false
        @headFlag = headClass
        @context = Context.new($globalContext, Con::CLASS, nil, self)
   
        if @headFlag
            @name = $conf.mainClassName
            $globalContext.class_=self
        else
            @name = @context.parent.genUniqueName("Cls")
        end
        $globalContext.registerClass(self)
        @methList = []
        @classMembers = [] # additional/auxiliary members added while generating stmts
        @childClassList = []
        @extendsClass=nil
        if $globalContext.classList.size  > 0  and prob($conf.p_extends_class)
            tempCls = wrand($globalContext.classList)
            @extendsClass = tempCls
            #prevent cycled inheritance
            while tempCls.extendsClass != nil
               if tempCls.extendsClass.name == @name
                    @extendsClass=nil
                    break
                end
               tempCls = tempCls.extendsClass 
            end
        end
        tempCls=@extendsClass
        while tempCls != nil
            tempCls.childClassList << self
            tempCls=tempCls.extendsClass
        end
        Var.new(@context, "long", Nam::CLS|Nam::PUB|Nam::STAT, nil, "instanceCount")
        @auxMemFlags = Hash.new(false)
        if @headFlag
            @constructor=JavaMethod.new(self, '', false, false, false, true, outer_callers, inner_callees, true) #if !@headFlag
            @methMain = JavaMethod.new(self, "void", true, false, true, false, [@constructor])
            @methMainTest = JavaMethod.new(self, "void", false, true, true, false)
        end
        @constructor=nil
        if !@headFlag and prob($conf.p_constructor) and $globalContext.getCallersHashDepth() < $conf.max_callers_chain
            @constructor=JavaMethod.new(self, '', false, false, false, true, outer_callers, inner_callees)
            @methList << @constructor
        else
            @constructor=JavaMethod.new(self, '', false, false, false, true, outer_callers, inner_callees, true) if !@headFlag
        end

        if not @extendsClass.nil? and not @constructor.nil? and not @extendsClass.constructor.nil?
           @context.addMethodCaller( @constructor, @extendsClass.constructor, nil) 
        end

    end

    def runnable?()
        return isRunnable
    end

    def gen_checkSum
        return @name+".instanceCount";
    end

    def setConstructor(method)
        @constructor=method
    end

    def isExtendedBy?(cls)
        if cls == nil 
            return false
        end
        tempCls = cls
        while tempCls.extendsClass != nil
            if tempCls.extendsClass == self
                return true
            else
                tempCls = tempCls.extendsClass
            end
        end
        return false
    end

# can we create field of type cls2 in class cls1?
# returns true if there are no cyclic inheritance issues
    def JavaClass.noCyclicInheritanceCheck?(cls1, cls2)
        if cls2.nil? 
            return false # not sure what it means, but it's better to not create field if in doubt
        end

        if (cls1 == cls2)
            return false
        end

# parent type shouldn't have fields of children types
        if cls1.isExtendedBy?(cls2)
            return false
        end

# if cls2 has field of type cls1 or if cls2 parents have fields of type cls1 or cls1's children then we will end up with cycled inheritance issue
        tmp = cls2
        while !tmp.nil?
            if tmp == cls1
                #detected cyclic inheritance when trying to create field of type cls2 in class cls1
                return false
            else
                tmp.context.objList.each do |cls, objs|
                    objs.each do |obj|
                        if obj.className == cls1 || cls1.isExtendedBy?(obj.className) || !JavaClass.noCyclicInheritanceCheck?(cls1, obj.className) # recursive call!!!!
                            #detected cyclic inheritance when trying to create field of type cls2 in class cls1
                            return false
                        end
                    end
                end
            end
            tmp = tmp.extendsClass
        end
        return true
    end


# can we create field of type cls2 in class cls1?
# returns true if there are no cyclic inheritance issues
    def JavaClass.noCyclicInheritanceCheck_?(cls1, cls2)
        if cls2.nil? 
            return false # not sure what it means, but it's better to not create field if in doubt
        end

        if (cls1 == cls2)
            return false
        end

# parent type shouldn't have fields of children types
        if cls1.isExtendedBy?(cls2)
            return false
        end

# if cls2 has field of type cls1 or if cls2 parents have fields of type cls1 or cls1's children then we will end up with cycled inheritance issue
        tmp = cls2
        while !tmp.nil?
            if tmp == cls1
                #detected cyclic inheritance when trying to create field of type cls2 in class cls1
                return false
            else
                tmp.context.objList.each do |cls, objs|
                    objs.each do |obj|
                        if obj.className == cls1 || cls1.isExtendedBy?(obj.className)#|| !JavaClass.noCyclicInheritanceCheck?(cls1, obj.className) # recursive call!!!!
                            #detected cyclic inheritance when trying to create field of type cls2 in class cls1
                            return false
                        end
                        cls1.childClassList.each do |cls1_child|
                            if obj.className == cls1_child
                            #detected cyclic inheritance when trying to create field of type cls2 in class cls1
                                return false
                            end
                        end

                    end
                end
            end
            tmp = tmp.extendsClass
        end
        return true
    end





    #------------------------------------------------------
    # add generated auxiliary class members to this class
    def addClassMembers(what, flagName="")
        @classMembers += what if flagName.empty? or !@auxMemFlags[flagName]
        @auxMemFlags[flagName] = true unless flagName.empty?
    end

    #------------------------------------------------------
    # add run() method for Runnable object
    def getRunMethod(forMeth)
        if runnable?
            met=@methList.select() { |m| m.runFlag }[0]
        else
            @isRunnable=true
            @num_methods += 1
            met=JavaMethod.new(self, 'void', false, false, false, false, [forMeth, @constructor], [], false, true)
            @methList<<met
        end
        return met
    end

    #------------------------------------------------------
    # find/add small method
    def getSmallMethod(forMeth)
        met=wrand(@methList.select() { |m| m.small }) if prob($conf.p_meth_reuse)
        if met.nil?
            @num_methods += 1
            met=JavaMethod.new(self, 'void', false, false, true, false, [forMeth, @constructor], [], false, false, true)
            @methList<<met
        end
        return met
    end

    #------------------------------------------------------
    # returns a method instance for invocation: either new or from the list or nil
    # type=nil means any type but void
    def getMethod(forMeth, type=nil)
        if $globalContext.getCallersHashDepth(forMeth) >=$conf.max_callers_chain || ($conf.mode == 'olddefault' && !forMeth.mainFlag) || ($conf.mode == 'olddefault' && !forMeth.mainTestFlag)
            return nil
        end
        if (!prob($conf.p_meth_reuse) and @methList.size < $conf.max_meths and @num_methods < $conf.max_meths)
            @num_methods += 1
            @methList << JavaMethod.new(self, type, false, false, !prob($conf.p_non_static_method)||forMeth.static, false, [forMeth])
            return @methList[-1]
        end
        matching=[]
        $globalContext.classList.each() do |cls|
            matching+=cls.methList.find_all { |meth|
                (meth.type == type or (meth.type != "void" and !type)) and
                    !forMeth.caller?(meth) and #$globalContext.canAddCaller?(meth, forMeth) and
                    (meth.static or !$globalContext.caller?(meth.methClass.constructor, forMeth)) and
                    !meth.constructorFlag and
                    (meth.static or (meth.static == forMeth.static)) # added in attempts to resolve issue with instant methods being called from static context
            }
        end
        return wrand(matching) if matching.size > 0
        return nil if @methList.size >= $conf.max_meths || @num_methods >= $conf.max_meths
        @num_methods += 1
        @methList << JavaMethod.new(self, type, false,  false, !prob($conf.p_non_static_method)||forMeth.static, false, [forMeth])
        return @methList[-1]
    end

    #------------------------------------------------------
    # generate printing check sums calculated globally
    def genGlobCheckSums
        res = ""
        @methList.each do |meth|
            next if meth.mainTestFlag
            res += ln('FuzzerUtils.out.println("' + meth.resFieldName + ': " + ' + meth.resFieldName + ');')
        end
        res += ln('FuzzerUtils.out.println("' + STAT_INT_FLD_NAME + ': " + ' +
                      STAT_INT_FLD_NAME + ');') if @auxMemFlags[INL_METH_FLAG]
        res
    end

    #------------------------------------------------------
    # generate all the declarations of the class
    def gen
        res = ""
        res += ln((@headFlag ? "public " : "")+"class " + @name + (@extendsClass != nil ? " extends " + @extendsClass.name : "") + (runnable? ? " implements Runnable " : "") + " {") + "\n"
        shift(1)
        res += ln("public static final int " + MAX_TRIPNM + " = " + $conf.max_size.to_s + ";") + "\n"
        res += @context.genDeclarations()
        res += @classMembers.collect { |st| ln(st) }.join() + "\n" unless @classMembers.empty?

        while (meth = @methList.detect { |m| !m.genFlag and !m.fictive })
            res += meth.gen() + "\n"
        end
        res += @methMainTest.gen() if @headFlag
        res += @methMain.gen() if @headFlag
        shift(-1)
        res + ln("}")
    end
end # class JavaClass

#===============================================================================
# globally available utilities
#===============================================================================

#------------------------------------------------------
# returns the count of loops in the hierarchy of nested statements beginning with given statement
def loopDepth(stmt)
    res = 0
    while (stmt)
        res += 1 if stmt.loopFlag
        stmt = stmt.parent
    end
    res
end

#------------------------------------------------------
def countNestedStmts(stmt)
    return [0, 0] if stmt.instance_of?(String) or !stmt.compoundFlag
    stmtCount = loopCount = 0
    stmt.nestedStmts.each_value do |list|
        list.each do |st|
            stat = countNestedStmts(st)
            stmtCount += (1 + stat[0])
            loopCount += ((stmt.loopFlag ? 1 : 0) + stat[1])
        end
    end
    [stmtCount, loopCount]
end

#------------------------------------------------------
# determines if the given class complies with criteria for being generated
def strongEnough?(classDef)
    stmtCount = loopCount = 0
    (classDef.methList + (classDef.headFlag ? [classDef.methMain] : [])+ (classDef.headFlag ? [classDef.methMainTest] : [])).each do |meth|
        stat = countNestedStmts(meth.rootStmt)
        stmtCount += stat[0]
        loopCount += stat[1]
    end
    stmtCount > $conf.max_stmts * 2 / 3 and loopCount > 1
end

$indent = 0 # depth of indentation while generating text

# form a line of output file
def ln(str)
    (' ' * TABN) * $indent + str + "\n"
end

# change indentation depth
def shift(indentChange)
    $indent += indentChange
end

def wrapLines(text)
    res = ""
    text.each_line do |line|
        line = line[(line[/^\s*/].size / $conf.max_shift).to_i * $conf.max_shift..-1] # reduce too big indentation
        if (line.size <= $conf.width+1)
            res += line
            next
        end
        shift = line[/^\s*/]
        comment_flag = line[shift.size..shift.size+1] == "//"
        tail = line[0..$conf.width-1][/\s\S*$/].size # should be at least one space
        quotes = line[0..$conf.width-1-tail].scan(/"/).count
        add_quotes=(quotes % 2 == 0)
        add_quotes_1 = (add_quotes ? "" : "\"+")
        add_quotes_2 = (add_quotes ? "" : "\"")
        res += line[0..$conf.width-1-tail] +add_quotes_1+"\n"
        line = line[$conf.width-tail+1..-1]
        shift = shift + (comment_flag ? "// " : " " * TABN)
        len = $conf.width - shift.size
        until (line.size <= len + 1)
            subLen = (line[0..len-1][/\s\S*$/] ? len : line[/^\S*\s/].size) # if no spaces
            tail = line[0..subLen-1][/\s\S*$/].size
            quotes = line[0..subLen-1-tail].scan(/"/).count
            add_quotes_2 = (add_quotes ? "" : "\"")
            add_quotes = ((quotes % 2 == 0) && add_quotes) || ((quotes % 2 != 0) && !add_quotes)
            add_quotes_1 = (add_quotes ? "" : "\"+")
            res += shift + add_quotes_2 + line[0..subLen-1-tail] + add_quotes_1 + "\n"
            line = line[subLen-tail+1..-1]
        end
        add_quotes_2 = (add_quotes ? "" : "\"")
        res += shift + add_quotes_2 + line if line[/\S/]
    end
    return res
end

def wrapLines_old(text)
    res = ""
    text.each_line do |line|
        line = line[(line[/^\s*/].size / $conf.max_shift).to_i * $conf.max_shift..-1] # reduce too big indentation
        if (line.size <= $conf.width+1)
            res += line
            next
        end
        shift = line[/^\s*/]
        comment_flag = line[shift.size..shift.size+1] == "//"
        tail = line[0..$conf.width-1][/\s\S*$/].size # should be at least one space
        res += line[0..$conf.width-1-tail] +"\n"
        line = line[$conf.width-tail+1..-1]
        shift = shift + (comment_flag ? "// " : " " * TABN)
        len = $conf.width - shift.size
        until (line.size <= len + 1)
            subLen = (line[0..len-1][/\s\S*$/] ? len : line[/^\S*\s/].size) # if no spaces
            tail = line[0..subLen-1][/\s\S*$/].size
            res += shift + line[0..subLen-1-tail] +"\n"
            line = line[subLen-tail+1..-1]
        end
        res += shift + line if line[/\S/]
    end
    return res
end

def error(message, internal=false, fatal=true)
    puts "Fuzzer: " + (internal ? "Internal error" : "Error") + "! #{message}"
    exit if fatal
end

def prob(percent)
    mrand(100) < percent
end

def mrand(n=0)
    r = rand(n)
    r = 0 if r < 1
    return r
end

def wrand(list)
    list[mrand(list.size)]
end

def rCharacter
    wrand(LETTER + DIGIT)
end

def rLetter
    wrand(LETTER)
end

def rLiteralObject(className=nil, context=nil, not_null=false)
    return "null" if !not_null and prob($conf.p_null_literal)
     klass = nil
     if !context.nil?
        if !context.class_.nil?
            klass = context.class_
        else
            if !context.method.nil? 
                klass = context.method.methClass
            end
        end
    end

    if className.nil?
        if context.nil?
            if ($globalContext.classList.size()>0)
                clsList=$globalContext.classList.find_all { |cls| cls.extendsClass.nil? }
            end
            if clsList.size() > 0
                class1=wrand(clsList)
                class2=class1
            else
                return "null"
            end
        else
            clsList=$globalContext.classList.find_all { |cls| (klass != nil and cls != klass and !klass.childClassList.include?(cls) and JavaClass.noCyclicInheritanceCheck_?(klass, cls)) or  (((context == nil) or (klass == nil)) and cls.extendsClass.nil? )}
            class2=wrand(clsList)
            class1=class2.nil? ? $globalContext.getClass($conf.p_class_reuse, context) : class2
        end

    else
        class1 = className
        clsList = class1.childClassList.find_all { |cls| (klass != nil and cls != klass and !klass.childClassList.include?(cls) and JavaClass.noCyclicInheritanceCheck_?(klass, cls)) or (((context == nil) or (klass == nil)) and cls.extendsClass.nil? )}

        class2=wrand(clsList)
        class1=class2.nil? ? class1 : wrand([class1, class2])
        # if we know that this is a local var in non-constructor method, then it's ok?:
        if (klass != nil) and (class1 == klass or klass.childClassList.include?(class1)) and !(!context.nil? and !context.method.nil? and !context.method.constructorFlag)
            return "null"
        end
    end
    
    if !context.nil? 
        if  !context.method.nil?
            caler=context.method
        elsif !context.class_.nil?
            caler=context.class_.constructor
        end

        context.addMethodCaller(caler, class1.constructor, nil) if !caler.nil? and !class1.constructor.nil?
   end
    return "new " + class1.name  + "()"
end

def rLiteralArray(className, context=nil, not_null=false)
    #return "null" if !not_null and prob($conf.p_null_literal)
    size=(className.size==0 ? MAX_TRIPNM : className.size)
    return "FuzzerUtils."+className.type.to_s+className.dim.to_s+"array("+size.to_s+", ("+className.type+")"+rLiteral(className.type, nil, context, true)+")"
end

def rLiteral(type, className=nil, context=nil, not_null=false)
    case type
        when "int"
            range = wrand([0xF, 0xFF, $conf.max_num])
            v = rand(range)
        when "long"
            range = wrand([0xF, 0xFF, 0xFFFF, 0xFFFFFFFF, 0x7FFFFFFFFFFFFFFF])
            return (rand(range) * (2 * rand(2) - 1)).to_s + "L"
        when "double"
            range = wrand([3, 128])
            return (rand(range) * (2 * rand(2) - 1)).to_s + "." + rand(1024*128).to_s
        when "float"
            range = wrand([3, 128])
            return (rand(range) * (2 * rand(2) - 1)).to_s + "." + rand(1024).to_s + "F"
        when "short"
            v = rand(32768)
        when "char"
            return rand(65536).to_s # should not be multiplied by -1
        when "byte"
            v = rand(128)
        when "boolean"
            return wrand(['true', 'false']) # should not be multiplied by -1
        when "String"
            return wrand(['"one"', '"two"', '"three"', '"four"']) # should not be multiplied by -1
        when 'Object'
            return rLiteralObject(className, context, not_null)
        when 'Array'
            return rLiteralArray(className, context, not_null)
        else
            error("rLiteral: type = " + type, true)
    end
    (v * (2 * rand(2) - 1)).to_s # multiply by -1 or +1
end

def arrNames(arr)
    "["+arr.inject("") { |ret, v| ret+=", "+v.name }+"]"
end

def hashNames(arr)
    ret="{"
    arr.each() do |cls, vars|
        ret+=cls.name+": ["+vars.inject("") { |r, v| r+=", "+v.name }+"], "
    end
    ret+="}"
    return ret
end

def printMethodCallers(hash=nil, reportDepth=true, reportClass=true, reportStatic=true)
    return if !$debug
    hash=$globalContext.methodCallers if hash.nil?
    hash.each do |key, vals|
        dputs((key.nil? ? "nil" : key.name)+" -> "+vals.inject("") { |str, el| str+=" "+(el.nil? ? "nil" : el.name) }, false)
    end
    dputs("Depth = "+$globalContext.getCallersHashDepth(nil, hash).to_s, false) if reportDepth
    dputs("Classes = "+$globalContext.classList.size().to_s, false) if reportClass
    dputs("static objects = "+hashNames($globalContext.statObjList()), false) if reportStatic
end

def printCallersDepth(hash=nil)
    hash=$globalContext.methodCallers if hash.nil?
    dputs "Depth = "+$globalContext.getCallersHashDepth(nil, hash).to_s
end

def dputs(message="", printPlace=true, stackDepth=1)
    if $debug
        str=""
        [2..stackDepth+1].each do |elem|
            str=str+":"+caller[elem].to_s().gsub(/,/, "\n//")
        end
        puts "//DEBUG " + (printPlace ? "(Called in "+str+")" : "")+" "+message
    end
end
