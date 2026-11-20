package tk.glucodata

/**
 * Stand-in for JugglucoNG's `tk.glucodata.Log`, which the vendored Sibionics code calls only for trace lines.
 * In AAPS these lines go nowhere: the source plugin logs what matters itself.
 */
object Log {

    fun i(tag: String?, msg: String?) = Unit
    fun d(tag: String?, msg: String?) = Unit
    fun w(tag: String?, msg: String?) = Unit
    fun e(tag: String?, msg: String?) = Unit
}
