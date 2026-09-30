import { STAFF_MENTION_ENV } from './staff.js';

/**
 * Format acceptedAt ISO → YYYY-MM-DD HH:mm (local)
 * @param {string} iso
 * @returns {string}
 */
export function formatAcceptedAt(iso) {
  const d = new Date(iso);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${day} ${hh}:${mm}`;
}

/**
 * Build Slack notification text in Japanese.
 * @param {{ staffName: string, partySize: number, acceptedAt: string, env?: NodeJS.ProcessEnv }} opts
 * @returns {string}
 */
export function buildSlackMessage({ staffName, partySize, acceptedAt, env = process.env }) {
  const mentionKey = STAFF_MENTION_ENV[staffName];
  const memberId = mentionKey ? env[mentionKey]?.trim() : '';
  const who = memberId ? `<@${memberId}>` : staffName;

  return [
    '株式会社ライトパス 総合受付',
    `${who} さん、お客様が ${partySize}名 お見えです。`,
    `担当: ${staffName}`,
    `受付時刻: ${formatAcceptedAt(acceptedAt)}`,
  ].join('\n');
}

/**
 * Post to Slack Incoming Webhook. Returns true on success.
 * @param {{ staffName: string, partySize: number, acceptedAt: string, webhookUrl?: string, env?: NodeJS.ProcessEnv, fetchImpl?: typeof fetch }} opts
 * @returns {Promise<boolean>}
 */
export async function notifySlack({
  staffName,
  partySize,
  acceptedAt,
  webhookUrl = process.env.SLACK_WEBHOOK_URL,
  env = process.env,
  fetchImpl = fetch,
}) {
  const url = webhookUrl?.trim();
  if (!url) {
    return false;
  }

  const text = buildSlackMessage({ staffName, partySize, acceptedAt, env });

  try {
    const res = await fetchImpl(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    });
    return res.ok;
  } catch {
    return false;
  }
}
