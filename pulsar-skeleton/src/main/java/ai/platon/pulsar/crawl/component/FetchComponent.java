/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.platon.pulsar.crawl.component;

import ai.platon.pulsar.common.URLUtil;
import ai.platon.pulsar.common.Urls;
import ai.platon.pulsar.common.config.ImmutableConfig;
import ai.platon.pulsar.common.config.MutableConfig;
import ai.platon.pulsar.common.options.LoadOptions;
import ai.platon.pulsar.persist.CrawlStatus;
import ai.platon.pulsar.persist.ProtocolHeaders;
import ai.platon.pulsar.persist.ProtocolStatus;
import ai.platon.pulsar.persist.WebPage;
import ai.platon.pulsar.crawl.fetch.TaskStatusTracker;
import ai.platon.pulsar.crawl.protocol.Content;
import ai.platon.pulsar.crawl.protocol.Protocol;
import ai.platon.pulsar.crawl.protocol.ProtocolFactory;
import ai.platon.pulsar.crawl.protocol.ProtocolOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.net.URL;
import java.time.Instant;
import java.util.Objects;

import static ai.platon.pulsar.common.config.PulsarConstants.SHORTEST_VALID_URL_LENGTH;
import static ai.platon.pulsar.persist.ProtocolStatus.ARG_REDIRECT_TO_URL;
import static ai.platon.pulsar.persist.metadata.Mark.FETCH;
import static ai.platon.pulsar.persist.metadata.Mark.GENERATE;

/**
 * Created by vincent on 17-5-1.
 * Copyright @ 2013-2017 Platon AI. All rights reserved
 * Fetch component
 */
@Component
public class FetchComponent implements AutoCloseable {

    public static final Logger LOG = LoggerFactory.getLogger(FetchComponent.class);
    protected final TaskStatusTracker taskStatusTracker;
    /**
     * The config, load from config files and unmodified
     */
    private final ImmutableConfig immutableConfig;
    /**
     * The protocol factory, map fetch tasks to the corresponding protocol
     */
    private final ProtocolFactory protocolFactory;

    public FetchComponent(ProtocolFactory protocolFactory,
                          TaskStatusTracker taskStatusTracker,
                          MutableConfig mutableConfig) {
        this.immutableConfig = mutableConfig;
        this.protocolFactory = protocolFactory;
        this.taskStatusTracker = taskStatusTracker;
    }

    public FetchComponent(ProtocolFactory protocolFactory,
                          TaskStatusTracker taskStatusTracker,
                          ImmutableConfig immutableConfig) {
        this.immutableConfig = immutableConfig;
        this.protocolFactory = protocolFactory;
        this.taskStatusTracker = taskStatusTracker;
    }

    public static void updateStatus(WebPage page, CrawlStatus crawlStatus, ProtocolStatus protocolStatus) {
        page.setCrawlStatus(crawlStatus);
        if (protocolStatus != null) {
            page.setProtocolStatus(protocolStatus);
        }
        page.increaseFetchCount();
    }

    public static void updateMarks(WebPage page) {
        page.getMarks().putIfNonNull(FETCH, page.getMarks().get(GENERATE));
    }

    public static void updateContent(WebPage page, Content content) {
        updateContent(page, content, null);
    }

    private static void updateContent(WebPage page, Content content, String contentType) {
        if (content == null) {
            return;
        }

        page.setLocation(content.getBaseUrl());
        page.setContent(content.getContent());

        if (contentType != null) {
            content.setContentType(contentType);
        } else {
            contentType = content.getContentType();
        }

        if (contentType != null) {
            page.setContentType(contentType);
        } else {
            LOG.error("Failed to determine content type!");
        }
    }

    public static void updateFetchTime(WebPage page) {
        updateFetchTime(page, Instant.now());
    }

    public static void updateFetchTime(WebPage page, Instant newFetchTime) {
        Instant lastFetchTime = page.getFetchTime();

        if (lastFetchTime.isBefore(newFetchTime)) {
            page.setPrevFetchTime(lastFetchTime);
        }
        page.setFetchTime(newFetchTime);

        page.putFetchTimeHistory(newFetchTime);
    }

    public ImmutableConfig getImmutableConfig() {
        return immutableConfig;
    }

    public TaskStatusTracker getTaskStatusTracker() {
        return taskStatusTracker;
    }

    /**
     * Fetch a url
     *
     * @param url The url to fetch
     * @return The fetch result
     */
    @Nonnull
    public WebPage fetch(String url) {
        Objects.requireNonNull(url);
        return fetchContent(WebPage.newWebPage(url, false));
    }

    /**
     * Fetch a url
     *
     * @param url     The url to fetch
     * @param options The options
     * @return The fetch result
     */
    @Nonnull
    public WebPage fetch(String url, LoadOptions options) {
        Objects.requireNonNull(url);
        Objects.requireNonNull(options);

        return fetchContent(createFetchEntry(url, options));
    }

    /**
     * Fetch a page
     *
     * @param page The page to fetch
     * @return The fetch result
     */
    @Nonnull
    public WebPage fetchContent(WebPage page) {
        return fetchContentInternal(page);
    }

    /**
     * Fetch a page
     *
     * @param page The page to fetch
     *             If response is null, block and wait for a response
     * @return The fetch result
     */
    @Nonnull
    protected WebPage fetchContentInternal(WebPage page) {
        Objects.requireNonNull(page);

        String url = page.getUrl();

        URL u = Urls.getURLOrNull(url);
        if (u == null) {
            return WebPage.NIL;
        }

        Protocol protocol = protocolFactory.getProtocol(page);
        if (protocol == null) {
            LOG.warn("No protocol found for {}", url);
            updateStatus(page, CrawlStatus.STATUS_UNFETCHED, ProtocolStatus.STATUS_PROTO_NOT_FOUND);
            return page;
        }

        ProtocolOutput output = protocol.getProtocolOutput(page);
        return processProtocolOutput(page, output);
    }

    protected boolean shouldFetch(String url) {
        int code = 0;
        if (taskStatusTracker.isFailed(url)) {
            code = 2;
        } else if (taskStatusTracker.isTimeout(url)) {
            code = 3;
        }

        if (code > 0 && LOG.isDebugEnabled()) {
            LOG.debug("Not fetching page, reason #{}, url: {}", code, url);
        }

        return code == 0;
    }

    protected WebPage processProtocolOutput(WebPage page, ProtocolOutput output) {
        Content content = output.getContent();
        if (content == null) {
            LOG.warn("No content for " + page.getConfiguredUrl());
            return page;
        }

        ProtocolHeaders headers = page.getHeaders();
        output.getHeaders().asMultimap().entries().forEach(e -> headers.put(e.getKey(), e.getValue()));

        ProtocolStatus protocolStatus = output.getStatus();
        int minorCode = protocolStatus.getMinorCode();

        if (protocolStatus.isSuccess()) {
            if (ProtocolStatus.NOTMODIFIED == minorCode) {
                updatePage(page, null, protocolStatus, CrawlStatus.STATUS_NOTMODIFIED);
            } else {
                updatePage(page, content, protocolStatus, CrawlStatus.STATUS_FETCHED);
            }

            return page;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Fetch failed, status: {}, url: {}", protocolStatus, page.getConfiguredUrl());
        }

        switch (minorCode) {
            case ProtocolStatus.WOULDBLOCK:
                taskStatusTracker.trackFailed(page.getUrl());
                break;

            case ProtocolStatus.MOVED:         // redirect
            case ProtocolStatus.TEMP_MOVED:
                CrawlStatus crawlStatus;
                boolean temp;
                if (minorCode == ProtocolStatus.MOVED) {
                    crawlStatus = CrawlStatus.STATUS_REDIR_PERM;
                    temp = false;
                } else {
                    crawlStatus = CrawlStatus.STATUS_REDIR_TEMP;
                    temp = true;
                }

                final String newUrl = protocolStatus.getArgOrDefault(ARG_REDIRECT_TO_URL, "");
                if (!newUrl.isEmpty()) {
                    String reprUrl = URLUtil.chooseRepr(page.getUrl(), newUrl, temp);
                    if (reprUrl.length() >= SHORTEST_VALID_URL_LENGTH) {
                        page.setReprUrl(reprUrl);
                    }
                }
                updatePage(page, content, protocolStatus, crawlStatus);
                break;

            case ProtocolStatus.THREAD_TIMEOUT:
            case ProtocolStatus.WEB_DRIVER_TIMEOUT:
            case ProtocolStatus.REQUEST_TIMEOUT:
            case ProtocolStatus.UNKNOWN_HOST:
                taskStatusTracker.trackTimeout(page.getUrl());
                updatePage(page, null, protocolStatus, CrawlStatus.STATUS_GONE);
                break;
            case ProtocolStatus.EXCEPTION:
                taskStatusTracker.trackFailed(page.getUrl());
                LOG.warn("Fetch failed, protocol status: {}", protocolStatus);
                /* FALL THROUGH **/
            case ProtocolStatus.RETRY:          // retry
            case ProtocolStatus.BLOCKED:
            case ProtocolStatus.CANCELED:       // canceled
                updatePage(page, null, protocolStatus, CrawlStatus.STATUS_RETRY);
                break;
            case ProtocolStatus.GONE:           // gone
            case ProtocolStatus.NOTFOUND:
            case ProtocolStatus.ACCESS_DENIED:
            case ProtocolStatus.ROBOTS_DENIED:
                taskStatusTracker.trackFailed(page.getUrl());
                updatePage(page, null, protocolStatus, CrawlStatus.STATUS_GONE);
                break;
            default:
                taskStatusTracker.trackFailed(page.getUrl());
                LOG.warn("Unknown ProtocolStatus: " + protocolStatus);
                updatePage(page, null, protocolStatus, CrawlStatus.STATUS_RETRY);
        }

        return page;
    }

    public WebPage createFetchEntry(String originalUrl, LoadOptions options) {
        Objects.requireNonNull(originalUrl);
        Objects.requireNonNull(options);

        WebPage page = WebPage.newWebPage(originalUrl, options.getShortenKey(), options.getVolatileConfig());
        page.setFetchMode(options.getFetchMode());
        page.setOptions(options.toString());

        return page;
    }

    public WebPage initFetchEntry(WebPage page, LoadOptions options) {
        Objects.requireNonNull(page);
        Objects.requireNonNull(options);

        page.setMutableConfig(options.getVolatileConfig());
        page.setFetchMode(options.getFetchMode());
        page.setOptions(options.toString());

        return page;
    }

    private void updatePage(WebPage page, Content content, ProtocolStatus protocolStatus, CrawlStatus crawlStatus) {
        updateStatus(page, crawlStatus, protocolStatus);
        updateContent(page, content);
        updateFetchTime(page);
        updateMarks(page);
    }

    @Override
    public void close() {
    }
}
