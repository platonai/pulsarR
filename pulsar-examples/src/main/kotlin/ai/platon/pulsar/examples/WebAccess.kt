package ai.platon.pulsar.examples

import ai.platon.pulsar.PulsarContext
import ai.platon.pulsar.PulsarEnv
import ai.platon.pulsar.common.options.LoadOptions
import ai.platon.pulsar.common.URLUtil
import ai.platon.pulsar.persist.WebPageFormatter
import com.google.common.collect.Lists
import org.slf4j.LoggerFactory

object WebAccess {
    private val env = PulsarEnv.getOrCreate()
    private val pc = PulsarContext.getOrCreate()
    private val i = pc.createSession()
    private val log = LoggerFactory.getLogger(WebAccess::class.java)

    private val seeds = mapOf(
            0 to "https://www.mia.com/formulas.html",
            1 to "https://www.mia.com/diapers.html",
            2 to "http://category.dangdang.com/cid4002590.html",
            3 to "https://list.mogujie.com/book/magic/51894",
            4 to "https://category.vip.com/search-1-0-1.html?q=3|49738||&rp=26600|48483&ff=|0|2|1&adidx=2&f=ad&adp=130610&adid=632686",
            5 to "https://list.jd.com/list.html?cat=6728,6742,13246",
            6 to "https://list.gome.com.cn/cat10000055-00-0-48-1-0-0-0-1-2h8q-0-0-10-0-0-0-0-0.html?intcmp=bx-1000078331-1",
            7 to "https://search.yhd.com/c0-0/k%25E7%2594%25B5%25E8%25A7%2586/",
            9 to "https://music.163.com/",
            10 to "https://news.sogou.com/ent.shtml",
            11 to "http://shop.boqii.com/brand/",
            12 to "https://list.gome.com.cn/cat10000070-00-0-48-1-0-0-0-1-0-0-1-0-0-0-0-0-0.html?intcmp=phone-163"
    )

    private val trivialUrls = listOf(
            "http://futures.hexun.com/2019-06-13/197504448.html",
            "http://www.drytailings.cn/case_tiekuang_xixuan_shebei.html",
            "http://futures.jrj.com.cn/2019/06/04095227661424.shtml",
            "http://futures.cnfol.com/mingjialunshi/20190614/27538801.shtml",
            "http://www.51wctt.com/News/43726/Detail/2",
            "http://www.ijiuai.com/keji/588723.html",
            "http://m.ali213.net/news/gl1906/341009_2.html",
            "http://futures.eastmoney.com/qihuo/i.html",
            "https://news.smm.cn/news/100936727",
            "http://www.96369.net/Indices/125",
            "http://zsjjyjy27.cn.b2b168.com/shop/supply/36379543.html",
            "http://www.b2b168.com/",
            "http://www.96369.net/indices/1003",
            "https://tianjiaji.b2b168.com/ranyoutianjiaji/ranseji/"
    )

    private val loadOptions = "-expires 1d"

    fun load() {
        val url = "https://list.mogujie.com/book/magic/51894 -expires 1s"
        // val url = "https://www.mia.com/formulas.html -expires 1s -pageLoadTimeout 1m"
//        val url = "http://category.dangdang.com/cid4002590.html -expires 1s"
        val page = i.load(url)
        val doc = i.parse(page)
        doc.absoluteLinks()
        doc.stripScripts()
        val path = i.export(doc)
        log.info("Export to: file://{}", path)
    }

    fun loadOutPages() {
        // val url = seeds[0]?:return
        val url = "https://list.mogujie.com/book/magic/51894"

        val args = "-ps -expires 1s"
        val outlink = ".goods_list_mod a"

        val page = i.load("$url $args")
        val document = i.parse(page)
        val path = i.export(page)
        println("Export to: file://$path")

//        val links = document.select(outlink) { it.attr("abs:href") }
//        i.loadAll(links, LoadOptions.parse("-expires 1s"))
        // page.links.stream().parallel().forEach { i.load("$it") }
        // println(WebPageFormatter(page).withLinks())
    }

    fun parallelLoadAllOutPages() {
        val args = "-parse -expires 1s -preferParallel true"
        val options = LoadOptions.parse(args)
        val tasks = i.loadAll(seeds.values, options).flatMap { it.links }.map { it.toString() }
                .groupBy { URLUtil.getHost(it, URLUtil.GroupMode.BY_DOMAIN) }.toList()
        Lists.partition(tasks, PulsarEnv.NCPU).forEach { partition ->
            partition.parallelStream().forEach { (_, urls) ->
                pc.createSession().use {
                    it.loadAll(urls.distinct().shuffled().take(10), options)
                }
            }
        }
    }

    fun loadAllProducts() {
        val url = seeds[2]?:return
        // val outlinkSelector = ".goods_item a[href~=detail]"
        val outlinkSelector = ".cloth_shoplist li a.pic"

        val links = i.load("$url -expires 1s")
                .let { i.parse(it) }
                .select(outlinkSelector) { it.attr("href") }
                .sortedBy { it.length }
                .take(40)
        log.info("Loading {} pages", links.size)
        val pages = i.loadAll(links, LoadOptions.parse("-retry -expires 1s"))

        println(pages.size)
    }

    fun parallelLoadAllProducts() {
        val url = seeds[3]?:return

        val portal = i.load("$url $loadOptions")
        val doc = i.parse(portal)
        doc.select(".goods_item a[href~=detail]")
                .map { it.attr("abs:href").substringBefore("?") }
                .forEach { portal.vividLinks[it] = "" }
        println(WebPageFormatter(portal))
        println(portal.simpleVividLinks)
        val links = portal.simpleLiveLinks.filter { it.contains("detail") }
        val pages = i.parallelLoadAll(links, LoadOptions.parse("-ps"))
        pages.forEach { println("${it.url} ${it.pageTitle}") }
    }

    fun loadAllNews() {
        val url = seeds[8]?:return

        val portal = i.load("$url $loadOptions")
        val links = portal.simpleLiveLinks.filter { it.contains("jinrong") }
        val pages = i.parallelLoadAll(links, LoadOptions.Companion.parse("--parse"))
        pages.forEach { println("${it.url} ${it.contentTitle}") }
    }

    fun scan() {
        val contractBaseUri = "http://www.ccgp-hubei.gov.cn:8040/fcontractAction!download.action?path="
        pc.scan(contractBaseUri).forEachRemaining {
            val size = it.content?.array()?.size?:0
            println(size)
        }
    }

    fun piped() {
        val url = seeds[8]?:return

        arrayOf(url)
                .map { i.load(it) }
                .map { i.parse(it) }
                .forEach { println("${it.location} ${it.title}") }
    }

    fun truncate() {
        pc.webDb.truncate()
    }

    fun run() {
        // load()
        loadOutPages()
        // loadAllProducts()
        // parallelLoadAll()
        // parallelLoadAllProducts()
    }
}

fun main() {
    WebAccess.run()
}
