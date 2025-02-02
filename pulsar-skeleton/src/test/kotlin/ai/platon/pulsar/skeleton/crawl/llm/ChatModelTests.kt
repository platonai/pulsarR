package ai.platon.pulsar.skeleton.crawl.llm

import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.config.KConfiguration
import ai.platon.pulsar.dom.Documents
import ai.platon.pulsar.external.ResponseState
import ai.platon.pulsar.skeleton.context.PulsarContexts
import ai.platon.pulsar.skeleton.context.support.ContextDefaults
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TODO: not sure if EnabledIfEnvironmentVariable works
 * */
@EnabledIfEnvironmentVariable(named = "llm.name", matches = ".+")
class ChatModelTests {
    
    companion object {
        private val url = "https://www.amazon.com/dp/B0C1H26C46"
        private val args = "-requireSize 200000"
        private val productHtml = ResourceLoader.readString("pages/amazon/B0C1H26C46.original.htm")
        private val productText = ResourceLoader.readString("prompts/product.txt")
        
        private val session = PulsarContexts.createSession()
        private val llm = session.sessionConfig["llm.name"]
        private val apiKey = session.sessionConfig["llm.apiKey"]
        private val modelAvailable get() = llm != null && apiKey != null
        
        @BeforeAll
        @JvmStatic
        fun checkConfiguration() {
            if (!modelAvailable) {
                println("=========================== LLM NOT CONFIGURED ==========================================")
                println("> Skip the tests because the API key is not set")
                println("> Please set the API key in the configuration file or environment variable")
                println("> The configuration file can be found in: " + KConfiguration.EXTERNAL_RESOURCE_BASE_DIR)
                println("> All xml files in the directory will be loaded as the configuration file")
            }
        }
    }
    
    /**
     * Test configuration
     * */
    @Test
    @EnabledIf("#{model != null}")
    fun `When check configuration then it works`() {
        val conf = ContextDefaults().unmodifiedConfig
        
        val model = conf.get("llm.name")
        val apiKey = conf.get("llm.apiKey")
        
        println(model)
        println(apiKey)
        
        val model2 = session.unmodifiedConfig.get("llm.name")
        val apiKey2 = session.unmodifiedConfig.get("llm.apiKey")
        
        assertEquals(model, model2)
        assertEquals(apiKey, apiKey2)
    }
    
    @Test
    fun `When chat to LLM then it responds`() {
        if (!modelAvailable) return
        
        val prompt = "以下是一个电商网站的网页内容，找出商品标题和商品价格：$productText"
        val response = session.chat(prompt)
        println(response.content)
        assertTrue { response.content.isNotEmpty() }
        assertTrue { response.tokenUsage.inputTokenCount > 0 }
    }
    
    /**
     * TODO: @EnabledIf is not working
     * */
    @Test
    @EnabledIf("#{model != null}")
    fun `Should generate answer and return token usage and finish reason stop`() {
        if (!modelAvailable) return
        
        val document = Documents.parse(productHtml, url)
        val prompt = "以下是一个电商网站的网页内容，找出商品标题、商品价格："
        val response = session.chat(document, prompt)
        println(response.content)
        
        assertTrue { response.tokenUsage.inputTokenCount > 0 }
        assertTrue { response.tokenUsage.outputTokenCount > 0 }
        assertTrue { response.tokenUsage.totalTokenCount > 0 }
        assertTrue { response.state == ResponseState.STOP }
    }
}
