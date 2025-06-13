/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 * 
 * Forked from kotlinx-io (https://github.com/Kotlin/kotlinx-io), original copyright is below
 * 
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

@file:JvmName("-Reader") // Leading '-' hides this class from Java.

package jayo

/**
 * Removes two bytes from this reader and returns a short composed of them according to the little-endian order.
 *
 * @throws JayoEOFException if there are not enough data to read a short value.
 * @throws JayoClosedResourceException if this reader is closed.
 */
public fun Reader.readShortLe(): Short {
    return java.lang.Short.reverseBytes(readShort())
}

/**
 * Removes four bytes from this reader and returns an integer composed of them according to the little-endian order.
 *
 * @throws JayoEOFException if there are not enough data to read an int value.
 * @throws JayoClosedResourceException if this reader is closed.
 */
public fun Reader.readIntLe(): Int {
    return Integer.reverseBytes(readInt())
}

/**
 * Removes eight bytes from this reader and returns a long composed of them according to the little-endian order.
 *
 * @throws JayoEOFException if there are not enough data to read a long value.
 * @throws JayoClosedResourceException if this reader is closed.
 */
public fun Reader.readLongLe(): Long {
    return java.lang.Long.reverseBytes(readLong())
}

/**
 * Removes an unsigned byte from this reader and returns it.
 *
 * @throws JayoEOFException when there are no more bytes to read.
 * @throws JayoClosedResourceException if this reader is closed.
 */
public fun Reader.readUByte(): UByte = readByte().toUByte()

/**
 * Removes two bytes from this reader and returns an unsigned short composed of them according to the big-endian order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned short value.
 * @throws JayoClosedResourceException if this reader is closed.
 */
public fun Reader.readUShort(): UShort = readShort().toUShort()

/**
 * Removes four bytes from this reader and returns an unsigned integer composed of them according to the big-endian
 * order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned int value.
 * @throws JayoClosedResourceException if this reader is closed.
 */
public fun Reader.readUInt(): UInt = readInt().toUInt()

/**
 * Removes eight bytes from this reader and returns an unsigned long composed of them according to the big-endian order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned long value.
 * @throws JayoClosedResourceException if this reader is closed.
 */
public fun Reader.readULong(): ULong = readLong().toULong()

/**
 * Removes two bytes from this reader and returns an unsigned short composed of them according to the little-endian
 * order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned short value.
 * @throws JayoClosedResourceException if this reader is closed.
 */
public fun Reader.readUShortLe(): UShort = readShortLe().toUShort()

/**
 * Removes four bytes from this reader and returns an unsigned integer composed of them according to the little-endian
 * order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned int value.
 * @throws JayoClosedResourceException if this reader is closed.
 */
public fun Reader.readUIntLe(): UInt = readIntLe().toUInt()

/**
 * Removes eight bytes from this reader and returns an unsigned long composed of them according to the little-endian
 * order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned long value.
 * @throws JayoClosedResourceException if this reader is closed.
 */
public fun Reader.readULongLe(): ULong = readLongLe().toULong()
