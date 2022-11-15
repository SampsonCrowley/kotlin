// TARGET_BACKEND: JS_IR
// TARGET_BACKEND: JS_IR_ES6
// RUN_PLAIN_BOX_FUNCTION
// INFER_MAIN_MODULE

// WITH_STDLIB

// FILE: lib.kt
// MODULE: js_export_and_serializable

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*

@JsExport
@Serializable
class Basic(val name: String, val age: UInt)

@JsExport
@Serializable
class WithComapnion(val name: String, val age: UInt) {
    companion object {
        fun foo() = "Test"
    }
}

// FILE: test.js
function box() {
    const { Basic, WithCompanion } = this.js_export_and_serializable;

    if (Basic.Companion !== undefined) return "fail: Basic should not export synthetic companion";

    const basic = new Basic("Test", 44);

    if (basic.name !== "Test") return "fail: something wrong with a simple string field access";
    if (basic.age !== 44) return "fail: something wrong with a simple uint field access";

    if (WithComapnion.Companion === undefined) return "fail: User defined companion should be exported";
    if (WithComapnion.Companion.serializer !== undefined) return "fail: serializer fabric should not be exported";

    const withCompanion = new WithCompanion("Test", 44);

    if (withCompanion.name !== "Test") return "fail: something wrong with a simple string field access";
    if (withCompanion.age !== 44) return "fail: something wrong with a simple uint field access";
    if (WithComapnion.Companion.foo() !== "Test") return "fail: something wrong with user defined function inside companion"

    return "OK"
}
