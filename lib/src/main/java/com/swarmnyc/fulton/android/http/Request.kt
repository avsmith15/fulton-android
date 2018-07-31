package com.swarmnyc.fulton.android.http

import com.swarmnyc.fulton.android.Fulton
import com.swarmnyc.fulton.android.error.ApiError
import com.swarmnyc.fulton.android.util.JsonGenericType
import com.swarmnyc.fulton.android.util.urlEncode
import java.lang.reflect.Type
import java.net.URI

class Request {
    var connectionTimeOutMs = Fulton.context.connectTimeoutMs
    var readTimeOutMs = Fulton.context.readTimeOutMs
    var method: Method = Method.GET

    var urlRoot: String? = null
    var dataType: Type? = null

    var cacheDurationMs: Int = Fulton.context.defaultCacheDurationMs

    var url: String? = null

    var useGzip: Boolean = Fulton.context.defaultUseGzip
    var body: Any? = null

    var startedAt = System.currentTimeMillis()

    /**
     * get or set the path of the url
     * */
    var paths: List<String>? = null

    /**
     * add the path of the url
     * */
    fun paths(vararg values: String) {
        if (this.paths == null) {
            this.paths = values.asList()
        } else {
            this.paths = this.paths!! + values
        }
    }

    /**
     * get or set the query of the url
     * */
    var query: Map<String, Any>? = null

    /**
     * add the query of the url
     * */
    fun query(vararg values: Pair<String, Any>) {
        if (this.query == null) {
            this.query = values.toMap()
        } else {
            this.query = this.query!! + values
        }
    }

    /**
     * get or set the headers of the request
     * the default values are
     * Content-Type: application/json
     * Accept: application/json
     * */
    var headers: MutableMap<String, String> = mutableMapOf("Content-Type" to "application/json", "Accept" to "application/json")

    /**
     * add the headers of the request
     * */
    fun headers(vararg values: Pair<String, String>) {
        headers.putAll(values)
    }

    var queryParams: QueryParams? = null

    fun queryParams(block: QueryParams.() -> Unit){
        queryParams = QueryParams().apply(block)
    }

    /**
     * get or set the result type of the request
     * */
    var subResultType: List<Type>? = null

    /**
     * add the result type of the request
     * */
    fun subResultType(vararg values: Type) {
        if (this.subResultType == null) {
            this.subResultType = values.asList()
        } else {
            this.subResultType = this.subResultType!! + values
        }
    }

    /**
     * if the value is set, will use this response directly
     */
    var mockResponse: Response? = null

    /**
     * the function of building url
     */
    fun buildUrl() {
        val u = buildString {
            append(urlRoot)

            if (paths?.isNotEmpty() == true) {
                paths!!.forEach { append("/$it") }
            }

            if (queryParams != null) {
                append(queryParams!!.toQueryString())
            }

            if (query?.isNotEmpty() == true) {
                append("?")

                append(query!!.entries.joinToString("&") {
                    "${it.key}=${it.value.toString().urlEncode()}"
                })
            }
        }

        // the normalize doesn't remove double // on below API 21
        this.url = URI.create(u).normalize().toString()
    }

    fun buildDataType() {
        if (dataType != null && subResultType != null) {
            this.dataType = JsonGenericType(dataType!!, *subResultType!!.toTypedArray())
        }
    }

    internal fun verify(): Exception? {
        if (url == null) return NullPointerException("Request.url cannot be null")
        if (dataType == null) return NullPointerException("Request.dataType cannot be null")

        return null
    }
}


