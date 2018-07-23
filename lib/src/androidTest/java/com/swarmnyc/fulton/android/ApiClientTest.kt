package com.swarmnyc.fulton.android

import android.support.test.runner.AndroidJUnit4
import android.util.Log
import com.swarmnyc.fulton.android.util.await
import com.swarmnyc.fulton.android.util.toJson
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch


private const val UrlRoot = "http://api.fulton.com"

@RunWith(AndroidJUnit4::class)
class ApiClientTest {
    companion object {
        val TAG = ApiClientTest::class.java.simpleName!!
    }

    @Test
    fun treadTest() {
        // there will create 3 thread, 1. main thread, 2. promise body thread 3. result(success, fail, always) thread
        val mainThread = Thread.currentThread().id
        val promise = deferred<Unit, Throwable>()
        Log.d(TAG, "Main Thread Id : ${Thread.currentThread().id}")
        promise.promise.context.workerContext.offer {
            Log.d(TAG, "Promise Thread Id : ${Thread.currentThread().id}")
            promise.resolve(Unit)
        }

        val latch = CountDownLatch(0)
        promise.promise.success {
            Log.d(TAG, "Success Thread Id : ${Thread.currentThread().id}")
            assertNotEquals(mainThread, Thread.currentThread().id)
        }.always {
            Log.d(TAG, "Always Thread Id : ${Thread.currentThread().id}")
            assertNotEquals(mainThread, Thread.currentThread().id)

            latch.countDown()
        }

        latch.await()
    }

    @Test
    fun requestSuccessUnitTest() {
        val apiClient = object : ApiClient() {
            override val urlRoot: String = UrlRoot

            fun get(): Promise<Unit?, ApiError> {
                return request {

                }
            }

            override fun <T> execRequest(promise: Deferred<T, ApiError>, req: Request) {
                val res = Response(urlRoot, 200)

                handleResponse(promise, req, res)
            }
        }

        val result = apiClient.get().await()

        assertEquals(Unit, result)
    }

    @Test
    fun requestSuccessStringTest() {
        val apiClient = object : ApiClient() {
            override val urlRoot: String = UrlRoot

            fun get(): Promise<String?, ApiError> {
                return request {

                }
            }

            override fun <T> execRequest(promise: Deferred<T, ApiError>, req: Request) {
                val res = Response(urlRoot, 200, data = "TEST".toByteArray())

                handleResponse(promise, req, res)
            }
        }

        val result = apiClient.get().await()

        assertEquals("TEST", result)
    }

    @Test
    fun requestErrorTest() {
        val apiClient = object : ApiClient() {
            override val urlRoot: String = UrlRoot

            fun get(): Promise<String?, ApiError> {
                return request {

                }
            }

            override fun <T> execRequest(promise: Deferred<T, ApiError>, req: Request) {
                val res = Response(urlRoot, 400, data = "TEST".toByteArray())

                handleResponse(promise, req, res)
            }
        }

        var result: String? = null
        apiClient.get()
                .fail {
                    result = it.cause?.message
                    it.isHandled = true
                }.await()

        assertEquals("TEST", result)

    }

    @Test
    fun requestJoinTest() {
        val apiClient = object : ApiClient() {
            override val urlRoot: String = UrlRoot

            fun method1(): Promise<String, ApiError> {
                return request {}
            }

            fun method2(value: String): Promise<Int, ApiError> {
                return request {}
            }

            override fun <T> execRequest(promise: Deferred<T, ApiError>, req: Request) {
                val res = Response(urlRoot, 200, data = "1234".toByteArray())

                handleResponse(promise, req, res)
            }
        }


        val result: Int = apiClient.method1().then { apiClient.method2(it) }.await()!!

        assertEquals(1234, result)
    }

    @Test()
    fun dataTypeTest() {
        val json = listOf(ModelA("A", 1, listOf(ModelB("AB", 1))), ModelA("B", 2, listOf())).toJson()
        val apiClient = object : ApiClient() {
            override val urlRoot: String = UrlRoot

            fun method1(): ApiPromise<List<ModelA>> {
                return request {
                    method = Method.GET
                    paths = listOf("list")
                    subResultType = listOf(ModelA::class.java)
                }
            }

            override fun <T> execRequest(promise: Deferred<T, ApiError>, req: Request) {
                val res = Response(urlRoot, 200, data = json.toByteArray())

                handleResponse(promise, req, res)
            }
        }

        val result = apiClient.method1().await()!!

        assertEquals(2, result.size)
        assertEquals("A", result[0].name)
        assertEquals(1, result[0].list.size)
        assertEquals("AB", result[0].list[0].name)
        assertEquals("B", result[1].name)
    }

    @Test
    fun errorHandleTest() {
        val apiClient = object : ApiClient() {
            override val urlRoot: String = UrlRoot

            fun get(): Promise<String?, ApiError> {
                return request {}
            }

            override fun <T> execRequest(promise: Deferred<T, ApiError>, req: Request) {
                val res = Response(urlRoot, 400, data = "TEST".toByteArray())

                handleResponse(promise, req, res)
            }
        }

        val latch = CountDownLatch(1)
        var result = false

        Fulton.context.errorHandler = object : ApiErrorHandler {
            override fun onError(apiError: ApiError) {
                Log.d(TAG, "error called")
                assertEquals(true, apiError.isHandled)
                result = true

                latch.countDown()
            }
        }

        apiClient.get()
                .fail {
                    it.isHandled = true
                }

        latch.await()
        assertEquals(true, result)
    }
}

data class ModelA(val name: String, val index: Int, val list: List<ModelB>)

data class ModelB(val name: String, val index: Int)