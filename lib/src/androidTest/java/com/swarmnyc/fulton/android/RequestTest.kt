package com.swarmnyc.fulton.android

import android.support.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RequestTest {
    companion object {
        val TAG = RequestTest::class.java.simpleName
    }

    @Test
    fun buildUrlTest() {
        val urlRoot = "http://api.fulton.com"

        var options = Request()
        options.urlRoot = urlRoot
        options.paths = listOf("news")
        options.buildUrl()

        assertEquals("http://api.fulton.com/news", options.url)

        options = Request()
        options.urlRoot = urlRoot
        options.paths = listOf("/news")
        options.buildUrl()

        assertEquals("http://api.fulton.com/news", options.url)

        options = Request()
        options.urlRoot = urlRoot
        options.paths = listOf("news", "id")
        options.buildUrl()

        assertEquals("http://api.fulton.com/news/id", options.url)

        options = Request()
        options.urlRoot = urlRoot
        options.paths= listOf("/news")
        options.query = mapOf("k1" to "a", "k2" to 2, "k3" to "&")
        options.buildUrl()

        assertEquals("http://api.fulton.com/news?k1=a&k2=2&k3=%26", options.url)
    }
}
