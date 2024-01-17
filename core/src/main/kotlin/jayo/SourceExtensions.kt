/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 * 
 * Forked from kotlinx-io (https://github.com/Kotlin/kotlinx-io), original copyright is below
 * 
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

@file:JvmName("-Source") // A leading '-' hides this class from Java.

package jayo

import jayo.exceptions.JayoEOFException

/**
 * Removes two bytes from this source and returns a short composed of them according to the little-endian order.
 *
 * @throws JayoEOFException if there are not enough data to read a short value.
 * @throws IllegalStateException if this source is closed.
 */
public fun Source.readShortLe(): Short {
    return java.lang.Short.reverseBytes(readShort())
}

/**
 * Removes four bytes from this source and returns an integer composed of them according to the little-endian order.
 *
 * @throws JayoEOFException if there are not enough data to read an int value.
 * @throws IllegalStateException if this source is closed.
 */
public fun Source.readIntLe(): Int {
    return Integer.reverseBytes(readInt())
}

/**
 * Removes eight bytes from this source and returns a long composed of them according to the little-endian order.
 *
 * @throws JayoEOFException if there are not enough data to read a long value.
 * @throws IllegalStateException if this source is closed.
 */
public fun Source.readLongLe(): Long {
    return java.lang.Long.reverseBytes(readLong())
}

/**
 * Removes an unsigned byte from this source and returns it.
 *
 * @throws JayoEOFException when there are no more bytes to read.
 * @throws IllegalStateException if this source is closed.
 */
public fun Source.readUByte(): UByte = readByte().toUByte()

/**
 * Removes two bytes from this source and returns an unsigned short composed of them according to the big-endian order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned short value.
 * @throws IllegalStateException if this source is closed.
 */
public fun Source.readUShort(): UShort = readShort().toUShort()

/**
 * Removes four bytes from this source and returns an unsigned integer composed of them according to the big-endian
 * order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned int value.
 * @throws IllegalStateException if this source is closed.
 */
public fun Source.readUInt(): UInt = readInt().toUInt()

/**
 * Removes eight bytes from this source and returns an unsigned long composed of them according to the big-endian order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned long value.
 * @throws IllegalStateException if this source is closed.
 */
public fun Source.readULong(): ULong = readLong().toULong()

/**
 * Removes two bytes from this source and returns an unsigned short composed of them according to the little-endian
 * order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned short value.
 * @throws IllegalStateException if this source is closed.
 */
public fun Source.readUShortLe(): UShort = readShortLe().toUShort()

/**
 * Removes four bytes from this source and returns an unsigned integer composed of them according to the little-endian
 * order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned int value.
 * @throws IllegalStateException if this source is closed.
 */
public fun Source.readUIntLe(): UInt = readIntLe().toUInt()

/**
 * Removes eight bytes from this source and returns an unsigned long composed of them according to the little-endian
 * order.
 *
 * @throws JayoEOFException if there are not enough data to read an unsigned long value.
 * @throws IllegalStateException if this source is closed.
 */
public fun Source.readULongLe(): ULong = readLongLe().toULong()
