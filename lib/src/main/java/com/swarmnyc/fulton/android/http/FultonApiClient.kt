package com.swarmnyc.fulton.android.http

import com.swarmnyc.fulton.android.error.ApiError
import com.swarmnyc.fulton.android.error.FultonApiError
import com.swarmnyc.fulton.android.error.HttpApiError

/**
 * Api Client for Fulton which is restFul styles api
 * */
abstract class FultonApiClient : ApiClient() {
    protected var identityManager = context.identityManager

    override fun initRequest(req: Request) {
        if (identityManager.isValid()) {
            identityManager.token!!.apply {
                if (tokenType.toLowerCase() == "bearer") {
                    identityManager.token!!.apply {
                        req.headers("Authorization" to "$tokenType $accessToken")
                    }
                }
            }
        }
    }

    /**
     * make a GET request to get list of entities
     */
    protected inline fun <reified T> list(queryParams: QueryParams? = null, noinline builder: (Request.() -> Unit)? = null): ApiPromise<ApiManyResult<T>> {
        return request {
            this.subResultType(T::class.java)

            this.queryParams = queryParams

            if (builder != null) builder(this)
        }
    }

    /**
     * make a GET request to get a single entity
     */
    protected inline fun <reified T : Any> detail(id: Any, queryParams: QueryParams? = null, noinline builder: (Request.() -> Unit)? = null): ApiPromise<T> {
        return request {
            // override the dataType because result is { data : T }, but convert to T, so when using can skip .data
            this.dataType = ApiOneResult::class.java
            this.subResultType(T::class.java)

            this.queryParams = queryParams

            if (builder != null) builder(this)

            this.paths(id.toString())
        }
    }

    /**
     * make POST request to create an entity
     */
    protected inline fun <reified T : Any> create(entity: T, noinline builder: (Request.() -> Unit)? = null): ApiPromise<T> {
        return request {
            this.method = Method.POST
            this.dataType = ApiOneResult::class.java
            this.subResultType(T::class.java)
            this.body = entity

            if (builder != null) builder(this)
        }
    }

    /**
     * make a Put request to update the entity
     */
    protected fun <T> update(id: Any, entity: T, builder: (Request.() -> Unit)? = null): ApiPromise<Unit> {
        return request {
            this.method = Method.PUT
            this.body = entity

            if (builder != null) builder(this)

            this.paths(id.toString())
        }
    }

    /**
     * make a Put request to update the partial of the entity
     */
    protected fun update(id: Any, partialEntity: Map<String, Any>, builder: (Request.() -> Unit)? = null): ApiPromise<Unit> {
        return request {
            this.method = Method.PUT
            this.body = partialEntity

            if (builder != null) builder(this)

            this.paths(id.toString())
        }
    }

    /**
     * make a Delete request to delete the entity
     */
    protected fun delete(id: Any, builder: (Request.() -> Unit)? = null): ApiPromise<Unit> {
        return request {
            this.method = Method.DELETE

            if (builder != null) builder(this)

            this.paths(id.toString())
        }
    }

    override fun createError(req: Request, res: Response): ApiError {
        return if (res.isJson) {
            FultonApiError(req, res)
        } else {
            HttpApiError(req, res)
        }
    }
}

