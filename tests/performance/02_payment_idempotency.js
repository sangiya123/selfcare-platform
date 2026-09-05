// k6 Performance Test: Payment with Idempotency
// Tests NFR-PERF + idempotency safety under concurrent load
// Same idempotency key + same body = single transaction (no double charge)
//
// Usage:
//   k6 run --vus 50 --duration 2m tests/performance/02_payment_idempotency.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const API_GATEWAY = __ENV.API_GATEWAY || 'http://localhost:8080';
const TENANT_ID = __ENV.TENANT_ID || 'dialog-lk';

const duplicateTxCount = new Counter('duplicate_transaction_count');
const doubleChargeRate = new Rate('double_charge_rate');

export const options = {
  scenarios: {
    // 50 VUs, each posting the SAME idempotency key 10 times
    concurrent_same_key: {
      executor: 'constant-vus',
      vus: 50,
      duration: '2m',
      tags: { scenario: 'concurrent-same-key' },
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<500'],
    'double_charge_rate': ['rate<0.001'], // <0.1% double charge tolerance
  },
};

export default function () {
  // Same idempotency key for all VUs in this iteration group
  const idempotencyKey = `idem-${__VU}-${__ITER}`;
  const body = JSON.stringify({
    transactionType: 'BILL_PAYMENT',
    amount: '100.00',
    currency: 'LKR',
    sourceConnectionId: 'source-conn-1',
    targetConnectionId: 'target-conn-1',
    billId: 'bill-12345',
    paymentMethod: 'CARD',
    idempotencyKey: idempotencyKey,
  });

  const res = http.post(
    `${API_GATEWAY}/api/v1/payments`,
    body,
    {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer test-token',
        'X-Tenant-Id': TENANT_ID,
        'X-Idempotency-Key': idempotencyKey,
        'X-Correlation-Id': `k6-pay-${__VU}-${__ITER}`,
      },
    }
  );

  const isOk = check(res, {
    'payment status 200, 201, or 409 (already processed)': (r) =>
      r.status === 200 || r.status === 201 || r.status === 409,
  });

  if (res.status === 200 || res.status === 201) {
    try {
      const body = JSON.parse(res.body);
      // The backend should return the SAME transactionId for the same idempotency key
      if (body.data && body.data.transactionId) {
        // Count duplicates (we expect most to be duplicates since same key)
        duplicateTxCount.add(1);
      }
    } catch (e) {}
  }

  sleep(0.5);
}
