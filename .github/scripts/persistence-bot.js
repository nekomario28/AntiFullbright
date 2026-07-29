'use strict';

const mineflayer = require('mineflayer');

const port = Number.parseInt(process.env.MC_PORT || '25567', 10);
const username = process.env.MC_USERNAME || 'PersistenceBot';
const timeoutMs = Number.parseInt(process.env.BOT_TIMEOUT_MS || '180000', 10);

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port,
  username,
  version: '1.21.1',
  auth: 'offline',
  checkTimeoutInterval: 30000
});

let spawned = false;
const timeout = setTimeout(() => {
  console.error('PERSISTENCE_BOT_TIMEOUT');
  try { bot.quit('timeout'); } catch (_) {}
  process.exit(2);
}, timeoutMs);

timeout.unref();

bot.once('spawn', () => {
  spawned = true;
  console.log(`PERSISTENCE_BOT_READY username=${username} uuid=${bot.player.uuid}`);
});

bot.on('kicked', reason => {
  console.error(`PERSISTENCE_BOT_KICKED ${JSON.stringify(reason)}`);
  process.exit(spawned ? 3 : 4);
});

bot.on('error', error => {
  console.error(`PERSISTENCE_BOT_ERROR ${error.stack || error.message}`);
  process.exit(5);
});

bot.on('end', reason => {
  console.log(`PERSISTENCE_BOT_END ${reason || 'unknown'}`);
  process.exit(0);
});
