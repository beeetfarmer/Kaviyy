package com.nl.kaviyy.core.network

import coil3.asImage
import coil3.request.ImageRequest
import com.nl.kaviyy.core.model.auth.Credentials
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okio.Buffer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ServerImageLoadersTest {
    private val mock = MockWebServer().apply { start() }

    @After
    fun tearDown() = mock.close()

    @Test
    fun cacheKeysDropCredentialParams() {
        assertEquals(
            "https://k.lan/api/Image/series-cover?seriesId=4",
            credentialFreeKey("https://k.lan/api/Image/series-cover?seriesId=4&apiKey=SECRET"),
        )
        assertEquals("https://k.lan/x?a=1", credentialFreeKey("https://k.lan/x?APIKEY=s&a=1&token=t"))
        assertNull(credentialFreeKey("https://k.lan/api/Image/series-cover?seriesId=4"))
        assertNull(credentialFreeKey("not a url"))
    }

    @Test
    fun everyServerSharesOneMemoryCacheThatSignOutClears() {
        val context = RuntimeEnvironment.getApplication()

        fun loader(id: Long) =
            ServerImageLoaders.create(
                context,
                Server(ServerId(id), "S$id", (ServerUrl.parse("https://s$id.lan") as ServerUrlResult.Valid).url),
                okhttp3.OkHttpClient(),
            )
        val a = loader(1)
        val b = loader(2)
        val memory = a.memoryCache
        assertTrue(memory != null && memory === b.memoryCache) // not one heap share per server
        memory.set(
            coil3.memory.MemoryCache.Key("k"),
            coil3.memory.MemoryCache.Value(
                android.graphics.Bitmap
                    .createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
                    .asImage(),
            ),
        )
        ServerImageLoaders.clearMemory()
        assertEquals(0L, memory.size)
    }

    @Test
    fun imagesUseTheServersAuthenticatedClient() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val url = (ServerUrl.parse(mock.url("/").toString()) as ServerUrlResult.Valid).url
            val server = Server(ServerId(9), "S", url, allowInsecure = true)
            val source =
                object : CredentialSource {
                    override fun current() = Credentials.AuthKey("img-key")

                    override fun update(credentials: Credentials.Jwt) = Unit

                    override fun signOut() = Unit
                }
            val png = Buffer().write(Base64.getDecoder().decode(PNG_1X1))
            mock.enqueue(
                MockResponse
                    .Builder()
                    .headers(headersOf("Content-Type", "image/png"))
                    .body(png)
                    .build(),
            )
            val client = ServerHttpClients(baseHttpClient()).create(server, source)
            val loader = ServerImageLoaders.create(context, server, client)
            val image = mock.url("/api/Image/series-cover?seriesId=1").toString()
            loader.execute(ImageRequest.Builder(context).data(image).build()) // decode result is irrelevant here
            assertEquals("img-key", mock.takeRequest().headers[API_KEY])
            assertTrue(ServerImageLoaders.cacheDir(context, server).path.endsWith("images/9"))
        }

    private companion object {
        const val PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    }
}
