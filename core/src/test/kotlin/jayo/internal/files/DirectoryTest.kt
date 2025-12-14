/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.files

import jayo.JayoException
import jayo.files.Directory
import jayo.files.JayoFileAlreadyExistsException
import jayo.files.JayoFileNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DirectoryTest {
    @TempDir
    private lateinit var tempDirPath: Path

    @TempDir
    private lateinit var tempDirPathDest: Path

    @Test
    fun directoryExists() {
        assertThat(Directory.exists(tempDirPath)).isTrue()
        assertThat(tempDirPath.exists()).isTrue()
    }

    @Test
    fun openDirectory() {
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        assertThrows<IllegalArgumentException> {
            Directory.open(file)
        }
        val otherDirPath = tempDirPath.resolve("otherDir")
        assertThrows<JayoFileNotFoundException> {
            Directory.open(otherDirPath)
        }
        Directory.open(tempDirPath)
        assertThat(tempDirPath.exists()).isTrue()
    }

    @Test
    fun createDirectory() {
        val otherDirPath = tempDirPath.resolve("otherDir")
        assertThat(otherDirPath.exists()).isFalse()
        Directory.create(otherDirPath)
        assertThat(otherDirPath.exists()).isTrue()
        assertThrows<JayoFileAlreadyExistsException> {
            Directory.create(otherDirPath)
        }
    }

    @Test
    fun createDirectoryIfNotExists() {
        val otherDirPath = tempDirPath.resolve("otherDir")
        assertThat(otherDirPath.exists()).isFalse()
        Directory.createIfNotExists(otherDirPath)
        assertThat(otherDirPath.exists()).isTrue()
        Directory.createIfNotExists(otherDirPath)
        assertThat(otherDirPath.exists()).isTrue()
    }

    @Test
    fun directoryPath() {
        assertThat(Directory.open(tempDirPath).path).isEqualTo(tempDirPath)
    }

    @Test
    fun directoryName() {
        val dirName = "dir"
        val dirPath = tempDirPath.resolve(dirName)
        assertThat(Directory.create(dirPath).name).isEqualTo(dirName)
    }

    @Test
    fun directoryDelete() {
        val filename = "fileToDelete.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        Directory.open(tempDirPath).delete()
        assertThat(file.exists()).isFalse()
        assertThat(tempDirPath.exists()).isFalse()
    }

    @Test
    fun directoryAtomicMoveToDirectory() {
        val filename = "fileToMove.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")

        Directory.open(tempDirPath).atomicMove(tempDirPathDest)

        assertThat(tempDirPathDest.resolve(filename).readText()).isEqualTo("a")
        assertThat(tempDirPath.exists()).isFalse()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun directoryAtomicMoveToNonEmptyDirectoryThrow() {
        val filename = "fileToMove.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        val fileInDest = tempDirPathDest.resolve(filename)
        fileInDest.writeText("b")

        assertThrows<JayoException> {
            Directory.open(tempDirPath).atomicMove(tempDirPathDest)
        }
        assertThat(file.exists()).isTrue() // move failed, the file is still there
    }

    @Test
    fun directoryAtomicMoveToFileThrow() {
        val filename = "fileToMove.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        val fileInDest = tempDirPathDest.resolve(filename)
        fileInDest.writeText("b")

        assertThrows<JayoException> {
            Directory.open(tempDirPath).atomicMove(fileInDest)
        }
        assertThat(file.exists()).isTrue() // move failed, the file is still there
    }

    @Test
    fun directoryCopyToDirectory() {
        val filename = "fileToMove.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")

        Directory.open(tempDirPath).copy(tempDirPathDest)

        assertThat(tempDirPathDest.resolve(filename).readText()).isEqualTo("a")
        assertThat(tempDirPath.exists()).isTrue()
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun directoryCopyToNonEmptyDirectoryThrow() {
        val filename = "fileToMove.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        val fileInDest = tempDirPathDest.resolve(filename)
        fileInDest.writeText("b")

        assertThrows<JayoException> {
            Directory.open(tempDirPath).atomicMove(tempDirPathDest)
        }
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun directoryCopyToFileThrow() {
        val filename = "fileToMove.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")
        val fileInDest = tempDirPathDest.resolve(filename)
        fileInDest.writeText("b")

        assertThrows<JayoException> {
            Directory.open(tempDirPath).copy(fileInDest)
        }
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun directoryNotExistAnymore() {
        val jayoDir = Directory.open(tempDirPath)
        // delete the directory
        tempDirPath.deleteExisting()
        assertThat(tempDirPath.exists()).isFalse()

        assertThrows<JayoFileNotFoundException> {
            jayoDir.delete()
        }
        assertThrows<JayoFileNotFoundException> {
            jayoDir.listEntries()
        }
        assertThrows<JayoFileNotFoundException> {
            jayoDir.atomicMove(tempDirPathDest)
        }
        assertThrows<JayoFileNotFoundException> {
            jayoDir.copy(tempDirPathDest)
        }
    }

    @Test
    fun listEntries() {
        val subDir = tempDirPath.resolve("subDir")
        subDir.createDirectory()
        val filename = "file.txt"
        val file = tempDirPath.resolve(filename)
        file.writeText("a")

        val entries = Directory.open(tempDirPath).listEntries()

        assertThat(entries)
            .hasSize(2)
            .containsExactlyInAnyOrder(subDir, file)
    }
}