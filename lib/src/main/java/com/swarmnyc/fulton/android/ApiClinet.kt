package com.swarmnyc.fulton.android

import android.util.Log
import com.swarmnyc.fulton.android.util.fromJson
import com.swarmnyc.fulton.android.util.toJson
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.thenApply
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

typealias ApiPromise<T> = Promise<T, ApiError>

typealias ApiDeferred<T> = Deferred<T, ApiError>

infix fun <V, R> ApiPromise<V>.then(bind: (V) -> ApiPromise<R>): ApiPromise<R> {
    val deferred = deferred<R, ApiError>(context)
    success {
        try {
            bind(it).success(deferred::resolve).fail(deferred::reject)
        } catch (e: Exception) {
            deferred.reject(ApiError(e))
        }
    }.fail(deferred::reject)

    deferred.promise.thenApply { }

    return deferred.promise
}

inline infix fun <V, R> ApiPromise<V>.thenApply(crossinline bind: V.() -> ApiPromise<R>): ApiPromise<R> {
    val deferred = deferred<R, ApiError>(context)
    success {
        try {
            bind(it).success(deferred::resolve).fail(deferred::reject)
        } catch (e: Exception) {
            deferred.reject(ApiError(e))
        }
    }.fail(deferred::reject)

    return deferred.promise
}

/**
 * Base Api Client, supports
 * - error handle
 * - async
 * - cache
 * */
abstract class ApiClient {
    companion object {
        const val TAG = "fulton.api"
        const val NO_CACHE = 0
    }

    protected abstract val urlRoot: String

    /**
     * init request, like set headers
     * */
    open fun initRequest(req: Request) {
    }

    protected inline fun <reified T : Any> request(builder: Request.() -> Unit): ApiPromise<T> {
        val req = Request()
        req.urlRoot = urlRoot
        req.dataType = T::class.java

        builder(req)

        req.buildUrl()
        req.buildDataType()

        return request(req)
    }

    protected fun <T : Any> request(req: Request): ApiPromise<T> {
        val promise = deferred<T, ApiError>()

        promise.promise.context.workerContext.offer {
            if (req.method == Method.GET && req.cacheDurationMs > NO_CACHE) {
                val cacheResult = Fulton.context.cacheManagement.get<T>(req.url!!, req.dataType!!)
                if (cacheResult != null) {
                    // cache hits
                    promise.resolve(cacheResult)

                    return@offer
                }
            }

            execRequest(promise, req)
        }

        return promise.promise
    }

    protected open fun <T> execRequest(promise: ApiDeferred<T>, req: Request) {
        initRequest(req)

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val msg = buildString {
                appendln("--> ${req.method} (${req.url})")
                appendln("Body : ${if (req.body == null) "(empty)" else req.body?.toJson()}")
                appendln("Headers : (${req.headers.size})")

                for ((key, value) in req.headers) {
                    appendln("$key : $value")
                }
            }

            Log.d(TAG, msg)
            Log.d(TAG, "--> ${req.method} (${req.url})\n$req")
        }

        var conn: HttpURLConnection? = null
        try {
            val url = URL(req.url)
            conn = url.openConnection() as HttpURLConnection

            conn.apply {
                doInput = true
                connectTimeout = req.timeOutMs
                requestMethod = if (req.method == Method.PATCH) Method.POST.value else req.method.value
                instanceFollowRedirects = true

                for ((key, value) in req.headers) {
                    setRequestProperty(key, value)
                }

                if (req.method == Method.PATCH) {
                    setRequestProperty("X-HTTP-Method-Override", "PATCH")
                }

                if (req.body != null) {
                    conn.doOutput = true

                    val wither = OutputStreamWriter(conn.outputStream)
                    req.body!!.toJson(wither)

                    wither.flush()
                    wither.close()
                }

                connect()

                val stream = conn.errorStream ?: conn.inputStream

                val contentEncoding = conn.contentEncoding ?: ""

                val data = if (contentEncoding.compareTo("gzip", true) == 0) {
                    GZIPInputStream(stream).readBytes()
                } else {
                    stream.readBytes()
                }

                stream.close()

                val res = Response(conn.url.toString(), conn.responseCode, conn.headerFields.filterKeys { it != null }, data)

                handleResponse(promise, req, res)
            }
        } catch (e: Exception) {
            handelError(promise, req, Response(error = e))
        } finally {
            conn?.disconnect()
        }
    }

    protected open fun <T> handleResponse(promise: ApiDeferred<T>, req: Request, res: Response) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val time = (System.currentTimeMillis() - req.startedAt)
            val msg = buildString {
                appendln("<-- ${res.status} (${res.url})")
                appendln("Time Spent : $time")
                appendln("Length : ${res.contentLength}")
                appendln("Headers : (${res.headers.size})")
                for ((key, value) in res.headers) {
                    appendln("$key : $value")
                }
                appendln("Body : ${if (res.data.isNotEmpty()) String(res.data) else "(empty)"}")
            }

            Log.d(TAG, msg)
        }

        if (res.error == null) {
            try {
                handleSuccess(promise, req, res)
            } catch (e: Throwable) {
                handelError(promise, req, res)
            }
        } else {
            handelError(promise, req, res)
        }
    }

    protected open fun <T> handleSuccess(promise: ApiDeferred<T>, options: Request, res: Response) {
        var shouldCache = options.method == Method.GET && options.cacheDurationMs > NO_CACHE

        val result: T = when (options.dataType) {
            Unit::class.java, Nothing::class.java -> {
                shouldCache = false
                @Suppress("UNCHECKED_CAST")
                Unit as T
            }
            String::class.java -> {
                @Suppress("UNCHECKED_CAST")
                String(res.data) as T
            }
            else -> {
                res.data.fromJson(options.dataType!!)
            }
        }

        if (shouldCache) {
            cacheData(options.url!!, options.cacheDurationMs, res.data)
        }

        promise.resolve(result)
    }

    protected open fun <T> handelError(promise: ApiDeferred<T>, req: Request, res: Response) {
        val apiError = ApiError(res.error!!, req, res)

        try {
            promise.promise.fail {
                Fulton.context.errorHandler.onError(apiError)
            }

            promise.reject(apiError)
        } catch (e: Exception) {
            throw Exception("Error Handle failed", e)
        }
    }

    protected open fun cacheData(url: String, cache: Int, byteArray: ByteArray) {
        Fulton.context.cacheManagement.add(this.javaClass.simpleName, url, cache, byteArray)
    }

    open fun cleanCache() {
        Fulton.context.cacheManagement.clean(this.javaClass.simpleName)
    }
}

