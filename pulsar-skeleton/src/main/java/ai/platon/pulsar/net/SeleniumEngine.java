package ai.platon.pulsar.net;

import ai.platon.pulsar.common.*;
import ai.platon.pulsar.common.config.ImmutableConfig;
import ai.platon.pulsar.common.config.MutableConfig;
import ai.platon.pulsar.common.config.Params;
import ai.platon.pulsar.common.config.ReloadableParameterized;
import ai.platon.pulsar.crawl.protocol.ForwardingResponse;
import ai.platon.pulsar.crawl.protocol.Response;
import ai.platon.pulsar.net.browser.WebDriverQueues;
import ai.platon.pulsar.persist.WebPage;
import ai.platon.pulsar.persist.metadata.BrowserType;
import ai.platon.pulsar.persist.metadata.MultiMetadata;
import ai.platon.pulsar.persist.metadata.ProtocolStatusCodes;
import com.google.common.collect.Iterables;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.FluentWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ai.platon.pulsar.common.HttpHeaders.*;
import static ai.platon.pulsar.common.config.CapabilityTypes.*;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * Created by vincent on 18-1-1.
 * Copyright @ 2013-2017 Platon AI. All rights reserved
 */
public class SeleniumEngine implements ReloadableParameterized, AutoCloseable {
    public static final Logger LOG = LoggerFactory.getLogger(SeleniumEngine.class);

    // The javascript to execute by Web browsers
    public static BrowserControl browserControl = new BrowserControl();
    private static AtomicInteger batchTaskId = new AtomicInteger(0);

    private ImmutableConfig immutableConfig;
    private MutableConfig defaultMutableConfig;

    private final WebDriverQueues drivers;
    private final GlobalExecutor executor;

    private String supportedEncodings = "UTF-8|GB2312|GB18030|GBK|Big5|ISO-8859-1"
            + "|windows-1250|windows-1251|windows-1252|windows-1253|windows-1254|windows-1257";
    private Pattern HTML_CHARSET_PATTERN;

    private Duration defaultPageLoadTimeout;
    private Duration scriptTimeout;
    private int scrollDownCount;
    private Duration scrollDownWait;
    private String clientJs;

    private AtomicInteger totalTaskCount = new AtomicInteger(0);
    private AtomicInteger totalSuccessCount = new AtomicInteger(0);

    private AtomicInteger batchTaskCount = new AtomicInteger(0);
    private AtomicInteger batchSuccessCount = new AtomicInteger(0);

    private AtomicBoolean closed = new AtomicBoolean(false);

    public static SeleniumEngine getInstance(ImmutableConfig conf) {
        SeleniumEngine engine = ObjectCache.get(conf).getBean(SeleniumEngine.class);
        if (engine == null) {
            engine = new SeleniumEngine(conf);
            ObjectCache.get(conf).put(engine);
        }
        return engine;
    }

    public SeleniumEngine(
            GlobalExecutor executor,
            WebDriverQueues drivers,
            ImmutableConfig immutableConfig) {
        this.executor = executor;
        this.drivers = drivers;
        this.immutableConfig = immutableConfig;

        reload(immutableConfig);
    }

    public SeleniumEngine(ImmutableConfig immutableConfig) {
        executor = GlobalExecutor.getInstance(immutableConfig);
        drivers = new WebDriverQueues(browserControl, immutableConfig);

        reload(immutableConfig);
    }

    @Override
    public ImmutableConfig getConf() {
        return immutableConfig;
    }

    @Override
    public void reload(ImmutableConfig immutableConfig) {
        this.immutableConfig = immutableConfig;
        this.defaultMutableConfig = new MutableConfig(immutableConfig.unbox());

        boolean supportAllCharacterEncodings = immutableConfig.getBoolean(PARSE_SUPPORT_ALL_CHARSETS, false);
        if (supportAllCharacterEncodings) {
            // All charsets are supported by the system
            // The set is big, can use a static cache to hold them if necessary
            supportedEncodings = Charset.availableCharsets().values().stream()
                    .map(Charset::name)
                    .collect(Collectors.joining("|"));
        } else {
            // A limited support charsets
            supportedEncodings = immutableConfig.get(PARSE_SUPPORTED_CHARSETS, supportedEncodings);
        }
        HTML_CHARSET_PATTERN = Pattern.compile(supportedEncodings.replace("UTF-8\\|?", ""), CASE_INSENSITIVE);

        defaultPageLoadTimeout = immutableConfig.getDuration(FETCH_PAGE_LOAD_TIMEOUT, Duration.ofSeconds(30));
        scriptTimeout = immutableConfig.getDuration(FETCH_SCRIPT_TIMEOUT, Duration.ofSeconds(5));

        scrollDownCount = immutableConfig.getInt(FETCH_SCROLL_DOWN_COUNT, 0);
        scrollDownWait = immutableConfig.getDuration(FETCH_SCROLL_DOWN_COUNT, Duration.ofMillis(500));

        clientJs = browserControl.parseJs(true);

        getParams().withLogger(LOG).info();
    }

    @Override
    public Params getParams() {
        return Params.of(
                "supportedEncodings", supportedEncodings,
                "defaultPageLoadTimeout", defaultPageLoadTimeout,
                "scriptTimeout", scriptTimeout,
                "scrollDownCount", scrollDownCount,
                "scrollDownWait", scrollDownWait,
                "clientJsLength", clientJs.length()
        );
    }

    public Response fetch(String url) {
        return fetchContent(WebPage.newWebPage(url, false, defaultMutableConfig));
    }

    public Response fetch(String url, MutableConfig mutableConfig) {
        return fetchContent(WebPage.newWebPage(url, false, mutableConfig));
    }

    public Response fetchContent(WebPage page) {
        // TODO: RejectedExecutionException
        Future<Response> future = executor.getExecutor().submit(() -> fetchContentInternal(page));
        ImmutableConfig conf = page.getMutableConfigOrElse(defaultMutableConfig);
        Duration timeout = conf.getDuration(FETCH_PAGE_LOAD_TIMEOUT, defaultPageLoadTimeout);
        return getResponse(page.getUrl(), future, timeout.plusSeconds(10));
    }

    public Collection<Response> fetchAll(String taskName, Iterable<String> urls) {
        batchTaskCount.set(0);
        batchSuccessCount.set(0);

        return CollectionUtils.collect(urls, this::fetch);
    }

    public Collection<Response> fetchAll(Iterable<String> urls) {
        String taskName = String.valueOf(batchTaskId.incrementAndGet());
        return fetchAll(taskName, urls);
    }

    public Collection<Response> fetchAll(String taskName, Iterable<String> urls, MutableConfig mutableConfig) {
        batchTaskCount.set(0);
        batchSuccessCount.set(0);

        return CollectionUtils.collect(urls, url -> fetch(url, mutableConfig));
    }

    public Collection<Response> fetchAll(Iterable<String> urls, MutableConfig mutableConfig) {
        String taskName = String.valueOf(batchTaskId.incrementAndGet());
        return fetchAll(taskName, urls, mutableConfig);
    }

    public Collection<Response> parallelFetchAll(Iterable<String> urls, MutableConfig mutableConfig) {
        return parallelFetchAllPages(CollectionUtils.collect(urls, WebPage::newWebPage), mutableConfig);
    }

    public Collection<Response> parallelFetchAll(String taskName, Iterable<String> urls, MutableConfig mutableConfig) {
        return parallelFetchAllPages(taskName, CollectionUtils.collect(urls, WebPage::newWebPage), mutableConfig);
    }

    public Collection<Response> parallelFetchAllPages(Iterable<WebPage> pages, MutableConfig mutableConfig) {
        String taskName = String.valueOf(batchTaskId.incrementAndGet());
        return parallelFetchAllPages(taskName, pages, mutableConfig);
    }

    public Collection<Response> parallelFetchAllPages(String taskName, Iterable<WebPage> pages, MutableConfig mutableConfig) {
        int size = Iterables.size(pages);

        LOG.info("Selenium batch task {}: fetching {} pages in parallel", taskName, size);

        batchTaskCount.set(0);
        batchSuccessCount.set(0);

        int priority = mutableConfig.getUint(SELENIUM_WEB_DRIVER_PRIORITY, 0);
        Map<String, Future<Response>> pendingTasks = new HashMap<>();
        Map<String, Response> finishedTasks = new HashMap<>();

        // create a task submitter
        Function<WebPage, Future<Response>> submitter =
                page -> executor.getExecutor().submit(() -> fetchContentInternal(priority, page, mutableConfig));
        // Submit all tasks
        pages.forEach(p -> pendingTasks.put(p.getUrl(), submitter.apply(p)));

        // The function must return in a reasonable time
        final Duration threadTimeout = getPageLoadTimeout(mutableConfig).plusSeconds(10);
        final int numTotalTasks = pendingTasks.size();
        // every page have 30s, and is responsible by different drivers
        final double estimatedTimeout = 30 * numTotalTasks / (1.0 + drivers.getTotalSize());
        final Duration taskTimeout = Duration.ofSeconds(Math.max((int)estimatedTimeout, 3 * 60));
        final int numAllowedFailures = Math.max(10, numTotalTasks / 3);
        AtomicInteger numFailedTasks = new AtomicInteger();

        Instant start = Instant.now();
        Duration elapsed = Duration.ofSeconds(0);

        int i = 0;
        while (finishedTasks.size() < numTotalTasks
                && numFailedTasks.get() <= numAllowedFailures && elapsed.compareTo(taskTimeout) < 0) {
            ++i;

            if (i >= 60 && i % 30 == 0) {
                LOG.warn("Task #{}: round #{}, {} pending, {} finished, {} failed, elapsed: {}, task timeout: {}",
                        taskName, i, pendingTasks.size(), finishedTasks.size(), numFailedTasks,
                        Duration.between(start, Instant.now()), taskTimeout);
            }

            // loop and wait for all parallel tasks return
            Iterator<Map.Entry<String, Future<Response>>> it = pendingTasks.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Future<Response>> entry = it.next();

                if (entry.getValue().isDone()) {
                    try {
                        Response response = getResponse(entry.getKey(), entry.getValue(), threadTimeout);
                        int code = response.getCode();
                        if (code == ProtocolStatusCodes.THREAD_TIMEOUT || code == ProtocolStatusCodes.EXCEPTION) {
                            numFailedTasks.incrementAndGet();
                        }
                        finishedTasks.put(entry.getKey(), response);
                    } catch (Throwable e) {
                        LOG.error("Unexpected error {}", StringUtil.stringifyException(e));
                    }

                    it.remove();
                }
            }

            // TODO: can we avoid this sleep
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}

            elapsed = Duration.between(start, Instant.now());
        }

        if (!pendingTasks.isEmpty()) {
            LOG.warn("Task is incomplete, finsished tasks {}, pending tasks {}, failed tasks: {}, elapsed: {}",
                    finishedTasks.size(), pendingTasks.size(), numFailedTasks, elapsed);
        }

        // if there are still pending tasks, cancel them
        pendingTasks.forEach((url, task) -> {
            // Attempts to cancel execution of this task
            task.cancel(true);
            try {
                Response response = getResponse(url, task, threadTimeout);
                finishedTasks.put(url, response);
            } catch (Throwable e) {
                LOG.error("Unexpected error {}", StringUtil.stringifyException(e));
            }
        });

        return finishedTasks.values();
    }

    private Response fetchContentInternal(WebPage page) {
        return fetchContentInternal(0, page, page.getMutableConfigOrElse(defaultMutableConfig));
    }

    /**
     * Must be thread safe
     * */
    private Response fetchContentInternal(int priority, WebPage page, MutableConfig mutableConfig) {
        String url = page.getUrl();

        totalTaskCount.getAndIncrement();
        batchTaskCount.getAndIncrement();

        WebDriver driver = drivers.poll(priority, mutableConfig);
        if (driver == null) {
            LOG.warn("Failed to get a WebDriver, retry later. Url: " + url);
            return new ForwardingResponse(url, ProtocolStatusCodes.RETRY, new MultiMetadata());
        }

        String pageSource = "";
        int status = ProtocolStatusCodes.SUCCESS_OK;
        MultiMetadata headers = new MultiMetadata();

        try {
            // page.baseUrl is the last working address, and page.url is the permanent internal address
            String finalAddress = page.getBaseUrl();
            if (finalAddress == null) {
                finalAddress = page.getUrl();
            }

            beforeVisit(priority, page, driver, mutableConfig);
            visit(finalAddress, driver);
            afterVisit(status, page, driver);

            // TODO: handle with frames
            // driver.switchTo().frame(1);
        } catch (org.openqa.selenium.TimeoutException e) {
            LOG.warn(e.toString());
            pageSource = driver.getPageSource();
            handleWebDriverTimeout(url, pageSource, driver);
            // TODO: the reason may be one of page load timeout, script timeout and implicit wait timeout
            status = ProtocolStatusCodes.WEB_DRIVER_TIMEOUT;
        } catch (org.openqa.selenium.WebDriverException e) {
            status = ProtocolStatusCodes.EXCEPTION;
            LOG.warn(e.toString());
        } finally {
            try {
                if (pageSource.isEmpty()) {
                    pageSource = driver.getPageSource();
                }
                pageSource = handleFinalPageSource(pageSource, page, driver, headers);
            } finally {
                drivers.put(priority, driver);
            }
        }

        // TODO: handle redirect
        // TODO: collect response header
        // TODO: fetch only the major pages, css, js, etc, ignore the rest resources, ignore external resources
        // TODO: ignore timeout and get the page source

        return new ForwardingResponse(url, pageSource, status, headers);
    }

    private Response getResponse(String url, Future<Response> future, Duration timeout) {
        Objects.requireNonNull(future);

        int httpCode;
        MultiMetadata headers;

        try {
            // Waits if necessary for at most the given time for the computation
            // to complete, and then retrieves its result, if available.
            return future.get(timeout.getSeconds(), TimeUnit.SECONDS);
        } catch (CancellationException e) {
            // if the computation was cancelled
            httpCode = ProtocolStatusCodes.CANCELED;
            headers = new MultiMetadata();
            headers.put("EXCEPTION", e.toString());
        } catch (TimeoutException e) {
            httpCode = ProtocolStatusCodes.THREAD_TIMEOUT;
            headers = new MultiMetadata();
            headers.put("EXCEPTION", e.toString());

            LOG.warn("Fetch resource timeout, " + e.toString());
        } catch (InterruptedException e) {
            httpCode = ProtocolStatusCodes.EXCEPTION;
            headers = new MultiMetadata();
            headers.put("EXCEPTION", e.toString());

            LOG.warn("Interrupted when fetch resource " + e.toString());
        } catch (ExecutionException e) {
            httpCode = ProtocolStatusCodes.EXCEPTION;
            headers = new MultiMetadata();
            headers.put("EXCEPTION", e.toString());

            LOG.warn("Unexpected exception " + StringUtil.stringifyException(e));
        } catch (Exception e) {
            httpCode = ProtocolStatusCodes.EXCEPTION;
            headers = new MultiMetadata();
            headers.put("EXCEPTION", e.toString());

            LOG.warn("{} url: {}", e, url);
        } finally {
            if (future.isCancelled()) {
                httpCode = ProtocolStatusCodes.CANCELED;
            } else if (future.isDone()) {
                httpCode = ProtocolStatusCodes.SUCCESS_OK;
            }
        }

        return new ForwardingResponse(url, httpCode, headers);
    }

    private void visit(String url, WebDriver driver) {
        driver.manage().window().maximize();
        driver.get(url);

        // As a JavascriptExecutor
        if (JavascriptExecutor.class.isAssignableFrom(driver.getClass())) {
            executeJs(driver);
        }
    }

    private void executeJs(WebDriver driver) {
        if (!JavascriptExecutor.class.isAssignableFrom(driver.getClass())) {
            return;
        }

        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

        // is the document ready?

        if (driver instanceof HtmlUnitDriver) {
            // Wait for page load complete, htmlunit need to wait and then extract injected javascript
            FluentWait<WebDriver> fWait = new FluentWait<>(driver)
                    .withTimeout(30, TimeUnit.SECONDS)
                    .pollingEvery(1, TimeUnit.SECONDS)
                    .ignoring(NoSuchElementException.class, TimeoutException.class)
                    .ignoring(StaleElementReferenceException.class);

//                        fWait.until(ExpectedConditions.visibilityOf(driver.findElement(By.tagName("body"))));
//                        fWait.until(ExpectedConditions.elementToBeClickable(By.tagName("body")));

            for (int i = 0; i < scrollDownCount; ++i) {
                // fWait.until(ExpectedConditions.javaScriptThrowsNoExceptions("window.scrollTo(0, document.body.scrollHeight);"));
            }
        } else {
            // Waiting 30 seconds for an element to be present on the page, checking

            // for its presence once every 5 seconds.

            FluentWait<WebDriver> wait = new FluentWait<>(driver)
                    .withTimeout(30, TimeUnit.SECONDS)
                    .pollingEvery(500, TimeUnit.MILLISECONDS)
                    .ignoring(NoSuchElementException.class, TimeoutException.class)
                    .ignoring(StaleElementReferenceException.class);

            WebElement body = wait.until(dr -> dr.findElement(By.tagName("body")));

            // Scroll down to bottom times to ensure ajax content can be loaded
            // TODO: no way to determine if there is ajax response while it scrolls, i remember phantomjs has a way
            for (int i = 0; i < scrollDownCount; ++i) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Scrolling down #" + (i + 1));
                }

                // TODO: some websites do not allow scroll to below the bottom
                String scrollJs = ";window.scrollBy(0, 500);";
                jsExecutor.executeScript(scrollJs);

                // TODO: is there better way to do this?
                long millis = scrollDownWait.toMillis();
                if (millis > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(millis);
                    } catch (InterruptedException e) {
                        LOG.warn("Sleep interruption. " + e);
                    }
                }
            }

            // now we must stop everything
            if (scrollDownCount > 0) {
                jsExecutor.executeScript(";return window.stop();");
            }
        }

        if (StringUtils.isNotBlank(clientJs)) {
            jsExecutor.executeScript(clientJs);
        }

        jsExecutor.executeScript(";return window.stop();");
    }

    private String handleFinalPageSource(String pageSource, WebPage page, WebDriver driver, MultiMetadata headers) {
        // The page content's encoding is already converted to UTF-8 by Web driver
        headers.put(CONTENT_ENCODING, "UTF-8");
        headers.put(CONTENT_LENGTH, String.valueOf(pageSource.length()));

        // Some parsers use html directive to decide the content's encoding, correct it to be UTF-8
        // TODO: Do it only for html content
        // TODO: Replace only corresponding html meta directive, not all occurrence
        pageSource = HTML_CHARSET_PATTERN.matcher(pageSource).replaceFirst("UTF-8");

        headers.put(Q_TRUSTED_CONTENT_ENCODING, "UTF-8");
        headers.put(Q_RESPONSE_TIME, OffsetDateTime.now(page.getZoneId()).toString());
        headers.put(Q_WEB_DRIVER, driver.getClass().getName());
        // headers.put(CONTENT_TYPE, "");

        if (LOG.isDebugEnabled()) {
            export(page, pageSource.getBytes(), ".htm");
        }

        return pageSource;
    }

    private void handleWebDriverTimeout(String url, String pageSource, WebDriver driver) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Selenium timeout. Timeouts: {}/{}/{}, drivers: {}/{}, url: {}",
                    defaultPageLoadTimeout, scriptTimeout, scrollDownWait,
                    drivers.getFreeSize(), drivers.getTotalSize(),
                    url
            );
        } else {
            LOG.warn("Selenium timeout, url: " + url);
        }

        if (!pageSource.isEmpty()) {
            LOG.info("Selenium timeout but the page source is OK, length: " + pageSource.length());
        }
    }

    private Duration getPageLoadTimeout(MutableConfig mutableConfig) {
        int priority = mutableConfig.getUint(SELENIUM_WEB_DRIVER_PRIORITY, 0);
        return getPageLoadTimeout(priority, mutableConfig);
    }

    private Duration getPageLoadTimeout(int priority, MutableConfig mutableConfig) {
        Duration pageLoadTimeout;
        if (priority > 0) {
            pageLoadTimeout = Duration.ofSeconds(priority * 30);
        } else {
            pageLoadTimeout = mutableConfig.getDuration(FETCH_PAGE_LOAD_TIMEOUT, defaultPageLoadTimeout);
        }

        return pageLoadTimeout;
    }

    private void beforeVisit(int priority, WebPage page, WebDriver driver, MutableConfig mutableConfig) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Fetching task: {}/{}, thread: #{}, drivers: {}/{}, timeouts: {}/{}/{}, {}",
                    batchTaskCount.get(), totalTaskCount.get(),
                    Thread.currentThread().getId(),
                    drivers.getFreeSize(), drivers.getTotalSize(),
                    getPageLoadTimeout(priority, mutableConfig), scriptTimeout, scrollDownWait,
                    page.getConfiguredUrl()
            );
        }

        if (mutableConfig == null) {
            return;
        }

        WebDriver.Timeouts timeouts = driver.manage().timeouts();

        // Page load timeout
        Duration pageLoadTimeout = getPageLoadTimeout(priority, mutableConfig);
        timeouts.pageLoadTimeout(pageLoadTimeout.getSeconds(), TimeUnit.SECONDS);

        // Script timeout
        scriptTimeout = mutableConfig.getDuration(FETCH_SCRIPT_TIMEOUT, scriptTimeout);
        timeouts.setScriptTimeout(scriptTimeout.getSeconds(), TimeUnit.SECONDS);

        // Scrolling
        scrollDownCount = mutableConfig.getInt(FETCH_SCROLL_DOWN_COUNT, scrollDownCount);
        if (scrollDownCount > 20) {
            scrollDownCount = 20;
        }
        scrollDownWait = mutableConfig.getDuration(FETCH_SCROLL_DOWN_WAIT, scrollDownWait);
        if (scrollDownWait.compareTo(pageLoadTimeout) > 0) {
            scrollDownWait = pageLoadTimeout;
        }
    }

    private void afterVisit(int status, WebPage page, WebDriver driver) {
        if (status == ProtocolStatusCodes.SUCCESS_OK) {
            batchSuccessCount.incrementAndGet();
            totalSuccessCount.incrementAndGet();

            // TODO: A metrics system is required
            if (LOG.isDebugEnabled()) {
                LOG.debug("Selenium batch task success: {}/{}, total task success: {}/{}",
                        batchSuccessCount, batchTaskCount,
                        totalSuccessCount, totalTaskCount
                );
            }
        }

        if (driver instanceof ChromeDriver) {
            page.setLastBrowser(BrowserType.CHROME);
        } else if (driver instanceof HtmlUnitDriver) {
            page.setLastBrowser(BrowserType.HTMLUNIT);
        } else {
            LOG.warn("Actual browser is set to be NATIVE by selenium engine");
            page.setLastBrowser(BrowserType.NATIVE);
        }

        // As a RemoteWebDriver
        if (RemoteWebDriver.class.isAssignableFrom(driver.getClass())) {
            RemoteWebDriver remoteWebDriver = (RemoteWebDriver) driver;

            if (LOG.isDebugEnabled()) {
                try {
                    byte[] bytes = remoteWebDriver.getScreenshotAs(OutputType.BYTES);
                    export(page, bytes, ".png");
                } catch (Exception e) {
                    LOG.warn("Failed to take screenshot for " + page.getUrl());
                }
            }
        }
    }

    private void export(WebPage page, byte[] content, String suffix) {
        PulsarPaths paths = PulsarPaths.INSTANCE;
        String browser = page.getLastBrowser().name().toLowerCase();
        Path path = paths.get(paths.getWebCacheDir().toString(), browser, paths.fromUri(page.getUrl(), suffix));
        PulsarFiles.INSTANCE.saveTo(content, path, true);
    }

    @Override
    public void close() {
        if (closed.getAndSet(true)) {
            return;
        }

        executor.close();
        drivers.close();
    }
}
