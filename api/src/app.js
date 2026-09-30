import express from 'express';
import cors from 'cors';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isValidPartySize, isValidStaffName } from './staff.js';
import { createReception, listTodaysReceptions } from './store.js';
import { notifySlack } from './slack.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const publicDir = join(__dirname, '../public');

/**
 * @param {{ notify?: typeof notifySlack }} [deps]
 */
export function createApp(deps = {}) {
  const notify = deps.notify ?? notifySlack;
  const app = express();

  app.use(cors());
  app.use(express.json());
  app.use(express.static(publicDir));

  app.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  app.get('/receptions', (_req, res) => {
    res.json(listTodaysReceptions());
  });

  app.post('/receptions', async (req, res) => {
    const { staffName, partySize } = req.body ?? {};

    if (!isValidStaffName(staffName)) {
      return res.status(400).json({
        error: 'invalid_staffName',
        message: 'staffName must be one of the configured staff names',
      });
    }

    if (!isValidPartySize(partySize)) {
      return res.status(400).json({
        error: 'invalid_partySize',
        message: 'partySize must be an integer between 1 and 6',
      });
    }

    const acceptedAt = new Date().toISOString();
    const notified = await notify({
      staffName,
      partySize,
      acceptedAt,
    });

    const reception = createReception({
      staffName,
      partySize,
      notified,
      acceptedAt,
    });

    return res.status(201).json(reception);
  });

  return app;
}
