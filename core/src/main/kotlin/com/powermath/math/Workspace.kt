package com.powermath.math

data class VarDef(val name: String, val source: String, val value: Value)

sealed class WsChange {
    data class VarSet(val def: VarDef) : WsChange()
    data class VarDelete(val name: String) : WsChange()
    data class FnSet(val def: FuncDef) : WsChange()
    data class FnDelete(val name: String) : WsChange()
}

/**
 * Mathematical workspace (persistence itself lives in the Android layer).
 * Scope policy:
 *  - Variables store the exact value computed when they were defined (a snapshot).
 *  - Functions see only their own parameters and workspace variables (looked up when called).
 *  - Names are case-sensitive; a name may be a variable or a function, never both; built-in names are reserved.
 *  - "Define" refuses to replace an existing name; "Edit"/":=" replaces explicitly.
 * Maps are replaced copy-on-write so background threads always read a consistent snapshot.
 */
class Workspace : Scope {
    @Volatile private var vars: Map<String, VarDef> = emptyMap()
    @Volatile private var funcs: Map<String, FuncDef> = emptyMap()
    @Volatile var ans: Value? = null

    val variables: List<VarDef> get() = vars.values.toList()
    val functions: List<FuncDef> get() = funcs.values.toList()

    override fun variable(name: String): Value? = if (name == "ans") ans else vars[name]?.value
    override fun function(name: String): FuncDef? = funcs[name]

    private fun checkName(name: String, kind: String) {
        if (!Names.isValidName(name)) throw MathError(ErrorKind.DEFINITION, "Invalid $kind name '$name': use letters and underscores only, starting with a letter")
        if (name in Names.reserved) throw MathError(ErrorKind.DEFINITION, "'$name' is a reserved built-in name")
    }

    fun defineVariable(name: String, source: String, overwrite: Boolean = false, angle: AngleMode = AngleMode.DEG): WsChange.VarSet {
        checkName(name, "variable")
        if (funcs.containsKey(name)) throw MathError(ErrorKind.DEFINITION, "'$name' is already a function")
        if (vars.containsKey(name) && !overwrite) throw MathError(ErrorKind.DEFINITION, "Variable '$name' already exists; use Edit or ':=' to replace it")
        val expr = Parser(this).parse(source)
        if (expr is Expr.Rel || expr is Expr.Assign || expr is Expr.FuncDefStmt) throw MathError(ErrorKind.DEFINITION, "A variable must be defined by a numeric expression")
        if (expr is Expr.Call && expr.name in Names.commands) throw MathError(ErrorKind.DEFINITION, "A variable must be defined by a numeric expression")
        val free = ExprUtil.freeVars(expr).filter { variable(it) == null }
        if (free.isNotEmpty()) throw MathError(ErrorKind.DEFINITION, "Undefined variable '${free.first()}' in the definition")
        if (ExprUtil.freeVars(expr).contains("ans")) throw MathError(ErrorKind.DEFINITION, "'ans' cannot be used in a definition")
        val value = Evaluator(this, angle, EvalLimits.withTimeoutMs(5000)).eval(expr)
        val def = VarDef(name, source.trim(), value)
        vars = LinkedHashMap(vars).also { it[name] = def }
        return WsChange.VarSet(def)
    }

    fun deleteVariable(name: String): WsChange.VarDelete {
        if (!vars.containsKey(name)) throw MathError(ErrorKind.UNDEFINED, "Variable '$name' does not exist")
        val users = funcs.values.filter { ExprUtil.freeVars(it.body).contains(name) && name !in it.params }.map { it.name }
        if (users.isNotEmpty()) throw MathError(ErrorKind.DEFINITION, "Cannot delete '$name': used by function ${users.joinToString { "'$it'" }}")
        vars = LinkedHashMap(vars).also { it.remove(name) }
        return WsChange.VarDelete(name)
    }

    fun defineFunction(name: String, params: List<String>, bodySource: String, overwrite: Boolean = false): WsChange.FnSet {
        val nameOfFn = name
        checkName(name, "function")
        if (vars.containsKey(name)) throw MathError(ErrorKind.DEFINITION, "'$name' is already a variable")
        if (funcs.containsKey(name) && !overwrite) throw MathError(ErrorKind.DEFINITION, "Function '$name' already exists; use Edit or ':=' to replace it")
        if (params.isEmpty()) throw MathError(ErrorKind.DEFINITION, "A function needs at least one parameter")
        if (params.toSet().size != params.size) throw MathError(ErrorKind.DEFINITION, "Duplicate parameter names")
        params.forEach { checkName(it, "parameter") }
        if (name in params) throw MathError(ErrorKind.DEFINITION, "A parameter cannot have the same name as the function")
        val parseScope = object : Scope {
            override fun variable(name: String): Value? = if (name in params) Value.ZERO else this@Workspace.variable(name)
            override fun function(name: String): FuncDef? =
                if (name == nameOfFn) FuncDef(nameOfFn, params, "", Expr.Num(Rat.ZERO)) else funcs[name]
        }
        val body = Parser(parseScope).parse(bodySource)
        if (body is Expr.Rel || body is Expr.Assign || body is Expr.FuncDefStmt) throw MathError(ErrorKind.DEFINITION, "A function body must be an expression")
        if (body is Expr.Call && body.name in Names.commands) throw MathError(ErrorKind.DEFINITION, "A function body cannot be a command")
        val free = ExprUtil.freeVars(body).filter { it !in params }
        for (f in free) {
            if (f == "ans") throw MathError(ErrorKind.DEFINITION, "'ans' cannot be used in a definition")
            if (!vars.containsKey(f)) throw MathError(ErrorKind.DEFINITION, "Undefined variable '$f' in the function body")
        }
        val def = FuncDef(name, params, bodySource.trim(), body)
        val newFuncs = LinkedHashMap(funcs).also { it[name] = def }
        for (called in ExprUtil.calledFunctions(body)) {
            if (called in Names.commands) throw MathError(ErrorKind.DEFINITION, "'$called' cannot be used inside a function")
            if (called !in Names.functions && !newFuncs.containsKey(called))
                throw MathError(ErrorKind.DEFINITION, "Unknown function '$called' in the function body")
            val fd = newFuncs[called]
            if (fd != null && called !in Names.functions && fd.params.size != (findCallArity(body, called) ?: fd.params.size))
                throw MathError(ErrorKind.DEFINITION, "Function '$called' called with the wrong number of arguments")
        }
        detectCycle(newFuncs, name)
        funcs = newFuncs
        return WsChange.FnSet(def)
    }

    private fun findCallArity(e: Expr, name: String): Int? = when (e) {
        is Expr.Call -> if (e.name == name) e.args.size else e.args.firstNotNullOfOrNull { findCallArity(it, name) }
        is Expr.Neg -> findCallArity(e.e, name)
        is Expr.Bin -> findCallArity(e.l, name) ?: findCallArity(e.r, name)
        is Expr.Percent -> findCallArity(e.e, name)
        is Expr.Fact -> findCallArity(e.e, name)
        else -> null
    }

    private fun detectCycle(all: Map<String, FuncDef>, start: String) {
        val path = ArrayList<String>()
        fun dfs(n: String, visiting: MutableSet<String>) {
            if (n in visiting) throw MathError(ErrorKind.DEFINITION, "Circular definition: ${(path.dropWhile { it != n } + n).joinToString(" → ")}")
            val fd = all[n] ?: return
            visiting.add(n); path.add(n)
            for (c in ExprUtil.calledFunctions(fd.body)) if (all.containsKey(c) && c !in Names.functions) dfs(c, visiting)
            visiting.remove(n); path.removeAt(path.size - 1)
        }
        for (n in all.keys) dfs(n, HashSet())
        dfs(start, HashSet())
    }

    fun deleteFunction(name: String): WsChange.FnDelete {
        if (!funcs.containsKey(name)) throw MathError(ErrorKind.UNDEFINED, "Function '$name' does not exist")
        val users = funcs.values.filter { it.name != name && ExprUtil.calledFunctions(it.body).contains(name) }.map { it.name }
        if (users.isNotEmpty()) throw MathError(ErrorKind.DEFINITION, "Cannot delete '$name': used by function ${users.joinToString { "'$it'" }}")
        funcs = LinkedHashMap(funcs).also { it.remove(name) }
        return WsChange.FnDelete(name)
    }

    fun clearVariables(): List<WsChange> {
        val users = funcs.values.filter { f -> ExprUtil.freeVars(f.body).any { it !in f.params } }
        if (users.isNotEmpty()) throw MathError(ErrorKind.DEFINITION, "Delete functions that use variables first (${users.joinToString { it.name }})")
        val ch = vars.keys.map { WsChange.VarDelete(it) }
        vars = emptyMap()
        return ch
    }

    fun clearFunctions(): List<WsChange> {
        val ch = funcs.keys.map { WsChange.FnDelete(it) }
        funcs = emptyMap()
        return ch
    }

    /** Restore persisted definitions (variables first, then functions in creation order). */
    fun restore(variables: List<Triple<String, String, String>>, functions: List<Triple<String, List<String>, String>>) {
        val v = LinkedHashMap<String, VarDef>()
        for ((name, source, valueLinear) in variables) {
            try { v[name] = VarDef(name, source, Evaluator(EmptyScope).eval(Parser(EmptyScope).parse(valueLinear))) } catch (_: MathError) {}
        }
        vars = v
        val f = LinkedHashMap<String, FuncDef>()
        val known = functions.map { it.first }.toSet()
        for ((name, params, body) in functions) {
            try {
                val sc = object : Scope {
                    override fun variable(name: String): Value? = if (name in params) Value.ZERO else v[name]?.value
                    override fun function(name: String): FuncDef? = if (name in known) FuncDef(name, emptyList(), "", Expr.Num(Rat.ZERO)) else null
                }
                f[name] = FuncDef(name, params, body, Parser(sc).parse(body))
            } catch (_: MathError) {}
        }
        funcs = f
    }
}
