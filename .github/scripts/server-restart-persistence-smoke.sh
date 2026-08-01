#!/usr/bin/env bash
set -euo pipefail

readonly NEOFORGE_VERSION="21.1.235"
readonly MOD_VERSION="1.1.0"
readonly INSTALLER="neoforge-${NEOFORGE_VERSION}-installer.jar"
readonly INSTALLER_BASE="https://maven.neoforged.net/releases/net/neoforged/neoforge/${NEOFORGE_VERSION}"
readonly SERVER_DIR="restart-persistence-server"
readonly INSTALL_LOG="restart-persistence-install.log"
readonly FIRST_LOG="restart-persistence-first.log"
readonly SECOND_LOG="restart-persistence-second.log"
readonly FIRST_BOT_LOG="restart-persistence-bot-first.log"
readonly SECOND_BOT_LOG="restart-persistence-bot-second.log"
readonly DATA_DIGEST="restart-persistence-data.sha256"
readonly MOD_DIGEST="restart-persistence-mod.sha256"
readonly FIFO="restart-persistence-server.stdin"
readonly PORT="25567"
readonly PLAYER="PersistenceBot"
readonly EXPECTED_WARNING_LEVEL="3"

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
    "${FIRST_LOG}" "${SECOND_LOG}" "${FIRST_BOT_LOG}" "${SECOND_BOT_LOG}" \
    "${DATA_DIGEST}" "${MOD_DIGEST}" "${FIFO}"
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
printf 'online-mode=false\nserver-port=%s\nview-distance=4\nsimulation-distance=4\n' "${PORT}" > "${SERVER_DIR}/server.properties"
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
        echo "Restart persistence server did not reach readiness markers." >&2
        tail -n 260 "${log_file}" >&2 || true
        exit 1
    fi
}

start_bot() {
    local log_file="$1"
    MC_PORT="${PORT}" MC_USERNAME="${PLAYER}" BOT_TIMEOUT_MS=240000 \
        node .github/scripts/persistence-bot.js > "${log_file}" 2>&1 &
    bot_pid=$!

    local ready=0
    for _ in $(seq 1 180); do
        if grep -Fq 'PERSISTENCE_BOT_READY' "${log_file}" 2>/dev/null; then
            ready=1
            break
        fi
        if ! kill -0 "${bot_pid}" 2>/dev/null; then
            break
        fi
        sleep 1
    done

    if [[ "${ready}" -ne 1 ]]; then
        echo "Persistence bot did not join the server." >&2
        cat "${log_file}" >&2 || true
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
        if ! kill -0 "${server_pid}" 2>/dev/null; then
            break
        fi
        sleep 1
    done
    echo "Required log pattern was not observed: ${pattern}" >&2
    tail -n 260 "${log_file}" >&2 || true
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
    tail -n 260 "${log_file}" >&2 || true
    return 1
}

start_server "${FIRST_LOG}"
start_bot "${FIRST_BOT_LOG}"
wait_log "${FIRST_LOG}" "${PLAYER} joined the game" 120
send_console "darkmining setwarning ${PLAYER} ${EXPECTED_WARNING_LEVEL}"
wait_log "${FIRST_LOG}" "(warning level to ${EXPECTED_WARNING_LEVEL}|警告レベルを ${EXPECTED_WARNING_LEVEL} に設定)" 60
send_console 'save-all flush'
wait_log "${FIRST_LOG}" '(Saved the game|Saved all player data)' 60
stop_server_gracefully "${FIRST_LOG}"
wait "${bot_pid}" 2>/dev/null || true
bot_pid=''

data_file="${SERVER_DIR}/world/data/antifullbright_warnings.dat"
if [[ ! -s "${data_file}" ]]; then
    echo "Expected warning SavedData file was not written: ${data_file}" >&2
    find "${SERVER_DIR}/world" -maxdepth 3 -type f -print >&2 || true
    exit 1
fi
sha256sum "${data_file}" | tee "${DATA_DIGEST}"

start_server "${SECOND_LOG}"
start_bot "${SECOND_BOT_LOG}"
wait_log "${SECOND_LOG}" "${PLAYER} joined the game" 120
send_console "darkmining status ${PLAYER}"
wait_log "${SECOND_LOG}" "(warningLevel=${EXPECTED_WARNING_LEVEL}|警告レベル=${EXPECTED_WARNING_LEVEL})" 60
stop_server_gracefully "${SECOND_LOG}"
wait "${bot_pid}" 2>/dev/null || true
bot_pid=''

if grep -Eq 'NoClassDefFoundError|ClassNotFoundException|ModLoadingException|ModLoadingCrashException|Failed to load registries' "${FIRST_LOG}" "${SECOND_LOG}"; then
    echo "Restart persistence logs contain a loading failure." >&2
    tail -n 260 "${FIRST_LOG}" >&2
    tail -n 260 "${SECOND_LOG}" >&2
    exit 1
fi

echo "Real restart persistence verified for ${PLAYER}: warning level ${EXPECTED_WARNING_LEVEL}"
grep -E "(warning level to ${EXPECTED_WARNING_LEVEL}|警告レベルを ${EXPECTED_WARNING_LEVEL} に設定)" "${FIRST_LOG}" | tail -n 1
grep -E "(warningLevel=${EXPECTED_WARNING_LEVEL}|警告レベル=${EXPECTED_WARNING_LEVEL})" "${SECOND_LOG}" | tail -n 1
grep -F 'Done (' "${FIRST_LOG}" | tail -n 1
grep -F 'Done (' "${SECOND_LOG}" | tail -n 1
