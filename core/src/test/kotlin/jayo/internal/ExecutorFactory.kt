/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

interface ExecutorFactory {
    fun newExecutorService(): ExecutorService
    fun newScheduledExecutorService(): ScheduledExecutorService

    companion object {
        private val CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors()

        @JvmStatic
        val SINGLE_EXECUTOR_FACTORY: ExecutorFactory = object : ExecutorFactory {
            override fun newExecutorService(): ExecutorService =
                Executors.newSingleThreadExecutor()

            override fun newScheduledExecutorService(): ScheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor()
        }

        @JvmStatic
        val PLATFORM_SCHEDULED_EXECUTOR_FACTORY: ExecutorFactory = object : ExecutorFactory {
            override fun newExecutorService(): ExecutorService =
                Executors.newScheduledThreadPool(CORE_POOL_SIZE)

            override fun newScheduledExecutorService(): ScheduledExecutorService =
                Executors.newScheduledThreadPool(CORE_POOL_SIZE)
        }

        @JvmStatic
        val VIRTUAL_SCHEDULED_EXECUTOR_FACTORY: ExecutorFactory = object : ExecutorFactory {
            override fun newExecutorService(): ExecutorService =
                Executors.newScheduledThreadPool(CORE_POOL_SIZE, TestUtil::newThread)

            override fun newScheduledExecutorService(): ScheduledExecutorService =
                Executors.newScheduledThreadPool(CORE_POOL_SIZE, TestUtil::newThread)
        }
    }
}
