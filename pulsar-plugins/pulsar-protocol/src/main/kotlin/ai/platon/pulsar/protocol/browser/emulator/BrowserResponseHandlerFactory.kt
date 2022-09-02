package ai.platon.pulsar.protocol.browser.emulator

import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.message.MiscMessageWriter
import ai.platon.pulsar.protocol.browser.driver.WebDriverPoolManager

class BrowserResponseHandlerFactory(
        private val driverPoolManager: WebDriverPoolManager,
        private val messageWriter: MiscMessageWriter,
        private val immutableConfig: ImmutableConfig
) {
    private val reflectedHandler by lazy {
        val clazz = immutableConfig.getClass(
                CapabilityTypes.BROWSER_RESPONSE_HANDLER, BrowserResponseHandler::class.java)
        clazz.constructors.first { it.parameters.size == 3 }
                .newInstance(driverPoolManager, messageWriter, immutableConfig) as BrowserResponseHandler
    }

    var specifiedHandler: BrowserResponseHandler? = null

    val eventHandler get() = specifiedHandler ?: reflectedHandler
}
