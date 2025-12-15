/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-Buffer") // Leading '-' hides this class from Java.

package jayo

/** @return a new [Buffer] */
public fun Buffer(): Buffer = Buffer.create()