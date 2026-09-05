// k6 Performance Test: Config Service Compile + Publish
// NFR-PERF-001: config/routing decisions should be served from in-memory compiled manifest
// NFR-PERF-006: <100ms P95 for cached/local capability responses
//
// Usage:
//   k6 run --vus 20 --duration 3m tests/performance/03_config_compile_publish.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const API_GATEWAY = __ENV.API_GATEWAY || 'http://localhost:8080';
const TENANT_ID = __ENV.TENANT_ID || 'dialog-lk';

const configFetchLatency = new Trend('config_fetch_latency_ms', true);
const themeFetchLatency = new Trend('theme_fetch_latency_ms', true);

export const options = {
  scenarios: {
    // Simulate 1000 mobile clients fetching config every 30s
    config_pull: {
      executor: 'constant-vus',
      vus: 1000,
      duration: '3m',
      tags: { scenario: 'config-pull' },
    },
  },
  thresholds: {
    // NFR-PERF-004: cached responses <100ms P95
    'config_fetch_latency_ms': ['p(50)<20', 'p(95)<100', 'p(99)<200'],
    'theme_fetch_latency_ms': ['p(50)<20', 'p(95)<100', 'p(99)<200'],
    'http_req_failed': ['rate<0.001'],
  },
};

export default function () {
  const headers = {
    headers: {
      'Authorization': 'Bearer test-token',
      'X-Tenant-Id': TENANT_ID,
    },
  };

  // Fetch compiled manifest
  const manifestRes = http.get(
    `${API_GATEWAY}/api/v1/config/manifest?tenant=${TENANT_ID}&env=prod&experience=home`,
    headers
  );
  configFetchLatency.add(manifestRes.timings.duration);

  check(manifestRes, {
    'manifest status 200': (r) => r.status === 200,
    'manifest has configVersion': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.configVersion !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  // Fetch theme
  const themeRes = http.get(
    `${API_GATEWAY}/api/v1/themes/current?tenant=${TENANT_ID}&env=prod`,
    headers
  );
  themeFetchLatency.add(themeRes.timings.duration);

  check(themeRes, {
    'theme status 200': (r) => r.status === 200,
  });

  sleep(2);
}
