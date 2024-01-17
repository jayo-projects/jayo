/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 * 
 * Forked from kotlinx-io (https://github.com/Kotlin/kotlinx-io), original copyright is below
 * 
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

@file:JvmName("-Sink") // A leading '-' hides this class from Java.

package jayo

/**
 * Writes two bytes containing a short, in the little-endian order, to this sink.
 * ```
 * val buffer = buffer()
 * buffer.writeShortLe(0x1234)
 * assertEquals(0x3412, buffer.readShort())
 * ```
 *
 * @param s the short to be written.
 * @throws IllegalStateException if this sink is closed.
 */
public fun Sink.writeShortLe(s: Short) {
    this.writeShort(java.lang.Short.reverseBytes(s))
}

/**
 * Writes four bytes containing an int, in the little-endian order, to this sink.
 * ```
 * val buffer = buffer()
 * buffer.writeIntLe(0x12345678)
 * assertEquals(0x78563412, buffer.readInt())
 * ```
 *
 * @param i the int to be written.
 * @throws IllegalStateException if this sink is closed.
 */
public fun Sink.writeIntLe(i: Int) {
    this.writeInt(Integer.reverseBytes(i))
}

/**
 * Writes eight bytes containing a long, in the little-endian order, to this sink.
 * ```
 * val buffer = buffer()
 * buffer.writeLongLe(0x123456789ABCDEF0)
 * assertEquals(0xF0DEBC9A78563412U.toLong(), buffer.readLong())
 * ```
 *
 * @param l the long to be written.
 * @throws IllegalStateException if this sink is closed.
 */
public fun Sink.writeLongLe(l: Long) {
    this.writeLong(java.lang.Long.reverseBytes(l))
}

/**
 * Writes an unsigned byte to this sink.
 * ```
 * val buffer = buffer()
 * buffer.writeUByte(255U)
 * assertContentEquals(byteArrayOf(-1), buffer.readByteArray())
 * ```
 *
 * @param b the byte to be written.
 * @throws IllegalStateException if this sink is closed.
 */
public fun Sink.writeUByte(b: UByte) {
    writeByte(b.toByte())
}

/**
 * Writes two bytes containing an unsigned short, in the big-endian order, to this sink.
 * ```
 * val buffer = buffer()
 * buffer.writeUShort(65535U)
 * assertContentEquals(byteArrayOf(-1, -1), buffer.readByteArray())
 * ```
 *
 * @param s the unsigned short to be written.
 * @throws IllegalStateException if this sink is closed.
 */
public fun Sink.writeUShort(s: UShort) {
    writeShort(s.toShort())
}

/**
 * Writes four bytes containing an unsigned int, in the big-endian order, to this sink.
 * ```
 * val buffer = buffer()
 * buffer.writeUInt(4294967295U)
 * assertContentEquals(byteArrayOf(-1, -1, -1, -1), buffer.readByteArray())
 * ```
 *
 * @param i the unsigned int to be written.
 * @throws IllegalStateException if this sink is closed.
 */
public fun Sink.writeUInt(i: UInt) {
    writeInt(i.toInt())
}

/**
 * Writes eight bytes containing an unsigned long, in the big-endian order, to this sink.
 * ```
 * val buffer = buffer()
 * buffer.writeULong(18446744073709551615UL)
 * assertContentEquals(byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1), buffer.readByteArray())
 * ```
 * @param l the unsigned long to be written.
 * @throws IllegalStateException if this sink is closed.
 */
public fun Sink.writeULong(l: ULong) {
    writeLong(l.toLong())
}

/**
 * Writes two bytes containing an unsigned short, in the little-endian order, to this sink.
 * ```
 * val buffer = buffer()
 * buffer.writeUShortLe(0x1234U)
 * assertEquals(0x3412U, buffer.readUShort())
 * ```
 * @param s the unsigned short to be written.
 * @throws IllegalStateException if this sink is closed.
 */
public fun Sink.writeUShortLe(s: UShort) {
    writeShortLe(s.toShort())
}

/**
 * Writes four bytes containing an unsigned int, in the little-endian order, to this sink.
 * ```
 * val buffer = buffer()
 * buffer.writeUIntLe(0x12345678U)
 * assertEquals(0x78563412U, buffer.readUInt())
 * ```
 * @param i the unsigned int to be written.
 * @throws IllegalStateException if this sink is closed.
 */
public fun Sink.writeUIntLe(i: UInt) {
    writeIntLe(i.toInt())
}

/**
 * Writes eight bytes containing an unsigned long, in the little-endian order, to this sink.
 * ```
 * val buffer = buffer()
 * buffer.writeULongLe(0x123456789ABCDEF0U)
 * assertEquals(0xF0DEBC9A78563412U, buffer.readULong())
 * ```
 * @param l the unsigned long to be written.
 * @throws IllegalStateException if this sink is closed.
 */
public fun Sink.writeULongLe(l: ULong) {
    writeLongLe(l.toLong())
}
