/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.files

import jayo.JayoException
import jayo.bytestring.encodeToByteString
import jayo.crypto.JdkDigest
import jayo.crypto.JdkHmac
import jayo.files.File
import jayo.files.JayoFileAlreadyExistsException
import jayo.files.JayoFileNotFoundException
import jayo.internal.RealBuffer
import jayo.internal.TestUtil.SEGMENT_SIZE
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.TemporalUnitWithinOffset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.*

class FileTest {
    @TempDir
    private lateinit var tempDirPath: Path

    @TempDir
    private lateinit var tempDirPathDest: Path

    @Test
    fun fileExists() {
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        assertThat(File.exists(file)).isTrue()
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun openFile() {
        assertThrows<IllegalArgumentException> {
            File.open(tempDirPath)
        }
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        assertThrows<JayoFileNotFoundException> {
            File.open(file)
        }
        file.writeText("a") // creates the file
        File.open(file)
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun createFile() {
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        assertThat(file.exists()).isFalse()
        File.create(file)
        assertThat(file.exists()).isTrue()
        assertThrows<JayoFileAlreadyExistsException> {
            File.create(file)
        }
    }

    @Test
    fun createFileIfNotExists() {
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        assertThat(file.exists()).isFalse()
        File.createIfNotExists(file)
        assertThat(file.exists()).isTrue()
        File.createIfNotExists(file)
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun filePath() {
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        assertThat(File.open(file).path).isEqualTo(file)
    }

    @Test
    fun fileName() {
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        assertThat(File.open(file).name).isEqualTo(filename)
    }

    @Test
    fun fileByteSize() {
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        assertThat(File.open(file).byteSize()).isEqualTo(1)
    }

    @Test
    fun fileWriter() {
        val file = tempDirPath.resolve("fileWriter.txt")
        file.createFile()
        File.open(file).writer().use { writer ->
            writer.writeFrom(RealBuffer().write("a"), 1L)
        }
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun fileWithAppend() {
        val file = tempDirPath.resolve("fileWriterWithOptions.txt")
        file.writeText("a")
        File.open(file).writer(StandardOpenOption.APPEND).use { writer ->
            writer.writeFrom(RealBuffer().write("b"), 1L)
        }
        assertThat(file.readText()).isEqualTo("ab")
    }

    @Test
    fun fileWriterWithIgnoredOptions() {
        val file = tempDirPath.resolve("fileWriter.txt")
        file.createFile()
        File.open(file).writer(StandardOpenOption.CREATE).use { writer ->
            writer.writeFrom(RealBuffer().write("a"), 1L)
        }
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun fileReader() {
        val file = tempDirPath.resolve("fileReader.txt")
        file.writeText("abc")
        File.open(file).reader().use { reader ->
            val buffer = RealBuffer()
            reader.readAtMostTo(buffer, 1L)
            assertThat(buffer.readString()).isEqualTo("a")
        }
    }

    @Test
    fun fileDelete() {
        val filename = "fileToMove.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        File.open(file).delete()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun fileAtomicMoveToFile() {
        val filename = "fileToMove.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        val dest = tempDirPathDest.resolve(filename)
        File.open(file).atomicMove(dest)
        assertThat(dest.readText()).isEqualTo("a")
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun fileAtomicMoveToDirThrow() {
        val filename = "fileToMove.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        assertThrows<JayoException> {
            File.open(file).atomicMove(tempDirPath)
        }
        assertThat(file.exists()).isTrue() // move failed, the file is still there
    }

    @Test
    fun fileCopyToFile() {
        val filename = "fileToCopy.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        val dest = tempDirPathDest.resolve(filename)
        File.open(file).copy(dest)
        assertThat(dest.readText()).isEqualTo("a")
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun fileCopyToDirThrow() {
        val filename = "fileToCopy.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        assertThrows<JayoException> {
            File.open(file).copy(tempDirPath)
        }
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun fileNotExistAnymore() {
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        val jayoFile = File.open(file)
        // delete the file
        file.deleteExisting()
        assertThat(file.exists()).isFalse()

        assertThrows<JayoFileNotFoundException> {
            jayoFile.byteSize()
        }
        assertThrows<JayoFileNotFoundException> {
            jayoFile.metadata()
        }
        assertThrows<JayoFileNotFoundException> {
            jayoFile.writer()
        }
        assertThrows<JayoFileNotFoundException> {
            jayoFile.reader()
        }
        assertThrows<JayoFileNotFoundException> {
            jayoFile.delete()
        }
        val dest = tempDirPathDest.resolve(filename) // an acceptable target for atomicMove and copy
        assertThrows<JayoFileNotFoundException> {
            jayoFile.atomicMove(dest)
        }
        assertThrows<JayoFileNotFoundException> {
            jayoFile.copy(dest)
        }
    }

    @Test
    fun hash() {
        val bytes = ByteArray(SEGMENT_SIZE * 2 + 1) { 'a'.code.toByte() }
        val expectedMd5 = "3ac15f278019c332ab4395eb3b1167b8"
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        file.writeBytes(bytes)
        assertThat(File.open(file).hash(JdkDigest.MD5).hex()).isEqualTo(expectedMd5)
    }

    @Test
    fun hMac() {
        val bytes = ByteArray(SEGMENT_SIZE * 2 + 1) { 'a'.code.toByte() }
        val key = "abc".encodeToByteString()
        val expectedMd5 = "2d6bd1f82825302aa6ed6cdac51771ff"
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        file.writeBytes(bytes)
        assertThat(File.open(file).hmac(JdkHmac.HMAC_MD5, key).hex()).isEqualTo(expectedMd5)
    }

    @Test
    fun fileMetadata() {
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        val creation = Instant.now()
        file.writeText("a")

        // wait and change the content to have a 'modifiedAt' that is different from 'created'
        Thread.sleep(50)
        file.writeText("b")

        // wait and read the content to have an 'accessedAt' that is different from 'modifiedAt'
        Thread.sleep(50)
        assertThat(file.readText()).isEqualTo("b")

        val jayoFile = File.open(file)
        val metadata = jayoFile.metadata()

        assertThat(metadata.isRegularFile).isTrue()
        assertThat(metadata.symlinkTarget).isNull()
        assertThat(metadata.createdAt)
            .isCloseTo(creation, TemporalUnitWithinOffset(100, ChronoUnit.MILLIS))
        assertThat(metadata.lastModifiedAt).isAfter(metadata.createdAt)
        assertThat(metadata.lastAccessedAt).isAfter(metadata.lastModifiedAt)
    }
}