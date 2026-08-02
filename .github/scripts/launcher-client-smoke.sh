#!/usr/bin/env bash
set -euo pipefail

readonly MINECRAFT_VERSION="1.21.1"
readonly NEOFORGE_VERSION="21.1.235"
readonly MOD_VERSION="1.1.0"
readonly PROFILE_ROOT="packaged-client-profile"
readonly MAIN_DIR="${PROFILE_ROOT}/main"
readonly WORK_DIR="${PROFILE_ROOT}/instance"
readonly PORTABLEMC="${PORTABLEMC_BIN:-.portablemc-venv/bin/portablemc}"
readonly JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/java}"
readonly CLEAN_LOG="launcher-client-clean.log"
readonly CLEAN_GAME_LOG="launcher-client-clean-game.log"
readonly BLOCK_LOG="launcher-client-startup-block.log"
readonly BLOCK_GAME_LOG="launcher-client-startup-block-game.log"
readonly MOD_DIGEST="launcher-client-mod.sha256"

mod_jar="build/libs/antifullbright-${MOD_VERSION}.jar"
if [[ ! -f "${mod_jar}" ]]; then
    echo "Expected packaged mod JAR was not found: ${mod_jar}" >&2
    exit 1
fi
if [[ ! -x "${PORTABLEMC}" ]]; then
    echo "PortableMC executable was not found: ${PORTABLEMC}" >&2
    exit 1
fi
if [[ -z "${JAVA_BIN}" || ! -x "${JAVA_BIN}" ]]; then
    echo "JAVA_HOME does not point to a Java executable." >&2
    exit 1
fi

# Keep MAIN_DIR cacheable across runs, but always recreate the isolated game instance.
rm -rf "${WORK_DIR}"
rm -f "${CLEAN_LOG}" "${CLEAN_GAME_LOG}" "${BLOCK_LOG}" "${BLOCK_GAME_LOG}" "${MOD_DIGEST}"
mkdir -p "${MAIN_DIR}" "${WORK_DIR}/mods" "${WORK_DIR}/resourcepacks"
cp "${mod_jar}" "${WORK_DIR}/mods/"
sha256sum "${mod_jar}" | tee "${MOD_DIGEST}"

launch_client() {
    local log_file="$1"
    rm -f "${WORK_DIR}/logs/latest.log"
    setsid env -u DISPLAY -u XAUTHORITY \
        LIBGL_ALWAYS_SOFTWARE=1 \
        ALSOFT_DRIVERS=null \
        PYTHONUNBUFFERED=1 \
        xvfb-run -a -s '-screen 0 1280x720x24 -ac' \
        "${PORTABLEMC}" \
        --main-dir "${MAIN_DIR}" \
        --work-dir "${WORK_DIR}" \
        --timeout 120 \
        start \
        --username AntiFBLauncher \
        --uuid 4e790de5-19df-3a76-9f1c-0fce3c61e978 \
        --jvm "${JAVA_BIN}" \
        '--jvm-args=-Xmx2G -XX:+UseG1GC' \
        "neoforge:${NEOFORGE_VERSION}" \
        </dev/null > "${log_file}" 2>&1 &
    echo $!
}

stop_client() {
    local process_id="$1"
    kill -TERM -- "-${process_id}" 2>/dev/null || true
    sleep 3
    kill -KILL -- "-${process_id}" 2>/dev/null || true
    wait "${process_id}" 2>/dev/null || true
}

has_marker() {
    local launcher_log="$1"
    local pattern="$2"
    grep -Fq "${pattern}" "${launcher_log}" 2>/dev/null \
        || grep -Fq "${pattern}" "${WORK_DIR}/logs/latest.log" 2>/dev/null
}

wait_for_markers() {
    local process_id="$1"
    local launcher_log="$2"
    local copied_game_log="$3"
    local mode="$4"
    local ready=0

    for _ in $(seq 1 360); do
        if [[ "${mode}" == clean ]]; then
            if has_marker "${launcher_log}" 'Backend library: LWJGL version' \
                && has_marker "${launcher_log}" 'Client content scan completed: No findings' \
                && has_marker "${launcher_log}" 'Watching resource packs for changes:'; then
                ready=1
                break
            fi
        else
            if has_marker "${launcher_log}" 'AntiFullbright blocked client setup.' \
                && has_marker "${launcher_log}" 'blocked_pack_path'; then
                ready=1
                break
            fi
        fi

        if ! kill -0 "${process_id}" 2>/dev/null; then
            break
        fi
        sleep 1
    done

    if [[ -f "${WORK_DIR}/logs/latest.log" ]]; then
        cp "${WORK_DIR}/logs/latest.log" "${copied_game_log}"
    fi
    stop_client "${process_id}"

    if [[ "${ready}" -ne 1 ]]; then
        echo "Launcher client did not reach the required ${mode} markers." >&2
        echo '--- launcher output ---' >&2
        tail -n 200 "${launcher_log}" >&2 || true
        echo '--- instance latest.log ---' >&2
        tail -n 320 "${copied_game_log}" >&2 || true
        exit 1
    fi

    if grep -Ehq 'NoClassDefFoundError|ClassNotFoundException|ModLoadingException|ModLoadingCrashException|Failed to create window|GLFW error|Error starting SoundSystem' \
        "${launcher_log}" "${copied_game_log}" 2>/dev/null; then
        echo "Launcher client logs contain an unrelated runtime failure." >&2
        tail -n 200 "${launcher_log}" >&2 || true
        tail -n 320 "${copied_game_log}" >&2 || true
        exit 1
    fi
}

clean_pid="$(launch_client "${CLEAN_LOG}")"
wait_for_markers "${clean_pid}" "${CLEAN_LOG}" "${CLEAN_GAME_LOG}" clean

python3 - <<'PY'
from pathlib import Path
import zipfile

target = Path('packaged-client-profile/instance/resourcepacks/ci-prohibited-lightmap.zip')
with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('pack.mcmeta', '{"pack":{"pack_format":34,"description":"AntiFullbright external launcher fixture"}}')
    archive.writestr('assets/minecraft/optifine/lightmap/world0.png', b'external-launcher-fixture')
PY

block_pid="$(launch_client "${BLOCK_LOG}")"
wait_for_markers "${block_pid}" "${BLOCK_LOG}" "${BLOCK_GAME_LOG}" blocked

echo "PortableMC external profile: Minecraft ${MINECRAFT_VERSION}, NeoForge ${NEOFORGE_VERSION}"
grep -F 'Backend library: LWJGL version' "${CLEAN_GAME_LOG}" | tail -n 1
grep -F 'Client content scan completed: No findings' "${CLEAN_GAME_LOG}" | tail -n 1
grep -F 'Watching resource packs for changes:' "${CLEAN_GAME_LOG}" | tail -n 1
grep -F 'AntiFullbright blocked client setup.' "${BLOCK_GAME_LOG}" | tail -n 1
grep -F 'blocked_pack_path' "${BLOCK_GAME_LOG}" | tail -n 1
