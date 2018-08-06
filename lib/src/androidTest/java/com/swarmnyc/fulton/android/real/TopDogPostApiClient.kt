package com.swarmnyc.fulton.android.real

import com.swarmnyc.fulton.android.http.ApiClient
import com.swarmnyc.fulton.android.http.ApiManyResult
import com.swarmnyc.fulton.android.model.TopDogPost
import com.swarmnyc.fulton.android.promise.Promise

class TopDogPostApiClient : ApiClient() {
    override val urlRoot: String = "https://topdog.varick.io/api/"

    fun listPosts(): Promise<ApiManyResult<TopDogPost>> {
        return request {
            paths("posts")
            query("includes" to listOf("tags", "anchor"))
            subResultType(TopDogPost::class.java)
        }
    }

    fun error404(): Promise<ApiManyResult<TopDogPost>> {
        return request {
            paths("404")
            subResultType(TopDogPost::class.java)
        }
    }
}