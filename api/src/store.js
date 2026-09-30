import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

/** @typedef {{ id: string, staffName: string, partySize: number, acceptedAt: string, notified: boolean }} Reception */

/**
 * @returns {string}
 */
function dataFilePath() {
  return resolve(process.env.RECEPTIONS_FILE || 'data/receptions.json');
}

/**
 * @returns {Reception[]}
 */
function loadFromDisk() {
  const file = dataFilePath();
  if (!existsSync(file)) {
    return [];
  }
  try {
    const raw = readFileSync(file, 'utf8');
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/**
 * @param {Reception[]} items
 */
function saveToDisk(items) {
  const file = dataFilePath();
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, JSON.stringify(items, null, 2), 'utf8');
}

/** @type {Reception[]} */
let receptions = loadFromDisk();

/**
 * @param {{ staffName: string, partySize: number, notified: boolean, acceptedAt?: string }} input
 * @returns {Reception}
 */
export function createReception({ staffName, partySize, notified, acceptedAt }) {
  const reception = {
    id: randomUUID(),
    staffName,
    partySize,
    acceptedAt: acceptedAt ?? new Date().toISOString(),
    notified,
  };
  receptions.push(reception);
  saveToDisk(receptions);
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

/** Reload from disk (startup / tests). */
export function reloadReceptions() {
  receptions = loadFromDisk();
}

/** Test helper — clears memory and the data file. */
export function clearReceptions() {
  receptions = [];
  saveToDisk(receptions);
}
