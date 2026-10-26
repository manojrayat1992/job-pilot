import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 run docker/k6-load-test.js --vus 5000 --duration 30s
// Direct 5,000 instant concurrent requests spike test (to reproduce the failure)
export const options = {
  scenarios: {
    instant_spike_crash_test: {
      executor: 'per-vu-iterations',
      vus: 5000,              // 5,000 simultaneous users hitting at once
      iterations: 1,          // 1 login request each
      maxDuration: '30s',
    },
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

export default function () {
  // Random user between 1 and 1,000,000
  const randomUserId = Math.floor(Math.random() * 1000000) + 1;
  const payload = JSON.stringify({
    email: `user${randomUserId}@test.com`,
    password: 'Password123!',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(`${BASE_URL}/api/auth/login`, payload, params);

  check(res, {
    'login status is 200': (r) => r.status === 200,
    'has accessToken': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.accessToken !== undefined;
      } catch (e) {
        return false;
      }
    },
  });
}
