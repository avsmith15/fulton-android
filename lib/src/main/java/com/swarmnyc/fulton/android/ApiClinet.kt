package com.swarmnyc.fulton.android

import android.util.Log
import com.github.kittinunf.fuel.core.*
import com.github.kittinunf.result.Result
import com.swarmnyc.fulton.android.util.fromJson
import com.swarmnyc.fulton.android.util.toJson
import com.swarmnyc.fulton.android.util.urlEncode
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.lang.reflect.Type
import java.net.URI

typealias ApiPromise<T> = Promise<T, ApiError>

typealias ApiDeferred<T> = Deferred<T, ApiError>

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

    protected abstract val apiUrl: String

    /**
     * init request, like set headers
     * */
    open fun initRequest(req: Request) {
    }

    /**
     * the function of building url
     */
    protected open fun buildUrl(path: String, queryString: Map<String, Any?>? = null): String {
        return buildUrl(listOf(path), queryString)
    }

    /**
     * the function of building url
     */
    protected open fun buildUrl(paths: List<String>? = null, queryString: Map<String, Any?>? = null): String {
        val url = buildString {
            append(apiUrl)

            paths?.forEach { append("/$it") }

            if (queryString != null && queryString.isNotEmpty()) {
                append("?")

                append(queryString.entries.joinToString("&") {
                    "${it.key}=${it.value.toString().urlEncode()}"
                })
            }

        }

        // the normalize doesn't remove double // on below API 21
        return URI.create(url).normalize().toString()
    }

    /**
     * make a HTTP request, for simple return type
     *
     * Only GET will be cached
     * @param cache cache is minute, if it is 0 means no cache
     * */
    protected inline fun <reified T : Any> request(method: Method, url: String, body: Any? = null, cache: Int = Fulton.context.defaultCacheDuration): ApiPromise<T> {
        return request(RequestOptions(method, url, T::class.java, body, cache))
    }

    /**
     * make a HTTP request
     *
     * calling reified generic method from another reified generic method has problem by language limit,
     * so have to pass type from the first reified generic
     *
     * Only GET will be cached
     * @param cache cache is minute, if it is 0 means no cache
     * */
    protected fun <T : Any> request(method: Method, url: String, type: Type, body: Any? = null, cache: Int = Fulton.context.defaultCacheDuration): ApiPromise<T> {
        return request(RequestOptions(method, url, type, body, cache))
    }

    protected fun <T : Any> request(options: RequestOptions): ApiPromise<T> {
        val promise = deferred<T, ApiError>()

        promise.promise.context.workerContext.offer {
            if (options.method == Method.GET && options.cacheDuration > NO_CACHE) {
                val cacheResult = Fulton.context.cacheManagement.get<T>(options.url, options.dataType)
                if (cacheResult != null) {
                    // cache hits
                    promise.resolve(cacheResult)

                    return@offer
                }
            }

            startRequest(promise, options)
        }

        return promise.promise
    }

    protected open fun <T> startRequest(promise: ApiDeferred<T>, options: RequestOptions) {
        val req = FuelManager.instance.request(options.method, options.url)
        if (options.body != null) {
            req.body(options.body.toJson())
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "--> ${req.method} (${req.url})\n$req")
        }

        req.timeout(options.timeOut).response { _, res, result ->
            handleResponse(promise, options, req, res, result)
        }
    }

    protected open fun <T> handleResponse(promise: ApiDeferred<T>, options: RequestOptions, req: Request, res: Response, result: Result<ByteArray, FuelError>) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val time = (System.currentTimeMillis() - options.startedAt)
            val msg = buildString {
                appendln("<-- ${res.statusCode} (${res.url})")
                appendln("Time Spent : $time")
                appendln("Response : ${res.responseMessage}")
                appendln("Length : ${res.contentLength}")
                appendln("Headers : (${res.headers.size})")
                for ((key, value) in res.headers) {
                    appendln("$key : $value")
                }
                appendln("Body : ${if (res.data.isNotEmpty()) String(res.data) else "(empty)"}")
            }

            Log.d(TAG, msg)
        }

        if (res.statusCode < 400) {
            try {
                handleSuccess(promise, options, res, result.get())
            } catch (e: Throwable) {
                handelError(promise, req, res, result.component2()!!)
            }
        } else {
            handelError(promise, req, res, result.component2()!!)
        }
    }

    protected open fun <T> handleSuccess(promise: ApiDeferred<T>, options: RequestOptions, res: Response, bytes: ByteArray) {
        var result: T? = null
        var shouldCache = options.method == Method.GET && options.cacheDuration > NO_CACHE

        when (options.dataType) {
            Unit::class.java, Nothing::class.java -> {
                @Suppress("UNCHECKED_CAST")
                result = Unit as T
                shouldCache = false
            }
            String::class.java -> {
                @Suppress("UNCHECKED_CAST")
                result = String(bytes) as T
            }
            else -> {
                val isJson = res.headers["Content-Type"]?.any {
                    it.toLowerCase().contains("json")
                } ?: false

                if (isJson) {
                    result = bytes.fromJson(options.dataType)

                }
            }
        }

        if (shouldCache) {
            cacheData(options.url, options.cacheDuration, bytes)
        }

        if (result != null) {
            promise.resolve(result)
        }
    }

    protected open fun <T> handelError(promise: ApiDeferred<T>, req: Request, res: Response, error: FuelError) {
        val apiError = ApiError(req, res, error.exception)

        try {
            promise.promise.fail {
                Fulton.context.errorHandler.onError(apiError)
            }

            promise.reject(apiError)
        } catch (e: Exception) {
            Log.e(TAG, "Error Handle failed", e)
        }
    }

    protected open fun cacheData(url: String, cache: Int, byteArray: ByteArray) {
        Fulton.context.cacheManagement.add(this.javaClass.simpleName, url, cache, byteArray)
    }

    open fun cleanCache() {
        Fulton.context.cacheManagement.clean(this.javaClass.simpleName)
    }
}
