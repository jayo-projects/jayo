/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from OkHttp (https://github.com/square/okhttp), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A log handler that records which log messages were published so that a calling test can make assertions about them.
 */
class TestLogHandler(
    loggerName: String
) : BeforeEachCallback, AfterEachCallback {
    constructor(logger: System.Logger) : this(logger.name)

    private val logger = LoggerFactory.getLogger(loggerName) as Logger

    private val logs = LinkedBlockingQueue<String>()

    private val appender = object : AppenderBase<ILoggingEvent>() {
        override fun append(p0: ILoggingEvent) {
            logs += "${p0.level}: ${p0.message}"
        }
    }

    private var previousLevel: Level? = null

    override fun beforeEach(context: ExtensionContext) {
        appender.start()
        previousLevel = logger.level
        logger.level = Level.TRACE
        logger.addAppender(appender)
    }

    override fun afterEach(context: ExtensionContext) {
        logger.level = previousLevel
        logger.detachAppender(appender)
    }

    fun takeAll(): List<String> {
        val list = mutableListOf<String>()
        logs.drainTo(list)
        return list
    }

    fun take(): String {
        return logs.poll(10, TimeUnit.SECONDS)
            ?: throw AssertionError("Timed out waiting for log message.")
    }
}