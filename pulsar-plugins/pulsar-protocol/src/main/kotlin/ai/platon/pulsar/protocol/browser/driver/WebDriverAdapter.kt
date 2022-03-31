package ai.platon.pulsar.protocol.browser.driver

import ai.platon.pulsar.browser.common.BrowserSettings
import ai.platon.pulsar.crawl.fetch.driver.AbstractWebDriver
import ai.platon.pulsar.crawl.fetch.driver.WebDriver
import ai.platon.pulsar.crawl.fetch.privacy.BrowserInstanceId
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class WebDriverAdapter(
    browserInstanceId: BrowserInstanceId,
    val driver: WebDriver,
    val priority: Int = 1000,
) : AbstractWebDriver(browserInstanceId, instanceSequencer.incrementAndGet()) {
    companion object {
        val instanceSequencer = AtomicInteger()
    }

    private val logger = LoggerFactory.getLogger(WebDriverAdapter::class.java)

    val pageViews = AtomicInteger()

    /**
     * The actual url return by the browser
     * */
    override suspend fun currentUrl(): String {
        return if (isQuit) "" else
            kotlin.runCatching { driver.currentUrl() }
                .onFailure { logger.warn("Unexpected exception", it) }
                .getOrElse { "" }
    }

    /**
     * The real time page source return by the browser
     * */
    override suspend fun pageSource(): String? = driver.pageSource()

    /**
     * The id of the session to the browser
     * */
    override val sessionId: String?
        get() = when {
            isQuit -> null
            else -> driver.sessionId
        }

    /**
     * The browser type
     * */
    override val browserType get() = driver.browserType

    override val supportJavascript get() = driver.supportJavascript

    override val isMockedPageSource get() = driver.isMockedPageSource

    /**
     * Navigate to the url
     * The browser might redirect, so it might not be the same to [currentUrl]
     * */
    override suspend fun navigateTo(url: String) {
        if (isWorking) {
            lastActiveTime = Instant.now()
            driver.navigateTo(url)
            this.url = url
            pageViews.incrementAndGet()
            lastActiveTime = Instant.now()
        }
    }

    override suspend fun waitFor(selector: String) = driver.waitFor(selector)

    override suspend fun waitFor(selector: String, timeoutMillis: Long) = driver.waitFor(selector, timeoutMillis)

    override suspend fun exists(selector: String) = driver.exists(selector)

    override suspend fun click(selector: String, count: Int) = driver.click(selector, count)

    override suspend fun scrollTo(selector: String) = driver.scrollTo(selector)

    override suspend fun type(selector: String, text: String) = driver.type(selector, text)

    override suspend fun evaluate(expression: String): Any? {
        return when {
            isNotWorking -> null
            else -> driver.evaluate(expression)
        }
    }

    override suspend fun getCookies() = driver.getCookies()

    override suspend fun bringToFront() {
        driver.takeIf { isWorking }?.runCatching { bringToFront() }
    }

    override suspend fun stop() {
        driver.takeIf { isWorking }?.runCatching { stop() }
    }

    override suspend fun setTimeouts(driverConfig: BrowserSettings) {
        if (isNotWorking) {
            return
        }

        driver.setTimeouts(driverConfig)
    }

    /**
     * Quits this driver, close every associated window
     * */
    override fun quit() {
        if (!isQuit) {
            synchronized(status) {
                if (!isQuit) {
                    status.set(Status.QUIT)
                    driver.runCatching { quit() }.onFailure { logger.warn("Unexpected exception", it) }
                }
            }
        }
    }

    override fun close() = driver.close()
}
