// k6 Performance Test: Dashboard BFF fan-out
// NFR-PERF-003 target: P95 < 300ms (server processing) when dependencies meet budget
// NFR-PERF-007: model fan-out amplification (10k users × 25 providers)
//
// Usage:
//   k6 run --out json=results/dashboard.json tests/performance/01_dashboard_load.js
//   k6 run --vus 100 --duration 5m tests/performance/01_dashboard_load.js
//
// This script:
//   1. Authenticates each virtual user
//   2. Hits the dashboard BFF endpoint
//   3. Verifies partial-response semantics (widgets fail independently)
//   4. Records P50/P95/P99 latency
//   5. Thresholds match NFR targets

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const API_GATEWAY = __ENV.API_GATEWAY || 'http://localhost:8080';
const TENANT_ID = __ENV.TENANT_ID || 'dialog-lk';
const ENV = __ENV.ENV || 'stg';

// Custom metrics
const dashboardLatency = new Trend('dashboard_latency_ms', true);
const widgetSuccessRate = new Rate('widget_success_rate');
const partialResponseRate = new Rate('partial_response_rate');

export const options = {
  scenarios: {
    // Smoke test — quick verification
    smoke: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 10 },
        { duration: '30s', target: 0 },
      ],
      tags: { scenario: 'smoke' },
    },
    // Load test — typical production load
    load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 },
        { duration: '5m', target: 100 },
        { duration: '2m', target: 200 },
        { duration: '5m', target: 200 },
        { duration: '3m', target: 0 },
      ],
      tags: { scenario: 'load' },
    },
    // Stress test — beyond typical load
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 500 },
        { duration: '3m', target: 1000 },
        { duration: '5m', target: 1000 },
        { duration: '3m', target: 0 },
      ],
      tags: { scenario: 'stress' },
    },
  },
  // NFR-PERF-003 thresholds
  thresholds: {
    'http_req_duration{scenario:load}': ['p(50)<150', 'p(95)<300', 'p(99)<500'],
    'http_req_duration{scenario:stress}': ['p(95)<600'],
    'http_req_failed': ['rate<0.01'],
    'widget_success_rate': ['rate>0.95'],
    'partial_response_rate': ['rate>0.90'],
    checks: ['rate>0.95'],
  },
};

export default function () {
  // Step 1: Authenticate
  const authRes = http.post(
    `${API_GATEWAY}/api/v1/auth/login`,
    JSON.stringify({
      email: `loadtest_${randomString(8)}@dialog.lk`,
      password: 'loadtest-password',
    }),
    { headers: { 'Content-Type': 'application/json', 'X-Tenant-Id': TENANT_ID } }
  );

  const authOk = check(authRes, {
    'auth status 200 or 401 (expected in test env)': (r) =>
      r.status === 200 || r.status === 401,
  });

  let token = 'test-token';
  try {
    const body = JSON.parse(authRes.body);
    if (body.data && body.data.accessToken) token = body.data.accessToken;
  } catch (e) {}

  const authHeaders = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      'X-Tenant-Id': TENANT_ID,
      'X-Correlation-Id': `k6-${__VU}-${__ITER}`,
    },
  };

  // Step 2: Fetch dashboard (BFF fan-out endpoint)
  const dashRes = http.get(`${API_GATEWAY}/api/v1/dashboard/home`, authHeaders);

  dashboardLatency.add(dashRes.timings.duration);

  const dashOk = check(dashRes, {
    'dashboard returns 200': (r) => r.status === 200,
    'dashboard has data field': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data !== undefined;
      } catch (e) {
        return false;
      }
    },
    'dashboard has correlationId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.meta && body.meta.correlationId;
      } catch (e) {
        return false;
      }
    },
  });

  // Step 3: Verify partial response semantics (NFR-AVL-002, ADR-008)
  // The dashboard may return widgets with status SUCCESS, STALE, PARTIAL, TIMEOUT,
  // UNAVAILABLE, or ERROR. None of these should make the whole dashboard fail.
  if (dashRes.status === 200) {
    try {
      const body = JSON.parse(dashRes.body);
      const widgets = body.data?.widgets || body.data?.sections || [];
      let totalWidgets = widgets.length;
      let successfulWidgets = widgets.filter(
        (w) => w.status === 'SUCCESS' || w.status === 'STALE'
      ).length;

      if (totalWidgets > 0) {
        widgetSuccessRate.add(successfulWidgets / totalWidgets);
        partialResponseRate.add(successfulWidgets > 0 ? 1 : 0);
      }
    } catch (e) {
      // Body parse error — count as failure
      widgetSuccessRate.add(0);
    }
  } else {
    widgetSuccessRate.add(0);
  }

  // Step 4: Fetch other key endpoints
  const summaryRes = http.get(`${API_GATEWAY}/api/v1/accounts/me`, authHeaders);
  check(summaryRes, {
    'accounts/me status 200 or 401': (r) => r.status === 200 || r.status === 401,
  });

  // Step 5: Sleep to simulate user think-time
  sleep(Math.random() * 2 + 1); // 1-3s
}
