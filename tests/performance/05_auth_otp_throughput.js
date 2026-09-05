// k6 Performance Test: Auth/OTP throughput
// NFR-PERF: Auth endpoints under load
// NFR-SEC: OTP throttling, replay prevention
//
// Usage:
//   k6 run --vus 200 --duration 2m tests/performance/05_auth_otp_throughput.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const API_GATEWAY = __ENV.API_GATEWAY || 'http://localhost:8080';

const otpSendCount = new Counter('otp_send_count');
const otpSuccessRate = new Rate('otp_success_rate');
const rateLimitedCount = new Counter('rate_limited_count');

export const options = {
  scenarios: {
    otp_throughput: {
      executor: 'constant-vus',
      vus: 200,
      duration: '2m',
      tags: { scenario: 'otp-throughput' },
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<200'],
    'otp_success_rate': ['rate>0.90'],
  },
};

export default function () {
  const msisdn = `+9477${String(Math.floor(Math.random() * 10000000)).padStart(7, '0')}`;

  // OTP request
  const otpRes = http.post(
    `${API_GATEWAY}/api/v1/auth/otp/request`,
    JSON.stringify({ msisdn, channel: 'SMS' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  otpSendCount.add(1);
  if (otpRes.status === 200) {
    otpSuccessRate.add(1);
  } else if (otpRes.status === 429) {
    rateLimitedCount.add(1);
    otpSuccessRate.add(0);
  } else {
    otpSuccessRate.add(0);
  }

  check(otpRes, {
    'OTP request OK or rate-limited': (r) => r.status === 200 || r.status === 429,
  });

  sleep(0.5);
}
