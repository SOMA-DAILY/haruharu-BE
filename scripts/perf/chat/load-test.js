// One k6 VU owns one room's six real WebSockets, so readiness and delivery can be checked locally.
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { WebSocket } from 'k6/websockets';
import { setTimeout, clearTimeout, setInterval, clearInterval } from 'k6/timers';

const fixture = JSON.parse(open('../../../build/chat-perf/fixtures.json'));
function integer(name, fallback, min, max) {
  const value = Number(__ENV[name] ?? fallback);
  if (!Number.isInteger(value) || value < min || value > max) throw new Error(`Invalid ${name}`);
  return value;
}
const USERS = integer('USERS', 60, 6, fixture.users.length);
const WARMUP = integer('WARMUP', 60, 0, 600);
const DURATION = integer('DURATION', 180, 1, 1800);
const INTERVAL_MS = integer('INTERVAL_MS', 5000, 100, 60000);
const DRAIN = integer('DRAIN', 30, 1, 120);
const LABEL = __ENV.LABEL || 'close-at';
const DB_POOL_SIZE = __ENV.DB_POOL_SIZE || 'unrecorded';
if (USERS % 6 || WARMUP * 1000 % INTERVAL_MS || DURATION * 1000 % INTERVAL_MS) {
  throw new Error('USERS must be a multiple of 6; WARMUP/DURATION must contain whole send intervals');
}
if (!/^[a-zA-Z0-9_-]+$/.test(LABEL)) throw new Error('LABEL may only contain letters, numbers, _ and -');
const EXPECTED_SENDS = USERS * DURATION * 1000 / INTERVAL_MS;

const sent = new Counter('chat_sent');
const delivered = new Counter('chat_delivered');
const missing = new Counter('chat_missing');
const duplicates = new Counter('chat_duplicates');
const errors = new Counter('chat_errors');
const skipped = new Counter('chat_generator_skipped');
const echo = new Trend('chat_echo_ms', true);
const peers = new Trend('chat_peer_ms', true);
const lag = new Trend('chat_send_lag_ms', true);

export const options = {
  scenarios: { rooms: { executor: 'per-vu-iterations', vus: USERS / 6, iterations: 1,
    maxDuration: `${WARMUP + DURATION + DRAIN + 60}s` } },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max', 'count'],
  thresholds: {
    checks: ['rate==1'], chat_sent: [`count==${EXPECTED_SENDS}`],
    chat_delivered: [`count==${EXPECTED_SENDS * 6}`], chat_missing: ['count==0'],
    chat_duplicates: ['count==0'], chat_errors: ['count==0'], chat_generator_skipped: ['count==0'],
  },
};

export function setup() {
  if (fixture.ws_url !== 'ws://127.0.0.1:18082/ws-chat') throw new Error('Only the dedicated local app is allowed');
  const testEnd = Date.now() + (WARMUP + DURATION + DRAIN + 60) * 1000;
  if (Date.parse(fixture.expires_at) <= testEnd) throw new Error('Refresh test JWTs with fixture.py credentials');
  for (let offset = 0; offset < USERS; offset += 6) {
    const members = fixture.users.slice(offset, offset + 6);
    if (new Set(members.map((u) => u.chat_room_id)).size !== 1) throw new Error('Fixture room grouping is invalid');
    const room = fixture.rooms.find((r) => r.chat_room_id === members[0].chat_room_id);
    if (!room || Date.parse(room.ends_at + '+09:00') <= testEnd) throw new Error('Fixture room is expired / expires during test');
  }
  const runId = `${LABEL}-${USERS}-${Date.now()}`;
  console.log(`run=${runId}, connections=${USERS}, rooms/VUs=${USERS / 6}, warmup=${WARMUP}s, measure=${DURATION}s, drain=${DRAIN}s`);
  return { runId };
}

function frame(command, headers = {}, body = '') {
  return `${command}\n${Object.entries(headers).map(([key, value]) => `${key}:${value}`).join('\n')}\n\n${body}\0`;
}

export default function ({ runId }) {
  const members = fixture.users.slice((__VU - 1) * 6, __VU * 6);
  const roomId = members[0].chat_room_id;
  const sockets = [];
  const timers = new Set();
  const pending = new Map();
  const readyMembers = new Set();
  const probeContent = `perf-ready|${runId}|${roomId}`;
  let connected = 0, ready = false, finished = false, sequence = 0, sentCount = 0;
  let probeTimer, readyAt;
  [sent, delivered, missing, duplicates, errors, skipped].forEach((metric) => metric.add(0));

  function later(callback, delay) {
    const timer = setTimeout(() => { timers.delete(timer); callback(); }, Math.max(0, delay));
    timers.add(timer);
  }
  function finish(completed) {
    if (finished) return;
    finished = true;
    timers.forEach(clearTimeout);
    if (probeTimer) clearInterval(probeTimer);
    let missingCount = 0;
    pending.forEach((item) => { if (item.measured) missingCount += 6 - item.receivers.size; });
    missing.add(missingCount);
    check(ready, { 'all six subscriptions became ready': (value) => value });
    check(completed, { 'measurement and drain completed': (value) => value });
    check(sentCount, { 'scheduled measured sends completed': (value) => value === EXPECTED_SENDS / (USERS / 6) });
    sockets.forEach((socket) => { if (socket.readyState === 1) socket.close(); });
  }
  function failed(reason) {
    if (finished) return;
    errors.add(1);
    console.error(`room=${roomId}: ${reason}`);
    finish(false);
  }
  function send(slot, content) {
    sockets[slot].send(frame('SEND', { destination: `/pub/chat-rooms/${roomId}/messages`,
      'content-type': 'application/json' }, JSON.stringify({ content })));
  }
  function schedule(slot, due) {
    const end = readyAt + (WARMUP + DURATION) * 1000;
    if (due >= end || finished) return;
    later(() => {
      if (finished) return;
      const now = Date.now();
      const measured = due >= readyAt + WARMUP * 1000;
      // Never hide generator saturation by reducing the target rate or catching up with a burst.
      if (now >= end || now - due >= INTERVAL_MS) {
        if (measured) skipped.add(1);
      } else {
        const content = `perf-load|${runId}|${roomId}|${slot}|${++sequence}`;
        const item = { slot, measured, at: now, receivers: new Set() };
        pending.set(content, item);
        try {
          send(slot, content);
          if (measured) { sent.add(1); sentCount++; lag.add(now - due); }
        } catch (_) {
          pending.delete(content);
          failed('SEND failed');
          return;
        }
      }
      schedule(slot, due + INTERVAL_MS);
    }, due - Date.now());
  }
  function start() {
    ready = true;
    clearInterval(probeTimer);
    readyAt = Date.now();
    members.forEach((_, slot) => {
      const offset = (Math.floor(slot * INTERVAL_MS / 6) + __VU * 137) % INTERVAL_MS;
      schedule(slot, readyAt + offset);
    });
    later(() => finish(true), (WARMUP + DURATION + DRAIN) * 1000);
  }
  later(() => {
    if (!ready) failed(`subscription readiness timeout (STOMP connected=${connected}/6, probe received=${readyMembers.size}/6)`);
  }, 30000);

  members.forEach((user, slot) => {
    const socket = new WebSocket(fixture.ws_url, ['v12.stomp']);
    sockets.push(socket);
    let buffer = '';
    socket.onopen = () => {
      if (finished) { socket.close(); return; }
      socket.send(frame('CONNECT', { 'accept-version': '1.2', host: 'localhost', 'heart-beat': '0,0',
        Authorization: `Bearer ${user.access_token}` }));
    };
    socket.onmessage = (event) => {
      if (finished) return;
      buffer += event.data;
      let boundary;
      while ((boundary = buffer.indexOf('\0')) >= 0) {
        const message = buffer.slice(0, boundary).replace(/^\n+/, '');
        buffer = buffer.slice(boundary + 1);
        const command = message.split('\n', 1)[0];
        if (command === 'CONNECTED') {
          socket.send(frame('SUBSCRIBE', { id: 'errors', destination: '/user/queue/errors', ack: 'auto' }));
          socket.send(frame('SUBSCRIBE', { id: 'notices', destination: '/user/queue/chat-room-events', ack: 'auto' }));
          socket.send(frame('SUBSCRIBE', { id: 'room', destination: `/sub/chat-rooms/${roomId}`, ack: 'auto' }));
          if (++connected === 6) {
            probeTimer = setInterval(() => { if (!finished && !ready) send(0, probeContent); }, 500);
          }
        } else if (command === 'MESSAGE') {
          let payload;
          try { payload = JSON.parse(message.slice(message.indexOf('\n\n') + 2)); }
          catch (_) { failed('invalid MESSAGE JSON'); return; }
          if (payload.code || payload.type === 'CHAT_ROOM_CLOSED') { failed('server rejected message / room closed'); return; }
          if (payload.content === probeContent) {
            readyMembers.add(slot);
            if (!ready && readyMembers.size === 6) start();
            continue;
          }
          const item = pending.get(payload.content);
          if (!item) continue;
          if (payload.chat_room_id !== roomId || payload.sender_id !== members[item.slot].user_id || !(payload.message_id > 0)) {
            failed('unexpected room / sender / message ID'); return;
          }
          if (item.receivers.has(slot)) { if (item.measured) duplicates.add(1); continue; }
          item.receivers.add(slot);
          if (item.measured) {
            delivered.add(1);
            (slot === item.slot ? echo : peers).add(Date.now() - item.at);
          } else if (item.receivers.size === 6) pending.delete(payload.content);
        } else if (command === 'ERROR') { failed('STOMP ERROR'); return; }
      }
    };
    socket.onerror = () => failed('WebSocket transport error');
    socket.onclose = () => { if (!finished) failed('unexpected WebSocket close'); };
  });
}

export function handleSummary(data) {
  const path = `build/chat-perf/${LABEL}-${USERS}-${Date.now()}.json`;
  const values = (name) => data.metrics[name]?.values || {};
  const count = (name) => values(name).count ?? 0;
  const format = (value) => typeof value === 'number' ? value.toFixed(2) : 'n/a';
  const latency = values('chat_echo_ms');
  const failures = Object.values(data.metrics).flatMap((metric) => Object.entries(metric.thresholds || {}))
    .filter(([, threshold]) => !threshold.ok).map(([name]) => name);
  const result = {
    config: { label: LABEL, users: USERS, rooms: USERS / 6, warmup_seconds: WARMUP,
      measure_seconds: DURATION, drain_seconds: DRAIN, interval_ms: INTERVAL_MS,
      expected_sends: EXPECTED_SENDS, target_sends_per_second: USERS * 1000 / INTERVAL_MS,
      db_pool_size_declared: DB_POOL_SIZE, dataset: fixture.dataset,
      note: 'Local single app; DB_POOL_SIZE is metadata only, not a server setting; record index state separately; room windows begin after each readiness barrier.' },
    failed_thresholds: failures, k6: data,
  };
  const output = `\n${LABEL}: ${USERS} connections / ${USERS / 6} rooms\n`
    + `Measured sends: ${count('chat_sent')} / ${EXPECTED_SENDS}; deliveries: ${count('chat_delivered')} / ${EXPECTED_SENDS * 6}\n`
    + `Echo latency ms: p50=${format(latency.med)}, p95=${format(latency['p(95)'])}, p99=${format(latency['p(99)'])}\n`
    + `Missing=${count('chat_missing')}, duplicates=${count('chat_duplicates')}, errors=${count('chat_errors')}, generator_skipped=${count('chat_generator_skipped')}\n`
    + `Thresholds: ${failures.length ? 'FAIL' : 'PASS'}; result: ${path}\n`;
  return { stdout: output, [path]: JSON.stringify(result, null, 2) + '\n' };
}
