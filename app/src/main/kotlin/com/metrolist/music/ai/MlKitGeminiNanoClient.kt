/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.reflect.Proxy
import java.util.concurrent.CancellationException
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.Continuation as KContinuation

/**
 * Reflection-based adapter for ML Kit GenAI Prompt (`com.google.mlkit:genai-prompt`).
 * Safe on FOSS / devices without AICore: missing classes → [GeminiNanoStatus.Unavailable].
 */
class MlKitGeminiNanoClient : GeminiNanoClient {
    @Volatile
    private var model: Any? = null

    @Volatile
    private var futuresClient: Any? = null

    @Volatile
    private var available: Boolean? = null

    private fun ensureClients(): Boolean {
        available?.let { return it }
        return synchronized(this) {
            available?.let { return it }
            try {
                val generationClass = Class.forName(GENERATION_CLASS)
                val instance =
                    runCatching { generationClass.getField("INSTANCE").get(null) }
                        .getOrElse {
                            // Kotlin object may expose INSTANCE via companion
                            generationClass.kotlin.objectInstance
                        }
                val getClient =
                    generationClass.methods.firstOrNull {
                        it.name == "getClient" && it.parameterCount == 0
                    } ?: return@synchronized false.also { available = false }

                val generativeModel = getClient.invoke(instance)
                val futuresClass = Class.forName(FUTURES_CLASS)
                val from =
                    futuresClass.methods.firstOrNull {
                        it.name == "from" && it.parameterCount == 1
                    } ?: return@synchronized false.also { available = false }

                model = generativeModel
                futuresClient = from.invoke(null, generativeModel)
                available = true
                true
            } catch (e: ClassNotFoundException) {
                Timber.tag(TAG).i("ML Kit GenAI Prompt not on classpath (FOSS or missing dep)")
                available = false
                false
            } catch (e: Throwable) {
                Timber.tag(TAG).w(e, "Failed to initialize Gemini Nano client")
                available = false
                false
            }
        }
    }

    override suspend fun checkStatus(): GeminiNanoStatus =
        withContext(Dispatchers.IO) {
            if (!ensureClients()) return@withContext GeminiNanoStatus.Unavailable
            try {
                val futures = futuresClient ?: return@withContext GeminiNanoStatus.Unavailable
                val checkStatus =
                    futures.javaClass.methods.firstOrNull {
                        it.name == "checkStatus" && it.parameterCount == 0
                    } ?: return@withContext GeminiNanoStatus.Error

                @Suppress("UNCHECKED_CAST")
                val future = checkStatus.invoke(futures) as ListenableFuture<Int>
                val code = future.await()
                mapFeatureStatus(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.tag(TAG).w(e, "checkStatus failed")
                GeminiNanoStatus.Error
            }
        }

    override suspend fun download(onProgress: (Long) -> Unit) {
        withContext(Dispatchers.IO) {
            if (!ensureClients()) return@withContext
            val futures = futuresClient ?: return@withContext
            try {
                val callbackClass = Class.forName(DOWNLOAD_CALLBACK_CLASS)
                suspendCancellableCoroutine { cont ->
                    val callback =
                        Proxy.newProxyInstance(
                            callbackClass.classLoader,
                            arrayOf(callbackClass),
                        ) { _, method, args ->
                            when (method.name) {
                                "onDownloadStarted" -> Unit
                                "onDownloadProgress" -> {
                                    val bytes = (args?.getOrNull(0) as? Number)?.toLong() ?: 0L
                                    onProgress(bytes)
                                }
                                "onDownloadCompleted" -> {
                                    if (cont.isActive) cont.resume(Unit)
                                }
                                "onDownloadFailed" -> {
                                    val err = args?.getOrNull(0) as? Throwable
                                    if (cont.isActive) {
                                        cont.resumeWithException(
                                            err ?: IllegalStateException("Gemini Nano download failed"),
                                        )
                                    }
                                }
                            }
                            null
                        }

                    val downloadMethod =
                        futures.javaClass.methods.firstOrNull {
                            it.name == "download" && it.parameterCount == 1
                        }
                    if (downloadMethod == null) {
                        cont.resumeWithException(IllegalStateException("download() not found"))
                        return@suspendCancellableCoroutine
                    }
                    downloadMethod.invoke(futures, callback)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.tag(TAG).w(e, "download failed")
                // Fall back to Kotlin Flow download on GenerativeModel if Futures path failed
                downloadViaFlow(onProgress)
            }
        }
    }

    private suspend fun downloadViaFlow(onProgress: (Long) -> Unit) {
        val generativeModel = model ?: return
        val downloadMethod =
            generativeModel.javaClass.methods.firstOrNull {
                it.name == "download" && it.parameterCount == 0
            } ?: return
        val flow = downloadMethod.invoke(generativeModel) ?: return
        // Collect Flow via reflection: flow.collect { } as suspend
        val collectMethod =
            flow.javaClass.methods.firstOrNull {
                it.name == "collect" && it.parameterCount == 2
            } ?: return

        suspendCancellableCoroutine { cont ->
            val collectorClass = Class.forName("kotlinx.coroutines.flow.FlowCollector")
            val collector =
                Proxy.newProxyInstance(
                    collectorClass.classLoader,
                    arrayOf(collectorClass),
                ) { _, method, args ->
                    if (method.name == "emit") {
                        val status = args?.getOrNull(0)
                        val statusName = status?.javaClass?.simpleName.orEmpty()
                        when {
                            statusName.contains("Progress", ignoreCase = true) -> {
                                val bytes =
                                    runCatching {
                                        val s = status ?: return@runCatching null
                                        s.javaClass.methods
                                            .firstOrNull { it.name == "getTotalBytesDownloaded" || it.name == "totalBytesDownloaded" }
                                            ?.let { m ->
                                                if (m.parameterCount == 0) m.invoke(s)
                                                else null
                                            } as? Number
                                    }.getOrNull()?.toLong() ?: 0L
                                onProgress(bytes)
                            }
                            statusName.contains("Completed", ignoreCase = true) -> {
                                if (cont.isActive) cont.resume(Unit)
                            }
                            statusName.contains("Failed", ignoreCase = true) -> {
                                if (cont.isActive) {
                                    cont.resumeWithException(IllegalStateException("Download failed"))
                                }
                            }
                        }
                        // emit is suspend — resume continuation immediately for Unit
                        Unit
                    } else {
                        null
                    }
                }
            try {
                val result =
                    collectMethod.invoke(
                        flow,
                        collector,
                        object : KContinuation<Any?> {
                            override val context = cont.context
                            override fun resumeWith(result: Result<Any?>) {
                                result.exceptionOrNull()?.let {
                                    if (cont.isActive) cont.resumeWithException(it)
                                    return
                                }
                                if (cont.isActive) cont.resume(Unit)
                            }
                        },
                    )
                if (result != COROUTINE_SUSPENDED && cont.isActive) {
                    cont.resume(Unit)
                }
            } catch (e: Throwable) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }

    override suspend fun generateContent(prompt: String): String? =
        withContext(Dispatchers.IO) {
            if (!ensureClients()) return@withContext null
            val generativeModel = model ?: return@withContext null
            try {
                // Prefer suspend generateContent(String) on GenerativeModel
                val suspendMethod =
                    generativeModel.javaClass.methods.firstOrNull { method ->
                        method.name == "generateContent" &&
                            method.parameterTypes.size == 2 &&
                            method.parameterTypes[0] == String::class.java
                    }

                val response: Any? =
                    if (suspendMethod != null) {
                        invokeSuspend(suspendMethod, generativeModel, prompt)
                    } else {
                        generateViaFutures(prompt)
                    }

                extractText(response)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.tag(TAG).w(e, "generateContent failed")
                null
            }
        }

    private suspend fun generateViaFutures(prompt: String): Any? {
        val futures = futuresClient ?: return null
        val method =
            futures.javaClass.methods.firstOrNull {
                it.name == "generateContent" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == String::class.java
            } ?: return null

        @Suppress("UNCHECKED_CAST")
        val future = method.invoke(futures, prompt) as ListenableFuture<Any>
        return future.await()
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> invokeSuspend(method: java.lang.reflect.Method, instance: Any, vararg args: Any?): T =
        suspendCancellableCoroutine { cont ->
            try {
                val result =
                    method.invoke(
                        instance,
                        *args,
                        object : KContinuation<Any?> {
                            override val context = cont.context
                            override fun resumeWith(result: Result<Any?>) {
                                result.fold(
                                    onSuccess = { value ->
                                        if (cont.isActive) cont.resume(value as T)
                                    },
                                    onFailure = { err ->
                                        if (cont.isActive) cont.resumeWithException(err)
                                    },
                                )
                            }
                        },
                    )
                if (result != COROUTINE_SUSPENDED && cont.isActive) {
                    cont.resume(result as T)
                }
            } catch (e: Throwable) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }

    private fun extractText(response: Any?): String? {
        if (response == null) return null
        if (response is String) return response
        runCatching {
            val getText =
                response.javaClass.methods.firstOrNull {
                    (it.name == "getText" || it.name == "text") && it.parameterCount == 0
                }
            val text = getText?.invoke(response) as? String
            if (!text.isNullOrBlank()) return text
        }
        // candidates[0].text / getCandidates()
        runCatching {
            val getCandidates =
                response.javaClass.methods.firstOrNull {
                    (it.name == "getCandidates" || it.name == "candidates") && it.parameterCount == 0
                } ?: return@runCatching
            val candidates = getCandidates.invoke(response) as? List<*> ?: return@runCatching
            val first = candidates.firstOrNull() ?: return@runCatching
            val getText =
                first.javaClass.methods.firstOrNull {
                    (it.name == "getText" || it.name == "text") && it.parameterCount == 0
                }
            return getText?.invoke(first) as? String
        }
        return response.toString().takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "GeminiNano"
        private const val GENERATION_CLASS = "com.google.mlkit.genai.prompt.Generation"
        private const val FUTURES_CLASS = "com.google.mlkit.genai.prompt.java.GenerativeModelFutures"
        private const val DOWNLOAD_CALLBACK_CLASS = "com.google.mlkit.genai.common.DownloadCallback"
    }
}
