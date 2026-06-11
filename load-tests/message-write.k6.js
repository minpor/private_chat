import http from 'k6/http';
import { check } from 'k6';

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const K6_PHASE = __ENV.K6_PHASE || 'all';
const WARMUP_RATE = Number(__ENV.WARMUP_TPS || '200');
const WARMUP_DURATION = __ENV.WARMUP_DURATION || '30s';
const TARGET_RATE = Number(__ENV.TARGET_TPS || '2000');
const DURATION = __ENV.DURATION || '2m';
const MAX_VUS = Number(__ENV.MAX_VUS || String(Math.max(TARGET_RATE * 2, 2000)));
const PREALLOCATED_VUS = Number(__ENV.PREALLOCATED_VUS || String(Math.min(TARGET_RATE, 5000)));

function buildScenarios() {
  const scenarios = {};
  if (K6_PHASE === 'all' || K6_PHASE === 'warmup') {
    scenarios.warmup = {
      executor: 'constant-arrival-rate',
      rate: WARMUP_RATE,
      timeUnit: '1s',
      duration: WARMUP_DURATION,
      preAllocatedVUs: Math.min(WARMUP_RATE, 500),
      maxVUs: Math.min(WARMUP_RATE * 2, 2000),
      exec: 'writeMessage',
      tags: { phase: 'warmup' }
    };
  }
  if (K6_PHASE === 'all' || K6_PHASE === 'measured') {
    scenarios.message_writes = {
      executor: 'constant-arrival-rate',
      rate: TARGET_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PREALLOCATED_VUS,
      maxVUs: MAX_VUS,
      exec: 'writeMessage',
      startTime: K6_PHASE === 'measured' ? '0s' : WARMUP_DURATION,
      tags: { phase: 'measured' }
    };
  }
  return scenarios;
}

function buildThresholds() {
  if (K6_PHASE === 'warmup') {
    return {};
  }
  return {
    'http_req_failed{phase:measured}': ['rate<0.01'],
    'http_req_duration{phase:measured}': ['p(95)<500']
  };
}

export const options = {
  scenarios: buildScenarios(),
  thresholds: buildThresholds()
};

function registerUser(username) {
  const password = 'loadtest-password-123';
  const registerRes = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({
      username,
      password,
      displayName: username
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (registerRes.status === 409) {
    const loginRes = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({ username, password }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    check(loginRes, { 'login ok': (r) => r.status === 200 });
    return loginRes.json('accessToken');
  }

  check(registerRes, { 'register ok': (r) => r.status === 201 || r.status === 200 });
  return registerRes.json('accessToken');
}

export function setup() {
  const existingToken = __ENV.LOAD_TEST_TOKEN;
  const existingChatId = __ENV.LOAD_TEST_CHAT_ID;
  if (existingToken && existingChatId) {
    return { token: existingToken, chatId: existingChatId };
  }

  const suffix = Date.now();
  const senderToken = registerUser(`load_sender_${suffix}`);
  registerUser(`load_receiver_${suffix}`);

  const chatRes = http.post(
    `${BASE_URL}/api/v1/chats`,
    JSON.stringify({ username: `load_receiver_${suffix}` }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${senderToken}`
      }
    }
  );
  check(chatRes, { 'chat created': (r) => r.status === 201 || r.status === 200 });

  return {
    token: senderToken,
    chatId: chatRes.json('id')
  };
}

export function writeMessage(data) {
  const res = http.post(
    `${BASE_URL}/api/v1/chats/${data.chatId}/messages`,
    JSON.stringify({
      clientMessageId: uuidv4(),
      text: 'load test payload'
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${data.token}`,
        'X-Correlation-Id': uuidv4()
      },
      tags: { name: 'POST /messages' }
    }
  );

  check(res, {
    'status is 202': (r) => r.status === 202
  });
}
