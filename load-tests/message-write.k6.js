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
const TARGET_RATE = Number(__ENV.TARGET_TPS || '2000');
const DURATION = __ENV.DURATION || '2m';

export const options = {
  scenarios: {
    message_writes: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.min(TARGET_RATE, 500),
      maxVUs: Math.min(TARGET_RATE * 2, 1000)
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500']
  }
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
  const receiverToken = registerUser(`load_receiver_${suffix}`);

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
    chatId: chatRes.json('id'),
    receiverToken
  };
}

export default function (data) {
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
