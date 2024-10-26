// Copyright © 2024 Brent Tunnicliff <brent@tunnicliff.dev>

package dev.tunnicliff.logging

import android.content.Context
import dev.tunnicliff.container.Container
import dev.tunnicliff.logging.internal.database.LoggingDatabase
import dev.tunnicliff.logging.logger.LogUploadHandler
import dev.tunnicliff.logging.logger.Logger
import dev.tunnicliff.logging.logger.LoggingConfigurationManager
import dev.tunnicliff.logging.logger.internal.DefaultLogUploader
import dev.tunnicliff.logging.logger.internal.DefaultLogWriter
import dev.tunnicliff.logging.logger.internal.DefaultLogger
import dev.tunnicliff.logging.logger.internal.DefaultLoggingConfigurationManager
import dev.tunnicliff.logging.logger.internal.LogUploader
import dev.tunnicliff.logging.logger.internal.LogWriter
import dev.tunnicliff.logging.logger.internal.SystemLog
import dev.tunnicliff.logging.logger.internal.SystemLogWrapper
import dev.tunnicliff.logging.view.internal.DefaultLogsViewModel
import dev.tunnicliff.logging.view.internal.LogsViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

/**
 * Dependency injection container for the library.
 */
class LoggingContainer private constructor(
    private val dependencies: Dependencies
) : Container() {
    interface Dependencies {
        fun applicationContext(): Context
        fun uploadHandler(): LogUploadHandler?
    }

    companion object {
        private lateinit var _SHARED: LoggingContainer

        /**
         * Shared instance of the container.
         *
         * `initialise()` must be called before this can be referenced otherwise
         *
         * @throws UninitializedPropertyAccessException if `initialise()` has not been called first.
         */
        val SHARED: LoggingContainer
            get() = _SHARED

        /**
         * Initialises the container.
         *
         * After which `SHARED` will be safe to use.
         *
         * @property dependencies the external dependencies required by the container.
         */
        fun initialise(dependencies: Dependencies) {
            _SHARED = LoggingContainer(dependencies)
        }
    }

    // region Public

    @OptIn(DelicateCoroutinesApi::class)
    fun logger(): Logger = resolveSingleton {
        DefaultLogger(
            // Using GlobalScope is not ideal, but wanted to avoid having to make each log function `suspend`.
            // Probably not an issue though since ideally LoggingRepository's lifecycle should match the app.
            coroutineScope = GlobalScope,
            loggingConfigurationManager = loggingConfigurationManager(),
            logUploader = logUploader(),
            logWriter = logWriter(),
            systemLog = systemLog()
        )
    }

    fun loggingConfigurationManager(): LoggingConfigurationManager = resolveSingleton {
        DefaultLoggingConfigurationManager(
            context = dependencies.applicationContext(),
            logger = { logger() }
        )
    }

    // endregion

    // region Internal

    internal fun logsViewModel(): LogsViewModel =
        DefaultLogsViewModel(loggingDatabase = loggingRepositoryDatabase())

    // endregion

    // region Private

    private fun loggingRepositoryDatabase(): LoggingDatabase = resolveSingleton {
        LoggingDatabase.new(dependencies.applicationContext())
    }

    private fun logUploader(): LogUploader = resolveWeak {
        DefaultLogUploader(
            loggingConfigurationManager = loggingConfigurationManager(),
            logWriter = { logWriter() },
            systemLog = systemLog(),
            uploadHandler = dependencies.uploadHandler()
        )
    }

    private fun logWriter(): LogWriter = resolveWeak {
        DefaultLogWriter(
            database = loggingRepositoryDatabase(),
            logUploader = { logUploader() },
            systemLog = systemLog(),
        )
    }

    private fun systemLog(): SystemLog = resolveWeak {
        SystemLogWrapper()
    }

    // endregion
}
