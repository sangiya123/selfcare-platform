package com.selfcare.platform.common.context;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Propagates the current {@link RequestContext} (thread-local) and MDC map onto executor worker
 * threads. Wire it to any ThreadPoolTaskExecutor via {@code setTaskDecorator(...)}; it is exposed
 * as a Spring bean named {@code requestContextTaskDecorator}.
 */
public class RequestContextTaskDecorator implements TaskDecorator {

    public static final String BEAN_NAME = "requestContextTaskDecorator";

    @Override
    public Runnable decorate(Runnable task) {
        RequestContext ctx = RequestContextHolder.get();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (mdc != null) MDC.setContextMap(mdc);
                RequestContextHolder.set(ctx);
                task.run();
            } finally {
                MDC.clear();
                RequestContextHolder.clear();
            }
        };
    }
}