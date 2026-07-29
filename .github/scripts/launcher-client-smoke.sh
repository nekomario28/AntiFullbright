#!/usr/bin/env bash
set -euo pipefail

readonly MINECRAFT_VERSION="1.21.1"
readonly NEOFORGE_VERSION="21.1.235"
readonly MOD_VERSION="1.1.0-beta.1"
readonly PROFILE_ROOT="packaged-client-profile"
readonly MAIN_DIR="${PROFILE_ROOT}/main"
readonly WORK_DIR="${PROFILE_ROOT}/instance"
readonly PORTABLEMC="${PORTABLEMC_BIN:-.portablemc-venv/bin/portablemc}"
readonly JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/java}"
readonly CLEAN_LOG="launcher-client-clean.log"
readonly BLOCK_LOG="launcher-client-startup-block.log"
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

rm -rf "${PROFILE_ROOT}"
rm -f "${CLEAN_LOG}" "${BLOCK_LOG}" "${MOD_DIGEST}"
mkdir -p "${MAIN_DIR}" "${WORK_DIR}/mods" "${WORK_DIR}/resourcepacks"
cp "${mod_jar}" "${WORK_DIR}/mods/"
sha256sum "${mod_jar}" | tee "${MOD_DIGEST}"

launch_client() {
    local log_file="$1"
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
        "neoforge:${MINECRAFT_VERSION}-${NEOFORGE_VERSION}" \
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

wait_for_markers() {
    local process_id="$1"
    local log_file="$2"
    local mode="$3"
    local ready=0

    for _ in $(seq 1 480); do
        if [[ "${mode}" == clean ]]; then
            if grep -Fq 'Backend library: LWJGL version' "${log_file}" 2>/dev/null \
                && grep -Fq 'Client content scan completed: No findings' "${log_file}" 2>/dev/null \
                && grep -Fq 'Watching resource packs for changes:' "${log_file}" 2>/dev/null; then
                ready=1
                break
            fi
        else
            if grep -Fq 'AntiFullbright blocked client setup.' "${log_file}" 2>/dev/null \
                && grep -Fq 'blocked_pack_path' "${log_file}" 2>/dev/null; then
                ready=1
                break
            fi
        fi

        if ! kill -0 "${process_id}" 2>/dev/null; then
            break
        fi
        sleep 1
    done

    stop_client "${process_id}"

    if [[ "${ready}" -ne 1 ]]; then
        echo "Launcher client did not reach the required ${mode} markers." >&2
        tail -n 320 "${log_file}" >&2 || true
        exit 1
    fi

    if grep -Eq 'NoClassDefFoundError|ClassNotFoundException|ModLoadingException|ModLoadingCrashException|Failed to create window|GLFW error|Error starting SoundSystem' "${log_file}"; then
        echo "Launcher client log contains an unrelated runtime failure." >&2
        tail -n 320 "${log_file}" >&2
        exit 1
    fi
}

clean_pid="$(launch_client "${CLEAN_LOG}")"
wait_for_markers "${clean_pid}" "${CLEAN_LOG}" clean

python3 - <<'PY'
from pathlib import Path
import zipfile

target = Path('packaged-client-profile/instance/resourcepacks/ci-prohibited-lightmap.zip')
with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('pack.mcmeta', '{"pack":{"pack_format":34,"description":"AntiFullbright external launcher fixture"}}')
    archive.writestr('assets/minecraft/optifine/lightmap/world0.png', b'external-launcher-fixture')
PY

block_pid="$(launch_client "${BLOCK_LOG}")"
wait_for_markers "${block_pid}" "${BLOCK_LOG}" blocked

echo "PortableMC external profile: neoforge:${MINECRAFT_VERSION}-${NEOFORGE_VERSION}"
grep -F 'Backend library: LWJGL version' "${CLEAN_LOG}" | tail -n 1
grep -F 'Client content scan completed: No findings' "${CLEAN_LOG}" | tail -n 1
grep -F 'Watching resource packs for changes:' "${CLEAN_LOG}" | tail -n 1
grep -F 'AntiFullbright blocked client setup.' "${BLOCK_LOG}" | tail -n 1
grep -F 'blocked_pack_path' "${BLOCK_LOG}" | tail -n 1
