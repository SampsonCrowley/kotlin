// FIR_IDENTICAL
// ISSUE: KT-54920

sealed interface I {
    class C : I
}

fun foo(x: I): Int {
    val a = when (x) { is I.C -> 1 }
    val b = when (x) { <!USELESS_IS_CHECK!>is I.C<!> -> 2 }
    return a + b
}
