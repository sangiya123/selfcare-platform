package com.selfcare.conformance.utils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the WireMock server lifecycle for stubbing downstream providers.
 * WireMock is shared across the entire test suite to avoid startup overhead.
 */
public final class WireMockManager {

    private static final Logger LOG = LoggerFactory.getLogger(WireMockManager.class);
    private static WireMockManager instance;
    private WireMockServer server;
    private boolean started;

    private WireMockManager() {
    }

    public static synchronized WireMockManager getInstance() {
        if (instance == null) {
            instance = new WireMockManager();
        }
        return instance;
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        LOG.info("Starting WireMock server on port {} with mappings from classpath:wiremock/mappings",
                TestConfig.WIREMOCK_PORT);
        server = new WireMockServer(WireMockConfiguration.options()
                .port(TestConfig.WIREMOCK_PORT)
                .usingFilesUnderClasspath("wiremock")
                .extensions(new ResponseTemplateTransformer(true))
                .bindAddress("127.0.0.1")
                .asynchronousResponseEnabled(true)
                .asynchronousResponseThreads(20));
        server.start();
        started = true;
        LOG.info("WireMock server started at {}", TestConfig.WIREMOCK_URL);
    }

    public synchronized void stop() {
        if (server != null && started) {
            LOG.info("Stopping WireMock server");
            server.stop();
            started = false;
        }
    }

    public synchronized void reset() {
        if (server != null) {
            server.resetAll();
        }
    }

    public WireMockServer server() {
        if (!started) {
            throw new IllegalStateException("WireMock not started; call start() first");
        }
        return server;
    }
}
