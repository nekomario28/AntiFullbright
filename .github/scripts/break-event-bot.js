'use strict';

const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');

const port = Number.parseInt(process.env.MC_PORT || '25568', 10);
const username = process.env.MC_USERNAME || 'BreakEventBot';
const timeoutMs = Number.parseInt(process.env.BOT_TIMEOUT_MS || '240000', 10);
const firstPosition = new Vec3(1, -62, 0);
const secondPosition = new Vec3(1, -62, 1);

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port,
  username,
  version: '1.21.1',
  auth: 'offline',
  checkTimeoutInterval: 30000
});

let expectedKick = false;
let completed = false;

const sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

const timeout = setTimeout(() => {
  console.error('BREAK_EVENT_BOT_TIMEOUT');
  try { bot.quit('timeout'); } catch (_) {}
  process.exit(2);
}, timeoutMs);
timeout.unref();

async function waitForFixture() {
  const deadline = Date.now() + 120000;
  while (Date.now() < deadline) {
    const first = bot.blockAt(firstPosition);
    const second = bot.blockAt(secondPosition);
    const nearFixture = bot.entity.position.distanceTo(firstPosition) < 6;
    if (nearFixture && first?.name === 'stone' && second?.name === 'stone') {
      return { first, second };
    }
    await sleep(250);
  }
  throw new Error(`fixture not ready; bot=${bot.entity.position}, first=${bot.blockAt(firstPosition)?.name}, second=${bot.blockAt(secondPosition)?.name}`);
}

bot.once('spawn', async () => {
  console.log(`BREAK_EVENT_BOT_READY username=${username} uuid=${bot.player.uuid}`);
  try {
    const { first, second } = await waitForFixture();

    await bot.dig(first, true);
    console.log(`BREAK_EVENT_BOT_FIRST_BREAK position=${firstPosition}`);

    await sleep(1250);
    expectedKick = true;
    try {
      await bot.dig(second, true);
      console.log(`BREAK_EVENT_BOT_SECOND_BREAK position=${secondPosition}`);
    } catch (error) {
      if (!completed) {
        console.log(`BREAK_EVENT_BOT_SECOND_BREAK_INTERRUPTED ${error.message}`);
      }
    }

    const kickDeadline = Date.now() + 30000;
    while (!completed && Date.now() < kickDeadline) {
      await sleep(100);
    }
    if (!completed) {
      throw new Error('second qualifying break did not disconnect the client');
    }
  } catch (error) {
    console.error(`BREAK_EVENT_BOT_FAILURE ${error.stack || error.message}`);
    try { bot.quit('failure'); } catch (_) {}
    process.exit(3);
  }
});

bot.on('kicked', reason => {
  completed = true;
  console.log(`BREAK_EVENT_BOT_KICKED ${JSON.stringify(reason)}`);
  clearTimeout(timeout);
  process.exit(expectedKick ? 0 : 4);
});

bot.on('error', error => {
  if (completed) return;
  console.error(`BREAK_EVENT_BOT_ERROR ${error.stack || error.message}`);
});

bot.on('end', reason => {
  if (completed) return;
  console.error(`BREAK_EVENT_BOT_END_UNEXPECTED ${reason || 'unknown'}`);
  process.exit(expectedKick ? 5 : 6);
});
