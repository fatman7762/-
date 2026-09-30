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
 * Mentions use plain "@氏名" (not Slack member IDs).
 * 中原・梁瀬はギャグ用に専用メッセージ。
 * @param {{ staffName: string, partySize: number, acceptedAt: string }} opts
 * @returns {string}
 */
export function buildSlackMessage({ staffName, partySize, acceptedAt }) {
  if (staffName === '中原') {
    return [`お前はクビだ！`, `@中原 ${partySize}名`].join('\n');
  }
  if (staffName === '梁瀬') {
    return [`おかえり`, `@梁瀬 ${partySize}名`].join('\n');
  }

  const sizeLabel = partySize >= 6 ? '6名～' : `${partySize}名`;

  return [
    '株式会社ライトパス 総合受付',
    `@${staffName} さん、お客様が ${sizeLabel} お見えです。`,
    `担当: ${staffName}`,
    `人数: ${sizeLabel}`,
    `受付時刻: ${formatAcceptedAt(acceptedAt)}`,
  ].join('\n');
}

/**
 * Post to Slack Incoming Webhook. Returns true on success.
 * @param {{ staffName: string, partySize: number, acceptedAt: string, webhookUrl?: string, fetchImpl?: typeof fetch }} opts
 * @returns {Promise<boolean>}
 */
export async function notifySlack({
  staffName,
  partySize,
  acceptedAt,
  webhookUrl = process.env.SLACK_WEBHOOK_URL,
  fetchImpl = fetch,
}) {
  const url = webhookUrl?.trim();
  if (!url) {
    return false;
  }

  const text = buildSlackMessage({ staffName, partySize, acceptedAt });

  try {
    const res = await fetchImpl(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
      signal: AbortSignal.timeout(5000),
    });
    return res.ok;
  } catch {
    return false;
  }
}
