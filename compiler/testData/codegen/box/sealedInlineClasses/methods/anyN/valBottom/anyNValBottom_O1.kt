
// IGNORE_BACKEND: JVM
// WITH_STDLIB
// WORKS_WHEN_VALUE_CLASS
// LANGUAGE: +ValueClasses, +SealedInlineClasses

OPTIONAL_JVM_INLINE_ANNOTATION
sealed value class I1

value object O1: I1() {
    val str: String
        get() = "O1"
}

OPTIONAL_JVM_INLINE_ANNOTATION
sealed value class I2: I1()

value object O2: I2()

OPTIONAL_JVM_INLINE_ANNOTATION
value class I3(val value: Any?): I2()


fun <T> boxValue(v: T): T = v

fun <T: I1> coerceToI1(v: T): I1 = v

fun <T: I2> coerceToI2(v: T): I2 = v


fun box(): String {
    var res: String = ""
    res = O1.str
    if (res != "O1") return "FAIL 1: $res"

    res = boxValue(O1).str
    if (res != "O1") return "FAIL 2: $res"

    return "OK"
}
