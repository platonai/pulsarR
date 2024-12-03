package ai.platon.pulsar.browser.driver.chrome.impl

import ai.platon.pulsar.browser.driver.chrome.DefaultWebSocketContainerFactory
import ai.platon.pulsar.browser.driver.chrome.Transport
import ai.platon.pulsar.browser.driver.chrome.util.ChromeIOException
import ai.platon.pulsar.browser.driver.chrome.util.ChromeDriverException
import ai.platon.pulsar.common.brief
import ai.platon.pulsar.common.config.AppConstants
import ai.platon.pulsar.common.warnForClose
import com.codahale.metrics.SharedMetricRegistries
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import javax.websocket.*

class TransportImpl : Transport {
    private val logger = LoggerFactory.getLogger(TransportImpl::class.java)
    private val tracer = logger.takeIf { it.isTraceEnabled }
    private val closed = AtomicBoolean()
    
    private lateinit var session: Session
    private val metricsPrefix = "c.i.WebSocketClient"
    private val metrics = SharedMetricRegistries.getOrCreate(AppConstants.DEFAULT_METRICS_NAME)
    private val meterRequests = metrics.meter("$metricsPrefix.requests")
    
    val id = instanceSequencer.incrementAndGet()
    override val isOpen: Boolean get() = session.isOpen || !closed.get()
    
    class DevToolsMessageHandler(val consumer: Consumer<String>) : MessageHandler.Whole<String> {
        override fun onMessage(message: String) {
            consumer.accept(message)
        }
    }
    
    /**
     * Connect the supplied annotated endpoint instance to its server. The supplied
     * object must be a class decorated with the class level.
     *
     * @throws ChromeIOException if the annotated endpoint instance is not valid,
     * or if there was a network or protocol problem that prevented the client endpoint
     * being connected to its server.
     * @throws IllegalStateException if called during the deployment phase of the containing
     * application.
     * */
    @Throws(ChromeIOException::class, IllegalStateException::class)
    override fun connect(uri: URI) {
        val webSocketService = this
        
        val endpoint = object : Endpoint() {
            override fun onOpen(session: Session, config: EndpointConfig) {
                webSocketService.onOpen(session, config)
                // logger.info("Connected to ws server {}", uri)
            }
            
            override fun onClose(session: Session, closeReason: CloseReason) {
                super.onClose(session, closeReason)
                webSocketService.onClose(session, closeReason)
                // logger.info("Closing ws server {}", uri)
            }
            
            override fun onError(session: Session, e: Throwable?) {
                super.onError(session, e)
                webSocketService.onError(session, e)
            }
        }
        
        session = try {
            WEB_SOCKET_CONTAINER.connectToServer(endpoint, uri)
        } catch (e: DeploymentException) {
            logger.warn("Failed to connect to ws server | $uri", e)
            throw ChromeIOException("Failed connecting to ws server | $uri", e)
        } catch (e: IOException) {
            logger.warn("Failed to connect to ws server | $uri", e)
            throw ChromeIOException("Failed connecting to ws server | $uri", e)
        }
    }
    
    @Throws(ChromeIOException::class)
    override fun send(message: String) {
        meterRequests.mark()
        
        try {
            tracer?.trace("Send {}", StringUtils.abbreviateMiddle(message, "...", 500))
            session.basicRemote.sendText(message)
        } catch (e: IllegalStateException) {
            throw ChromeIOException("Failed to send message", e)
        } catch (e: IOException) {
            throw ChromeIOException("Failed to send message", e)
        }
    }
    
    @Throws(ChromeIOException::class)
    override fun sendAsync(message: String): Future<Void> {
        meterRequests.mark()

        return try {
            tracer?.trace("Send {}", StringUtils.abbreviateMiddle(message, "...", 500))
            session.asyncRemote.sendText(message)
        } catch (e: IllegalStateException) {
            throw ChromeIOException("Failed to send message", e)
        } catch (e: IOException) {
            throw ChromeIOException("Failed to send message", e)
        }
    }

    @Throws(ChromeDriverException::class)
    override fun addMessageHandler(consumer: Consumer<String>) {
        if (session.messageHandlers.isNotEmpty()) {
            throw ChromeDriverException("You are already subscribed to this web socket service.")
        }
        
        session.addMessageHandler(DevToolsMessageHandler(consumer))
    }
    
    private fun onOpen(session: Session, config: EndpointConfig) {
        logger.debug("Web socket connected | {}", session.requestURI)
    }
    
    private fun onClose(session: Session, closeReason: CloseReason) {
        /**
         * If closeReason.closeCode CLOSED_ABNORMALLY occurs:
         *
         * CLOSED_ABNORMALLY is a reserved value and MUST NOT be set as a status code in a
         * Close control frame by an endpoint.  It is designated for use in
         * applications expecting a status code to indicate that the
         * connection was closed abnormally, e.g., without sending or
         * receiving a Close control frame.
         */
        if (closeReason.closeCode != CloseReason.CloseCodes.NORMAL_CLOSURE) {
            logger.info("Web socket {} {} | {}", closeReason.reasonPhrase, closeReason.closeCode, session.requestURI)
        } else {
            logger.debug("Web socket {} {}", closeReason.reasonPhrase, closeReason.closeCode)
        }
    }
    
    private fun onError(session: Session, e: Throwable?) {
        logger.error("Web socket error | {}\n>>>{}<<<", session.requestURI, e?.brief())
    }
    
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            // Close the current conversation with a normal status code and no reason phrase.
            if (session.isOpen) {
                session.runCatching { close() }.onFailure { warnForClose(this, it) }
            }
        }
    }

    override fun toString(): String {
        return session.requestURI.toString()
    }
    
    companion object {
        private val instanceSequencer = AtomicInteger()
        
        val WEB_SOCKET_CONTAINER = DefaultWebSocketContainerFactory().wsContainer
        
        /**
         * Creates a WebSocketService and connects to a specified uri.
         *
         * @param uri URI to connect to.
         * @return WebSocketService implementation.
         * @throws ChromeIOException If it fails to connect.
         */
        @Throws(ChromeIOException::class)
        fun create(uri: URI): Transport {
            return TransportImpl().also { it.connect(uri) }
        }
    }
}
