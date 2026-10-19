// Functional fixture check only; these timings are NOT a performance benchmark.
import http from 'k6/http';
import { check } from 'k6';
import { WebSocket } from 'k6/websockets';
import { setTimeout, clearTimeout } from 'k6/timers';

const fixture = JSON.parse(open('../../../build/chat-perf/fixtures.json'));
export const options = { vus: 1, iterations: 1, thresholds: { checks: ['rate==1'] } };

function frame(command, headers = {}, body = '') {
  return `${command}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n')}\n\n${body}\0`;
}

function stompCheck(user, archived) {
  const socket = new WebSocket(fixture.ws_url, ['v12.stomp']);
  const content = `perf-fixture-check-${Date.now()}-${user.user_id}`;
  let buffer = '';
  let success = false;
  let actionTimer;
  const deadline = setTimeout(() => socket.close(), 15000);
  socket.onopen = () => socket.send(frame('CONNECT', {
    'accept-version': '1.2', host: 'localhost', 'heart-beat': '0,0',
    Authorization: `Bearer ${user.access_token}`,
  }));
  socket.onmessage = (event) => {
    buffer += event.data;
    let boundary;
    while ((boundary = buffer.indexOf('\0')) >= 0) {
      const message = buffer.slice(0, boundary).replace(/^\n+/, '');
      buffer = buffer.slice(boundary + 1);
      const separator = message.indexOf('\n\n');
      const command = message.split('\n', 1)[0];
      if (command === 'CONNECTED') {
        socket.send(frame('SUBSCRIBE', { id: 'errors', destination: '/user/queue/errors', ack: 'auto' }));
        socket.send(frame('SUBSCRIBE', { id: 'notices', destination: '/user/queue/chat-room-events', ack: 'auto' }));
        if (!archived) {
          socket.send(frame('SUBSCRIBE', { id: 'room', destination: `/sub/chat-rooms/${user.chat_room_id}`, ack: 'auto' }));
        }
        // Low-load fixture check only. The performance driver needs a readiness barrier.
        actionTimer = setTimeout(() => {
          if (archived) {
            socket.send(frame('SUBSCRIBE', { id: 'closed', destination: `/sub/chat-rooms/${user.chat_room_id}`, ack: 'auto' }));
          } else {
            socket.send(frame('SEND', {
              destination: `/pub/chat-rooms/${user.chat_room_id}/messages`, 'content-type': 'application/json',
            }, JSON.stringify({ content })));
          }
        }, 1000);
      } else if (command === 'MESSAGE') {
        const payload = JSON.parse(message.slice(separator + 2));
        success = archived
          ? payload.type === 'CHAT_ROOM_CLOSED' && payload.chat_room_id === user.chat_room_id
          : payload.content === content && payload.sender_id === user.user_id && payload.message_id > 0;
        if (success) socket.close();
      } else if (command === 'ERROR') {
        check(false, { 'no STOMP ERROR': (value) => value });
        socket.close();
      }
    }
  };
  socket.onerror = () => check(false, { 'no WebSocket transport error': (value) => value });
  socket.onclose = () => {
    clearTimeout(deadline);
    if (actionTimer) clearTimeout(actionTimer);
    check(success, { [archived ? 'archived subscription rejected with closure notice' : 'active message saved and echoed']: (value) => value });
  };
}

export default function () {
  if (fixture.base_url !== 'http://127.0.0.1:18082' || fixture.ws_url !== 'ws://127.0.0.1:18082/ws-chat') {
    throw new Error('Fixture check only supports the dedicated local app');
  }
  for (const user of [...fixture.users, ...fixture.archived_users]) {
    const response = http.get(`${fixture.base_url}/api/v1/chat-rooms/${user.chat_room_id}/messages?size=100`, {
      headers: { Authorization: `Bearer ${user.access_token}` },
    });
    check(response, {
      'all fixture JWTs can read their room': (r) => r.status === 200,
      // Load runs append messages; seed history may have moved past the first page.
      'room history is readable': (r) => r.status === 200 && r.json().data.messages.length >= fixture.dataset.messages_per_room,
    });
  }
  stompCheck(fixture.users[0], false);
  stompCheck(fixture.archived_users[0], true);
}
