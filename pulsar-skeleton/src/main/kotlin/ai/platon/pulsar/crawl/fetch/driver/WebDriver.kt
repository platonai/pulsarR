package ai.platon.pulsar.crawl.fetch.driver

import ai.platon.pulsar.browser.common.BrowserSettings
import ai.platon.pulsar.crawl.fetch.privacy.BrowserInstanceId
import ai.platon.pulsar.persist.metadata.BrowserType
import org.jsoup.Connection
import java.io.Closeable
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

interface WebDriver: Closeable {
    val id: Int
    val url: String
    val browserInstanceId: BrowserInstanceId

    val lastActiveTime: Instant
    val idleTimeout: Duration
    val isIdle get() = Duration.between(lastActiveTime, Instant.now()) > idleTimeout

    val isCanceled: Boolean
    val isQuit: Boolean
    val isRetired: Boolean

    val name: String
    val browserType: BrowserType
    val supportJavascript: Boolean
    val isMockedPageSource: Boolean
    val sessionId: String?
    val delayPolicy: (String) -> Long get() = { 300L + Random.nextInt(500) }

    suspend fun currentUrl(): String
    suspend fun pageSource(): String?

    suspend fun navigateTo(url: String)
    suspend fun setTimeouts(driverConfig: BrowserSettings)

    suspend fun bringToFront()
    suspend fun waitFor(selector: String): Long
    suspend fun waitFor(selector: String, timeoutMillis: Long): Long
    suspend fun exists(selector: String): Boolean
    suspend fun type(selector: String, text: String)
    suspend fun click(selector: String, count: Int = 1)
    suspend fun scrollTo(selector: String)
    suspend fun scrollDown(count: Int = 1)
    suspend fun scrollUp(count: Int = 1)

    suspend fun outerHTML(selector: String): String?
    suspend fun firstText(selector: String): String?
    suspend fun allTexts(selector: String): String?
    suspend fun firstAttr(selector: String, attrName: String): String?
    suspend fun allAttrs(selector: String, attrName: String): String?

    suspend fun getCookies(): List<Map<String, String>>
    suspend fun evaluate(expression: String): Any?
    suspend fun evaluateSilently(expression: String): Any?

    suspend fun newSession(): Connection

    suspend fun stop()

    fun free()
    fun startWork()
    fun retire()
    fun quit()
    fun cancel()
}
