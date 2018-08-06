package com.swarmnyc.fulton.android.util

import com.swarmnyc.fulton.android.error.ApiError
import com.swarmnyc.fulton.android.promise.Promise
import java.util.concurrent.CountDownLatch

fun <T> Promise<T>.await(): T? {
    val latch = CountDownLatch(1)

    var t: T? = null
    var e: Throwable? = null

    this.then {
        t = it
        latch.countDown()
    }.catch {
        e = it
        latch.countDown()
    }

    latch.await()
    if (e != null) {
        if ((e as? ApiError)?.isHandled != true) {
            throw e!!
        }
    }

    return t
}