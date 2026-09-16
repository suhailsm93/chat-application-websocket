import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const syncQueryLatency = new Trend('chat_sync_query_latency_ms');

export const options = {
  vus: 100,
  duration: '1m',
  thresholds: {
    'http_req_duration': ['p(95)<100'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // Pass time-sortable cursor ID to test index lookup speed
  const sampleAfterId = '018c1a2f-7c30-7123-81a1-123456789abc';
  
  const params = {
    headers: {
      'Authorization': `Bearer ${__ENV.TEST_JWT_TOKEN || 'SAMPLE_TOKEN'}`,
    },
  };

  const startTime = Date.now();
  const res = http.get(`${BASE_URL}/api/messages/sync?afterMessageId=${sampleAfterId}&limit=50`, params);
  
  syncQueryLatency.add(Date.now() - startTime);

  check(res, {
    'Sync Status 200': (r) => r.status === 200,
    'Sync Payload Has Items': (r) => r.json('missedMessages') !== undefined,
  });

  sleep(0.5);
}