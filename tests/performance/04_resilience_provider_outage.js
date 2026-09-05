// k6 Resilience Test: Provider outage simulation
// NFR-AVL-002: No single downstream API failure may make the whole dashboard unavailable
// NFR-AVL-003: Every external provider call must define timeout, retry, circuit breaker
//
// Usage:
//   This test requires the test environment to have provider fault injection enabled.
//   Start the test with the orchestrator set to fail the "balance-provider" downstream.
//   k6 run --vus 100 --duration 2m tests/performance/04_resilience_provider_outage.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const API_GATEWAY = __ENV.API_GATEWAY || 'http://localhost:8080';
const TENANT_ID = __ENV.TENANT_ID || 'dialog-lk';

const dashboardSuccessRate = new Rate('dashboard_success_rate');
const partialResponseRate = new Rate('partial_response_rate');
const degradedLatency = new Rate('degraded_latency_under_threshold');

export const options = {
  scenarios: {
    resilience_test: {
      executor: 'constant-vus',
      vus: 100,
      duration: '2m',
      tags: { scenario: 'resilience' },
    },
  },
  thresholds: {
    // Dashboard must return SOMETHING even with provider outage
    'dashboard_success_rate': ['rate>0.99'],  // 99% dashboard responses
    'partial_response_rate': ['rate>0.95'],   // 95% partial responses (some widgets may fail)
  },
};

export default function () {
  const res = http.get(
    `${API_GATEWAY}/api/v1/dashboard/home`,
    {
      headers: {
        'Authorization': 'Bearer test-token',
        'X-Tenant-Id': TENANT_ID,
      },
    }
  );

  const is2xx = res.status >= 200 && res.status < 300;
  dashboardSuccessRate.add(is2xx ? 1 : 0);

  if (is2xx) {
    try {
      const body = JSON.parse(res.body);
      const widgets = body.data?.widgets || body.data?.sections || [];
      const total = widgets.length;
      const successful = widgets.filter(
        (w) => w.status === 'SUCCESS' || w.status === 'STALE'
      ).length;

      if (total > 0) {
        // Partial response = at least 1 widget succeeded
        partialResponseRate.add(successful > 0 ? 1 : 0);
      } else {
        partialResponseRate.add(0);
      }

      // Degraded latency = total response < 1000ms (within circuit breaker timeout)
      degradedLatency.add(res.timings.duration < 1000 ? 1 : 0);
    } catch (e) {
      partialResponseRate.add(0);
    }
  } else {
    partialResponseRate.add(0);
  }

  sleep(1);
}
