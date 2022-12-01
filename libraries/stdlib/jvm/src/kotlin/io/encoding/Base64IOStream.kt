/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("UnusedReceiverParameter")

package kotlin.io.encoding

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
//import java.io.Reader
//import java.io.Writer


//public fun Reader.wrapForDecoding(base64: Base64): InputStream { TODO() }
//public fun Writer.wrapForEncoding(base64: Base64): OutputStream { TODO() }

/**
 * Returns an input stream that wraps this input stream for decoding symbol stream using the specified [base64].
 *
 * Reading from the returned input stream leads to reading some symbols from the underlying input stream.
 * The symbols are decoded using the specified [base64] and the resulting bytes are returned.
 *
 * Closing the returned input stream will close the underlying input stream.
 */
public fun InputStream.wrapForDecoding(base64: Base64): InputStream {
    return Base64InputStream(this, base64)
}

/**
 * Returns an output stream that wraps this output stream for encoding byte data using the specified [base64].
 *
 * The byte data written to the returned output stream is encoded using the specified [base64]
 * and the resulting symbols are written to the underlying output stream.
 *
 * The returned output stream should be promptly closed after use,
 * during which it will flush all possible leftover symbols to the underlying
 * output stream. Closing the returned output stream will close the underlying
 * output stream.
 */
public fun OutputStream.wrapForEncoding(base64: Base64): OutputStream {
    return Base64OutputStream(this, base64)
}


private class Base64InputStream(
    private val input: InputStream,
    private val base64: Base64
) : InputStream() {
    private val bitsPerByte = Byte.SIZE_BITS
    private val bitsPerSymbol = 6

    private val bytesPerGroup: Int = 3
    private val symbolsPerGroup: Int = 4

    private var isClosed = false
    private var isEOF = false
    private val singleByteBuffer = ByteArray(1)

    private val symbolBuffer = ByteArray(1024)
    private var symbolBufferOffset = 0
    private var symbolBufferLength = 0
    private val symbolBufferEndIndex: Int
        get() = symbolBufferOffset + symbolBufferLength

    private val byteBuffer = ByteArray(1024)
    private var byteBufferOffset = 0
    private var byteBufferLength = 0
    private val byteBufferEndIndex: Int
        get() = byteBufferOffset + byteBufferLength

    override fun read(): Int {
        return if (read(singleByteBuffer, 0, 1) == -1) -1 else singleByteBuffer[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset + length > buffer.size) {
            throw IndexOutOfBoundsException("offset: $offset, length: $length, buffer size: ${buffer.size}")
        }
        if (isClosed) {
            throw IOException("The input stream is closed.")
        }
        if (isEOF) {
            return -1
        }
        if (length == 0) {
            return 0
        }

//        println("offset: $offset, length: $length, byteBufferLength: $byteBufferLength")

        if (byteBufferLength >= length) {
            byteBuffer.copyInto(buffer, offset, byteBufferOffset, offset + length)
            byteBufferOffset += length
            byteBufferLength -= length
            return length
        }

        val bytesNeeded = length - byteBufferLength
        val groupsNeeded = (bytesNeeded + bytesPerGroup - 1) / bytesPerGroup
        var symbolsNeeded = groupsNeeded * symbolsPerGroup

//        println("bytesNeeded: $bytesNeeded, groupsNeeded: $groupsNeeded, symbolsNeeded: $symbolsNeeded")

        var bufferOffset = offset

        while (!isEOF && symbolsNeeded > 0) {
            var symbolsToRead = minOf(symbolBuffer.size - symbolBufferEndIndex, symbolsNeeded - symbolBufferLength)
            while (!isEOF && symbolsToRead > 0) {
                val symbolsRead = input.read(symbolBuffer, symbolBufferEndIndex, symbolBuffer.size - symbolBufferEndIndex)
                if (symbolsRead == -1) {
                    isEOF = true
                    break
                }

//                println("symbolsToRead: $symbolsToRead, symbolsRead: $symbolsRead, symbolBufferLength: $symbolBufferLength")

                symbolBufferLength += symbolsRead
                symbolsToRead -= symbolsRead
            }

            check(isEOF || symbolBufferLength % symbolsPerGroup == 0)

            symbolsNeeded -= symbolBufferLength

            byteBufferLength += base64.decode(symbolBuffer, byteBuffer, byteBufferOffset, symbolBufferOffset, symbolBufferEndIndex)
            symbolBufferOffset += symbolBufferLength
            symbolBufferLength = 0

//            println("byteBufferOffset: $byteBufferOffset, byteBufferLength: $byteBufferLength, symbolsNeeded: $symbolsNeeded")

            val bytesToCopy = minOf(byteBufferLength, length - (bufferOffset - offset))
            byteBuffer.copyInto(buffer, bufferOffset, byteBufferOffset, bytesToCopy)

            bufferOffset += bytesToCopy
            byteBufferOffset += bytesToCopy
            byteBufferLength -= bytesToCopy

            shiftBuffersToStartIfNeeded()
        }

        check(symbolBufferLength == 0)

        return if (bufferOffset - offset == 0 && isEOF) -1 else bufferOffset - offset
    }

    override fun available(): Int {
        if (isClosed) {
            return 0
        }
        return (input.available() * bitsPerSymbol) / bitsPerByte
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            input.close()
        }
    }

    // private functions

    private fun shiftBuffersToStartIfNeeded() {
        check(symbolBufferLength == 0)
        symbolBufferOffset = 0

        if (byteBufferLength == 0) {
            byteBufferOffset = 0
        } else {
            // byte buffer should always have enough capacity to accommodate all symbols from symbol buffer
            if (symbolBuffer.size / symbolsPerGroup * bytesPerGroup > byteBuffer.size - byteBufferEndIndex) {
                byteBuffer.copyInto(byteBuffer, 0, byteBufferOffset, byteBufferEndIndex)
                byteBufferOffset = 0
            }
        }
    }
}

private class Base64OutputStream(
    private val output: OutputStream,
    private val base64: Base64
) : OutputStream() {
    private val bytesPerGroup: Int = 3
    private val symbolsPerGroup: Int = 4

    private var isClosed = false
    private val singleByteBuffer = ByteArray(1)

    private val symbolBuffer = ByteArray(1024)

    private val byteBuffer = ByteArray(1024)
    private var byteBufferLength = 0

    override fun write(b: Int) {
        singleByteBuffer[0] = b.toByte()
        write(singleByteBuffer, 0, 1)
    }

    override fun write(source: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset + length > source.size) {
            throw IndexOutOfBoundsException("offset: $offset, length: $length, source size: ${source.size}")
        }
        if (isClosed) {
            throw IOException("The output stream is closed.")
        }
        if (length == 0) {
            return
        }

//        println("offset: $offset, length = $length, byteBufferLength: $byteBufferLength")

        check(byteBufferLength < bytesPerGroup)

        var bytesWritten = 0

        if (byteBufferLength != 0) {
            val bytesToCopy = minOf(bytesPerGroup - byteBufferLength, length)
            source.copyInto(byteBuffer, destinationOffset = 0, offset, bytesToCopy)
            bytesWritten += bytesToCopy
            byteBufferLength += bytesToCopy

//            println("bytesToCopy: $bytesToCopy, bytesWritten = $bytesWritten, byteBufferLength: $byteBufferLength")

            if (byteBufferLength < bytesPerGroup) {
                return
            } else {
                check(byteBufferLength == bytesPerGroup)
                val symbolsEncoded = base64.encodeToByteArray(byteBuffer, symbolBuffer, destinationOffset = 0, startIndex = 0, byteBufferLength)
                check(symbolsEncoded == symbolsPerGroup)
                output.write(symbolBuffer, 0, symbolsPerGroup)
                byteBufferLength = 0
            }
        }

        while (bytesWritten + bytesPerGroup <= length) {
            val symbolBufferGroupCapacity = symbolBuffer.size / symbolsPerGroup
            val groupsToEncode = minOf(symbolBufferGroupCapacity, (length - bytesWritten) / bytesPerGroup)

            val symbolsEncoded = base64.encodeToByteArray(source, symbolBuffer, destinationOffset = 0, offset + bytesWritten, groupsToEncode * bytesPerGroup)
            check(symbolsEncoded == groupsToEncode * symbolsPerGroup)
            output.write(symbolBuffer, 0, symbolsEncoded)

            bytesWritten += groupsToEncode * bytesPerGroup
        }

        source.copyInto(byteBuffer, destinationOffset = 0, offset + bytesWritten, offset + length)
        byteBufferLength += length - bytesWritten
    }

    override fun flush() {
        output.flush()
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true

//            println("# byteBufferLength: $byteBufferLength")

            if (byteBufferLength != 0) {
                val symbolsEncoded = base64.encodeToByteArray(byteBuffer, symbolBuffer, destinationOffset = 0, startIndex = 0, byteBufferLength)
                check(symbolsEncoded == symbolsPerGroup)
                output.write(symbolBuffer, 0, symbolsPerGroup)
                byteBufferLength = 0
            }

            output.close()
        }
    }
}