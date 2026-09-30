import { describe, it, beforeEach, after, mock } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createApp } from '../src/app.js';
import { clearReceptions, reloadReceptions } from '../src/store.js';
import { buildSlackMessage, notifySlack } from '../src/slack.js';
import { STAFF_NAMES } from '../src/staff.js';

const tempDir = mkdtempSync(join(tmpdir(), 'reception-api-'));
process.env.RECEPTIONS_FILE = join(tempDir, 'receptions.json');
reloadReceptions();

after(() => {
  rmSync(tempDir, { recursive: true, force: true });
});

/**
 * @param {import('express').Express} app
 * @param {string} method
 * @param {string} path
 * @param {object} [body]
 */
async function request(app, method, path, body) {
  const server = app.listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  const { port } = /** @type {import('node:net').AddressInfo} */ (server.address());

  try {
    const res = await fetch(`http://127.0.0.1:${port}${path}`, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    const json = text ? JSON.parse(text) : null;
    return { status: res.status, json };
  } finally {
    await new Promise((resolve, reject) => server.close((err) => (err ? reject(err) : resolve())));
  }
}

describe('POST /receptions validation', () => {
  beforeEach(() => {
    clearReceptions();
  });

  it('rejects missing body fields', async () => {
    const app = createApp({ notify: async () => true });
    const res = await request(app, 'POST', '/receptions', {});
    assert.equal(res.status, 400);
  });

  it('rejects unknown staffName', async () => {
    const app = createApp({ notify: async () => true });
    const res = await request(app, 'POST', '/receptions', {
      staffName: '山田',
      partySize: 2,
    });
    assert.equal(res.status, 400);
    assert.equal(res.json.error, 'invalid_staffName');
  });

  it('rejects partySize 0', async () => {
    const app = createApp({ notify: async () => true });
    const res = await request(app, 'POST', '/receptions', {
      staffName: '野坂',
      partySize: 0,
    });
    assert.equal(res.status, 400);
    assert.equal(res.json.error, 'invalid_partySize');
  });

  it('rejects partySize 7', async () => {
    const app = createApp({ notify: async () => true });
    const res = await request(app, 'POST', '/receptions', {
      staffName: '野坂',
      partySize: 7,
    });
    assert.equal(res.status, 400);
  });

  it('rejects non-integer partySize', async () => {
    const app = createApp({ notify: async () => true });
    const res = await request(app, 'POST', '/receptions', {
      staffName: '野坂',
      partySize: 1.5,
    });
    assert.equal(res.status, 400);
  });

  for (const name of STAFF_NAMES) {
    it(`accepts staffName ${name}`, async () => {
      const app = createApp({ notify: async () => true });
      const res = await request(app, 'POST', '/receptions', {
        staffName: name,
        partySize: 1,
      });
      assert.equal(res.status, 201);
      assert.equal(res.json.staffName, name);
    });
  }
});

describe('POST /receptions success with mocked Slack', () => {
  beforeEach(() => {
    clearReceptions();
  });

  it('returns 201 with id, fields, and notified:true', async () => {
    const notify = mock.fn(async () => true);
    const app = createApp({ notify });
    const res = await request(app, 'POST', '/receptions', {
      staffName: '伊藤',
      partySize: 3,
    });

    assert.equal(res.status, 201);
    assert.ok(res.json.id);
    assert.equal(res.json.staffName, '伊藤');
    assert.equal(res.json.partySize, 3);
    assert.ok(res.json.acceptedAt);
    assert.equal(res.json.notified, true);
    assert.equal(notify.mock.callCount(), 1);
  });

  it('lists today receptions via GET /receptions', async () => {
    const app = createApp({ notify: async () => true });
    await request(app, 'POST', '/receptions', { staffName: '坂本', partySize: 2 });
    const list = await request(app, 'GET', '/receptions');
    assert.equal(list.status, 200);
    assert.equal(list.json.length, 1);
    assert.equal(list.json[0].staffName, '坂本');
  });

  it('GET /health returns ok', async () => {
    const app = createApp({ notify: async () => true });
    const res = await request(app, 'GET', '/health');
    assert.equal(res.status, 200);
    assert.deepEqual(res.json, { status: 'ok' });
  });
});

describe('Slack failure still returns 201', () => {
  beforeEach(() => {
    clearReceptions();
  });

  it('returns notified:false when notify returns false', async () => {
    const app = createApp({ notify: async () => false });
    const res = await request(app, 'POST', '/receptions', {
      staffName: '合田',
      partySize: 4,
    });
    assert.equal(res.status, 201);
    assert.equal(res.json.notified, false);
  });

  it('notifySlack returns false when webhook URL missing', async () => {
    const ok = await notifySlack({
      staffName: '野坂',
      partySize: 1,
      acceptedAt: new Date().toISOString(),
      webhookUrl: '',
      fetchImpl: async () => {
        throw new Error('should not be called');
      },
    });
    assert.equal(ok, false);
  });

  it('notifySlack returns false when fetch fails', async () => {
    const ok = await notifySlack({
      staffName: '野坂',
      partySize: 1,
      acceptedAt: new Date().toISOString(),
      webhookUrl: 'https://hooks.slack.com/services/test',
      fetchImpl: async () => {
        throw new Error('network');
      },
    });
    assert.equal(ok, false);
  });

  it('notifySlack returns false when Slack responds non-OK', async () => {
    const ok = await notifySlack({
      staffName: '野坂',
      partySize: 1,
      acceptedAt: new Date().toISOString(),
      webhookUrl: 'https://hooks.slack.com/services/test',
      fetchImpl: async () => ({ ok: false }),
    });
    assert.equal(ok, false);
  });
});

describe('buildSlackMessage', () => {
  it('uses plain @name mention with party size', () => {
    const text = buildSlackMessage({
      staffName: '野坂',
      partySize: 2,
      acceptedAt: '2026-03-15T04:05:00.000Z',
    });
    assert.match(text, /株式会社ライトパス 総合受付/);
    assert.match(text, /@野坂 さん、お客様が 2名 お見えです。/);
    assert.match(text, /担当: 野坂/);
    assert.match(text, /人数: 2名/);
    assert.match(text, /受付時刻: /);
  });

  it('labels party size 6 as 6名～', () => {
    const text = buildSlackMessage({
      staffName: '合田',
      partySize: 6,
      acceptedAt: '2026-03-15T04:05:00.000Z',
    });
    assert.match(text, /@合田 さん、お客様が 6名～ お見えです。/);
    assert.match(text, /人数: 6名～/);
  });

  it('sends fired message for 中原', () => {
    const text = buildSlackMessage({
      staffName: '中原',
      partySize: 3,
      acceptedAt: '2026-03-15T04:05:00.000Z',
    });
    assert.equal(text, 'お前はクビだ！\n@中原 3名');
  });
});
