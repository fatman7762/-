import { randomUUID } from 'node:crypto';

/** @typedef {{ id: string, staffName: string, partySize: number, acceptedAt: string, notified: boolean }} Reception */

/** @type {Reception[]} */
const receptions = [];

/**
 * @param {{ staffName: string, partySize: number, notified: boolean }} input
 * @returns {Reception}
 */
export function createReception({ staffName, partySize, notified }) {
  const reception = {
    id: randomUUID(),
    staffName,
    partySize,
    acceptedAt: new Date().toISOString(),
    notified,
  };
  receptions.push(reception);
  return reception;
}

/**
 * Returns receptions whose acceptedAt falls on the given calendar day (local TZ).
 * @param {Date} [now]
 * @returns {Reception[]}
 */
export function listTodaysReceptions(now = new Date()) {
  const start = new Date(now);
  start.setHours(0, 0, 0, 0);
  const end = new Date(now);
  end.setHours(23, 59, 59, 999);

  return receptions.filter((r) => {
    const t = new Date(r.acceptedAt).getTime();
    return t >= start.getTime() && t <= end.getTime();
  });
}

/** Test helper */
export function clearReceptions() {
  receptions.length = 0;
}
