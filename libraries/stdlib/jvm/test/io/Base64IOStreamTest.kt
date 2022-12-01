/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package test.io

import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.wrapForDecoding
import kotlin.io.encoding.wrapForEncoding
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Base64IOStreamTest {

    private fun testCoding(codec: Base64, text: String, encodedText: String) {
        val encodedBytes = ByteArray(encodedText.length) { encodedText[it].code.toByte() }
        val bytes = ByteArray(text.length) { text[it].code.toByte() }
        encodedBytes.inputStream().wrapForDecoding(codec).use { inputStream ->
            assertEquals(text, inputStream.reader().readText())
        }
        encodedBytes.inputStream().wrapForDecoding(codec).use { inputStream ->
            assertContentEquals(bytes, inputStream.readBytes())
        }
        ByteArrayOutputStream().let { outputStream ->
            outputStream.wrapForEncoding(codec).use {
                it.write(bytes)
            }
            assertContentEquals(encodedBytes, outputStream.toByteArray())
        }
    }

    @Test
    fun base64() {
        fun testBase64(text: String, encodedText: String) {
            testCoding(Base64, text, encodedText)
        }

        testBase64("", "")
        testBase64("f", "Zg==")
        testBase64("fo", "Zm8=")
        testBase64("foo", "Zm9v")
        testBase64("foob", "Zm9vYg==")
        testBase64("fooba", "Zm9vYmE=")
        testBase64("foobar", "Zm9vYmFy")
    }
}