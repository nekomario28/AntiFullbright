#!/usr/bin/env bash
set -euo pipefail

readonly NEOFORGE_VERSION="21.1.235"
readonly MOD_VERSION="1.1.0"
readonly INSTALLER="neoforge-${NEOFORGE_VERSION}-installer.jar"
readonly INSTALLER_BASE="https://maven.neoforged.net/releases/net/neoforged/neoforge/${NEOFORGE_VERSION}"
readonly SERVER_DIR="break-event-enforcement-server"
readonly INSTALL_LOG="break-event-enforcement-install.log"
readonly BOOTSTRAP_LOG="break-event-enforcement-bootstrap.log"
readonly SERVER_LOG="break-event-enforcement-server.log"
readonly BOT_LOG="break-event-enforcement-bot.log"
readonly DATA_COPY="break-event-enforcement-evidence.jsonl"
readonly MOD_DIGEST="break-event-enforcement-mod.sha256"
readonly FIFO="break-event-enforcement-server.stdin"
readonly PORT="25568"
readonly PLAYER="BreakEventBot"

mod_jar="build/libs/antifullbright-${MOD_VERSION}.jar"
if [[ ! -f "${mod_jar}" ]]; then
    echo "Expected packaged mod JAR was not found: ${mod_jar}" >&2
    exit 1
fi
if [[ ! -f "node_modules/mineflayer/package.json" ]]; then
    echo "mineflayer is not installed in node_modules." >&2
    exit 1
fi

rm -rf "${SERVER_DIR}"
rm -f "${INSTALLER}" "${INSTALLER}.sha256" "${INSTALL_LOG}" \
    "${BOOTSTRAP_LOG}" "${SERVER_LOG}" "${BOT_LOG}" "${DATA_COPY}" \
    "${MOD_DIGEST}" "${FIFO}"
mkdir -p "${SERVER_DIR}/mods"

curl --fail --silent --show-error --location \
    "${INSTALLER_BASE}/${INSTALLER}" \
    --output "${INSTALLER}"
curl --fail --silent --show-error --location \
    "${INSTALLER_BASE}/${INSTALLER}.sha256" \
    --output "${INSTALLER}.sha256"

expected_installer_sha="$(awk 'NR == 1 { print $1 }' "${INSTALLER}.sha256")"
actual_installer_sha="$(sha256sum "${INSTALLER}" | awk '{ print $1 }')"
if [[ ! "${expected_installer_sha}" =~ ^[0-9a-fA-F]{64}$ ]]; then
    echo "The official installer checksum file did not contain a valid SHA-256 value." >&2
    exit 1
fi
if [[ "${actual_installer_sha,,}" != "${expected_installer_sha,,}" ]]; then
    echo "NeoForge installer checksum mismatch." >&2
    exit 1
fi

echo "Verified NeoForge installer SHA-256: ${actual_installer_sha}"
(
    cd "${SERVER_DIR}"
    java -jar "../${INSTALLER}" --installServer
) > "${INSTALL_LOG}" 2>&1

if [[ ! -f "${SERVER_DIR}/run.sh" ]]; then
    echo "NeoForge installer did not create run.sh." >&2
    tail -n 200 "${INSTALL_LOG}" >&2
    exit 1
fi

cp "${mod_jar}" "${SERVER_DIR}/mods/"
sha256sum "${mod_jar}" | tee "${MOD_DIGEST}"
printf 'eula=true\n' > "${SERVER_DIR}/eula.txt"
printf 'online-mode=false\nserver-port=%s\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\ndifficulty=peaceful\n' \
    "${PORT}" > "${SERVER_DIR}/server.properties"
chmod +x "${SERVER_DIR}/run.sh"

server_pid=''
bot_pid=''

cleanup() {
    if [[ -n "${bot_pid}" ]] && kill -0 "${bot_pid}" 2>/dev/null; then
        kill -TERM "${bot_pid}" 2>/dev/null || true
        wait "${bot_pid}" 2>/dev/null || true
    fi
    if [[ -n "${server_pid}" ]] && kill -0 "${server_pid}" 2>/dev/null; then
        kill -TERM -- "-${server_pid}" 2>/dev/null || true
        sleep 2
        kill -KILL -- "-${server_pid}" 2>/dev/null || true
        wait "${server_pid}" 2>/dev/null || true
    fi
    exec 9>&- 2>/dev/null || true
    rm -f "${FIFO}"
}
trap cleanup EXIT

start_server() {
    local log_file="$1"
    rm -f "${FIFO}"
    mkfifo "${FIFO}"
    exec 9<>"${FIFO}"
    setsid bash -c 'cd "$1" && exec bash ./run.sh nogui' _ "${SERVER_DIR}" \
        < "${FIFO}" > "${log_file}" 2>&1 &
    server_pid=$!

    local ready=0
    for _ in $(seq 1 300); do
        if grep -Fq 'Done (' "${log_file}" 2>/dev/null \
            && grep -Fq 'AntiFullbright dark-mining detection is ready' "${log_file}" 2>/dev/null; then
            ready=1
            break
        fi
        if ! kill -0 "${server_pid}" 2>/dev/null; then
            break
        fi
        sleep 1
    done

    if [[ "${ready}" -ne 1 ]]; then
        echo "Break-event enforcement server did not reach readiness markers." >&2
        tail -n 300 "${log_file}" >&2 || true
        exit 1
    fi
}

send_console() {
    printf '%s\n' "$1" >&9
}

wait_log() {
    local log_file="$1"
    local pattern="$2"
    local attempts="${3:-90}"
    for _ in $(seq 1 "${attempts}"); do
        if grep -Eq "${pattern}" "${log_file}" 2>/dev/null; then
            return 0
        fi
        if [[ -n "${server_pid}" ]] && ! kill -0 "${server_pid}" 2>/dev/null; then
            break
        fi
        sleep 1
    done
    echo "Required log pattern was not observed: ${pattern}" >&2
    tail -n 300 "${log_file}" >&2 || true
    return 1
}

stop_server_gracefully() {
    local log_file="$1"
    send_console 'stop'
    for _ in $(seq 1 90); do
        if ! kill -0 "${server_pid}" 2>/dev/null; then
            wait "${server_pid}" 2>/dev/null || true
            server_pid=''
            exec 9>&-
            rm -f "${FIFO}"
            return 0
        fi
        sleep 1
    done
    echo "Server did not stop gracefully." >&2
    tail -n 300 "${log_file}" >&2 || true
    return 1
}

# First process creates the world-specific NeoForge SERVER config.
start_server "${BOOTSTRAP_LOG}"
stop_server_gracefully "${BOOTSTRAP_LOG}"

config_file="$(find "${SERVER_DIR}" -type f -name 'antifullbright-server.toml' -print -quit)"
if [[ -z "${config_file}" ]]; then
    echo "Could not locate the generated AntiFullbright server config." >&2
    find "${SERVER_DIR}" -maxdepth 5 -type f -print >&2 || true
    exit 1
fi

CONFIG_FILE="${config_file}" python3 - <<'PY'
import os
import re
from pathlib import Path

path = Path(os.environ['CONFIG_FILE'])
text = path.read_text()
replacements = {
    'continuousMiningSeconds': '1',
    'minimumBlocks': '2',
    'inactivityResetSeconds': '30',
    'torchHoldingGraceSeconds': '0',
    'warningsBeforeKick': '1',
    'warningDecayMinutes': '43200',
    'excludeOperators': 'false',
    'notifyOperatorsAtWarning': '1',
    'persistWarnings': 'false',
    'enableDedicatedLog': 'true',
}
for key, value in replacements.items():
    pattern = re.compile(rf'(?m)^\s*{re.escape(key)}\s*=.*$')
    if not pattern.search(text):
        raise SystemExit(f'missing config key: {key}')
    text = pattern.sub(f'{key} = {value}', text, count=1)
path.write_text(text)
print(f'Configured break-event fixture: {path}')
PY

start_server "${SERVER_LOG}"

# World spawn is randomized. Force-load the origin before constructing the fixture
# so the test does not depend on the generated spawn location.
send_console 'forceload add -16 -16 16 16'
wait_log "${SERVER_LOG}" '(force loaded|forceload|Marked)' 60
sleep 3

# Build a sealed room below maximumY with two reachable natural stone targets.
send_console 'fill -3 -64 -3 3 -57 3 minecraft:stone'
send_console 'fill -2 -63 -2 2 -58 2 minecraft:air'
send_console 'setblock 1 -62 0 minecraft:stone'
send_console 'setblock 1 -62 1 minecraft:stone'
sleep 3

if grep -Fq 'That position is not loaded' "${SERVER_LOG}"; then
    echo "The deterministic break-event fixture was not created in a loaded chunk." >&2
    tail -n 120 "${SERVER_LOG}" >&2
    exit 1
fi

MC_PORT="${PORT}" MC_USERNAME="${PLAYER}" BOT_TIMEOUT_MS=240000 \
    node .github/scripts/break-event-bot.js > "${BOT_LOG}" 2>&1 &
bot_pid=$!

for _ in $(seq 1 180); do
    if grep -Fq 'BREAK_EVENT_BOT_READY' "${BOT_LOG}" 2>/dev/null; then
        break
    fi
    if ! kill -0 "${bot_pid}" 2>/dev/null; then
        echo "Break-event bot exited before reaching spawn." >&2
        cat "${BOT_LOG}" >&2 || true
        exit 1
    fi
    sleep 1
done
if ! grep -Fq 'BREAK_EVENT_BOT_READY' "${BOT_LOG}"; then
    echo "Break-event bot did not reach spawn." >&2
    cat "${BOT_LOG}" >&2 || true
    exit 1
fi

wait_log "${SERVER_LOG}" "${PLAYER} joined the game" 120
send_console "gamemode survival ${PLAYER}"
send_console "effect clear ${PLAYER}"
send_console "tp ${PLAYER} 0.5 -63 0.5"

bot_finished=0
for _ in $(seq 1 240); do
    if ! kill -0 "${bot_pid}" 2>/dev/null; then
        bot_finished=1
        break
    fi
    sleep 1
done
if [[ "${bot_finished}" -ne 1 ]]; then
    echo "Break-event bot did not finish the enforcement sequence." >&2
    cat "${BOT_LOG}" >&2 || true
    exit 1
fi
set +e
wait "${bot_pid}"
bot_status=$?
set -e
bot_pid=''
if [[ "${bot_status}" -ne 0 ]]; then
    echo "Break-event bot failed with status ${bot_status}." >&2
    cat "${BOT_LOG}" >&2 || true
    exit "${bot_status}"
fi

wait_log "${SERVER_LOG}" 'Dark-mining detection: .*"action":"kick"' 60
sleep 3
send_console 'forceload remove all'
stop_server_gracefully "${SERVER_LOG}"

evidence_file="${SERVER_DIR}/logs/dark-mining-detections.jsonl"
if [[ ! -s "${evidence_file}" ]]; then
    echo "Expected dedicated enforcement evidence was not written: ${evidence_file}" >&2
    tail -n 300 "${SERVER_LOG}" >&2 || true
    exit 1
fi
cp "${evidence_file}" "${DATA_COPY}"

EVIDENCE_FILE="${DATA_COPY}" python3 - <<'PY'
import json
import os
from pathlib import Path

path = Path(os.environ['EVIDENCE_FILE'])
records = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
matches = [record for record in records if record.get('playerName') == 'BreakEventBot']
if len(matches) != 1:
    raise SystemExit(f'expected one BreakEventBot record, found {len(matches)}: {records}')
record = matches[0]
expected = {
    'warningCount': 1,
    'action': 'kick',
    'lastBrokenBlock': 'minecraft:stone',
    'eyeBlockLight': 0,
    'eyeSkyLight': 0,
    'brokenBlockLight': 0,
    'brokenSkyLight': 0,
}
for key, value in expected.items():
    if record.get(key) != value:
        raise SystemExit(f'unexpected {key}: {record.get(key)!r}, expected {value!r}')
if record.get('countedBlocks', 0) < 2:
    raise SystemExit(f'expected at least two counted blocks: {record}')
if record.get('continuousMiningSeconds', 0) < 1:
    raise SystemExit(f'expected at least one continuous mining second: {record}')
print('Validated event-to-kick evidence:', json.dumps(record, sort_keys=True))
PY

if ! grep -Fq 'BREAK_EVENT_BOT_FIRST_BREAK' "${BOT_LOG}" \
    || ! grep -Fq 'BREAK_EVENT_BOT_KICKED' "${BOT_LOG}"; then
    echo "Bot log is missing first-break or kick evidence." >&2
    cat "${BOT_LOG}" >&2 || true
    exit 1
fi

if grep -Eq 'NoClassDefFoundError|ClassNotFoundException|ModLoadingException|ModLoadingCrashException|Failed to load registries' \
    "${BOOTSTRAP_LOG}" "${SERVER_LOG}"; then
    echo "Break-event enforcement logs contain a loading failure." >&2
    tail -n 300 "${BOOTSTRAP_LOG}" >&2
    tail -n 300 "${SERVER_LOG}" >&2
    exit 1
fi

echo "Real packet -> NeoForge BreakEvent -> AntiFullbright kick path verified for ${PLAYER}."
grep -F 'BREAK_EVENT_BOT_FIRST_BREAK' "${BOT_LOG}" | tail -n 1
grep -F 'BREAK_EVENT_BOT_KICKED' "${BOT_LOG}" | tail -n 1
grep -E 'Dark-mining detection: .*"action":"kick"' "${SERVER_LOG}" | tail -n 1
