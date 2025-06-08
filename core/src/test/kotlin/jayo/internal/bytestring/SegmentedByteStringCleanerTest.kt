/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import jayo.TestLogHandler
import jayo.bytestring.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SegmentedByteStringCleanerTest {
    @RegisterExtension
    @JvmField
    val testLogHandler = TestLogHandler(SegmentedByteString.SegmentsRecycler.LOGGER)

    @Test
    fun snapshotSegmentsAreNotRecycled() {
        val buffer = RealBuffer()
        buffer.write("abc")
        var snapshot: ByteString? = buffer.snapshot()
        val s = (snapshot as SegmentedByteString).segments[0]
        buffer.clear()
        assertEquals("abc", snapshot.decodeToString())

        // force SegmentedByteString cleaner by setting its only reference to null, then forcing the garbage collection
        snapshot = null
        System.gc()
        Thread.sleep(200) // wait for the garbage collection to occur

        assertThat(testLogHandler.takeAll())
            .isNotEmpty()
            .contains(String.format("TRACE: Recycling Segment#%,d from the cleaned segmented ByteString", s.hashCode()))
    }
}