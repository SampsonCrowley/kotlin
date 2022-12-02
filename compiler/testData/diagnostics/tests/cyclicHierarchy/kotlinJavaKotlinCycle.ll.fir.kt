// LL FIR diverges from the errors expected in `kotlinJavaKotlinCycle.fir.kt`. Because the compiler doesn't guarantee exhaustiveness in
// reporting of inheritance cycles, the divergence is valid.

// FILE: I.kt

open class I : <!CYCLIC_INHERITANCE_HIERARCHY!>K<!>() {
    fun foo() {}
}

// FILE: J.java

class J extends I {
    void bar() {}
}

// FILE: K.kt

open class K : <!CYCLIC_INHERITANCE_HIERARCHY!>J<!>() {
    fun baz() {}
}
