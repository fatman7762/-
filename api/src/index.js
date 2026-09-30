import 'dotenv/config';
import { createApp } from './app.js';

const port = Number(process.env.PORT) || 8081;
const app = createApp();

app.listen(port, () => {
  console.log(`Reception API listening on http://0.0.0.0:${port}`);
});
