package com.selfcare.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Support Service — customer support / service requests.
 *
 * <p>Exposes the canonical {@link com.selfcare.platform.common.adapter.SupportProvider}
 * contract to the mobile app and BFFs: list/create service requests, get a single
 * request, message a request, and change its status. Each tenant's request is served
 * by that tenant's operator-support provider via the adapter registry.</p>
 */
@SpringBootApplication(scanBasePackages = {
    "com.selfcare"
})
public class SupportServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SupportServiceApplication.class, args);
    }
}