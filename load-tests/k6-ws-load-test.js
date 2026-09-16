import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const messageAckLatency = new Trend('chat_message_ack_latency_ms');
const messageSendSuccess = new Rate('chat_message_send_success_rate');
const activeConnections = new Counter('chat_active_ws_connections');

export const options = {
  stages: [
    { duration: '5s', target: 5 },
    { duration: '15s', target: 10 },
    { duration: '5s', target: 0 },
  ],
  thresholds: {
    'chat_message_ack_latency_ms': ['p(95)<300'],
    'chat_message_send_success_rate': ['rate>0.90'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws';

export function setup() {
  console.log('--- Starting Load Test Setup ---');

  const targetUsername = `bot_${Date.now()}`;
  const targetRes = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
    username: targetUsername,
    email: `${targetUsername}@loadtest.local`,
    password: 'Password123!',
    displayName: 'Target Bot',
    deviceName: 'Server-Bot',
    deviceType: 'SERVER'
  }), { headers: { 'Content-Type': 'application/json' } });

  if (targetRes.status !== 200) {
    console.error('Failed to register bot:', targetRes.status, targetRes.body);
    return { userPool: [] };
  }

  const targetUserId = targetRes.json('userId');
  const userPool = [];

  for (let i = 1; i <= 10; i++) {
    const username = `k6_user_${i}_${Date.now()}`;
    const regRes = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
      username: username,
      email: `${username}@loadtest.local`,
      password: 'Password123!',
      displayName: `Load VU ${i}`,
      deviceName: `k6-device-${i}`,
      deviceType: 'BENCHMARK'
    }), { headers: { 'Content-Type': 'application/json' } });

    if (regRes.status === 200) {
      const token = regRes.json('accessToken');

      const convRes = http.post(`${BASE_URL}/api/conversations/direct`, JSON.stringify({
        targetUserId: targetUserId
      }), {
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        }
      });

      if (convRes.status === 200) {
        userPool.push({
          token: token,
          conversationId: convRes.json('id')
        });
      }
    }
  }

  console.log(`--- Provisioned ${userPool.length} users successfully ---`);
  return { userPool: userPool };
}

export default function (data) {
  if (!data.userPool || data.userPool.length === 0) {
    sleep(1);
    return;
  }

  const userIndex = (__VU - 1) % data.userPool.length;
  const user = data.userPool[userIndex];

  if (!user || !user.token) {
    sleep(1);
    return;
  }

  const wsEndpoint = `${WS_URL}?token=${user.token}`;

  const res = ws.connect(wsEndpoint, {}, function (socket) {
    activeConnections.add(1);

    socket.on('open', () => {
      const startTime = Date.now();
      const requestId = `req-${startTime}`;

      const testMessage = JSON.stringify({
        type: 'MESSAGE_SEND',
        requestId: requestId,
        conversationId: user.conversationId,
        payloadJson: JSON.stringify({
          clientMessageId: `c-${startTime}`,
          messageType: 'TEXT',
          content: 'Benchmark real-time message payload',
          replyToMessageId: null
        })
      });

      socket.send(testMessage);
    });

    socket.on('message', (data) => {
      try {
        const frame = JSON.parse(data);
        if (frame.type === 'MESSAGE_ACK') {
          const parts = frame.requestId.split('-');
          if (parts.length > 1) {
            const sentTime = parseInt(parts[1]);
            if (!isNaN(sentTime)) {
              messageAckLatency.add(Date.now() - sentTime);
            }
          }
          messageSendSuccess.add(true);
        }
      } catch (e) {
        messageSendSuccess.add(false);
      }
    });

    socket.on('close', () => {
      activeConnections.add(-1);
    });

    socket.setTimeout(() => {
      socket.close();
    }, 3000);
  });

  check(res, { 'WebSocket Handshake 101': (r) => r && r.status === 101 });
  sleep(1);
}