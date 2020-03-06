package ai.platon.pulsar.examples

import ai.platon.pulsar.PulsarContext
import ai.platon.pulsar.PulsarEnv
import ai.platon.pulsar.common.*
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.util.Comparator
import kotlin.streams.toList

object WebTester {
    private val log = LoggerFactory.getLogger(WebTester::class.java)
    private val i = PulsarContext.getOrCreate().createSession()

    fun load() {
        val url = "https://www.finishline.com/store/men/shoes/_/N-1737dkj?mnid=men_shoes"
        val args = " -i 1s"
        val page = i.load("$url $args")
        val doc = i.parse(page)
        doc.absoluteLinks()
        doc.stripScripts()

        doc.select("a") { it.attr("abs:href") }.asSequence()
                .filter { Urls.isValidUrl(it) }
                .take(10)
                .joinToString("\n") { it }
                .also { println(it) }

        val path = i.export(doc)
        log.info("Export to: file://{}", path)
    }
}

fun main() {
//    WebTester.load()
//
//    PulsarEnv.shutdown()

//    Files.walk(AppPaths.BROWSER_TMP_DIR).forEach {
//        println(it)
//    }

    println("\n\n")

    Files.walk(AppPaths.BROWSER_TMP_DIR).filter {
        it.endsWith("Local Storage") || it.endsWith("Session Storage") || it.endsWith("Cookies")
    }.forEach { println(it) }
}
