#!/usr/bin/env bash
set -euo pipefail

readonly NEOFORGE_VERSION="21.1.235"
readonly MOD_VERSION="1.1.0-beta.1"
readonly INSTALLER="neoforge-${NEOFORGE_VERSION}-installer.jar"
readonly INSTALLER_BASE="https://maven.neoforged.net/releases/net/neoforged/neoforge/${NEOFORGE_VERSION}"
readonly SERVER_DIR="packaged-server"
readonly INSTALL_LOG="packaged-server-install.log"
readonly SERVER_LOG="packaged-server.log"
readonly MOD_DIGEST="packaged-server-mod.sha256"

mod_jar="build/libs/antifullbright-${MOD_VERSION}.jar"
if [[ ! -f "${mod_jar}" ]]; then
    echo "Expected packaged mod JAR was not found: ${mod_jar}" >&2
    exit 1
fi

rm -rf "${SERVER_DIR}"
rm -f "${INSTALLER}" "${INSTALLER}.sha256" "${INSTALL_LOG}" "${SERVER_LOG}" "${MOD_DIGEST}"
mkdir -p "${SERVER_DIR}/mods"

curl --fail --silent --show-error --location \
    "${INSTALLER_BASE}/${INSTALLER}" \
    --output "${INSTALLER}"
curl --fail --silent --show-error --location \
    "${INSTALLER_BASE}/${INSTALLER}.sha256" \
    --output "${INSTALLER}.sha256"\n
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
printf 'online-mode=false\nserver-port=25566\n' > "${SERVER_DIR}/server.properties"
chmod +x "${SERVER_DIR}/run.sh"

(
    cd "${SERVER_DIR}"
    exec setsid bash ./run.sh nogui
) > "${SERVER_LOG}" 2>&1 &
process_id=$!
ready=0

for _ in $(seq 1 240); do
    if grep -Fq 'Done (' "${SERVER_LOG}" 2>/dev/null \
        && grep -Fq 'AntiFullbright dark-mining detection is ready' "${SERVER_LOG}" 2>/dev/null; then
        ready=1
        break
    fi
    if ! kill -0 "${process_id}" 2>/dev/null; then
        break
    fi
    sleep 1
done

kill -TERM -- "-${process_id}" 2>/dev/null || true
sleep 3
kill -KILL -- "-${process_id}" 2>/dev/null || true
wait "${process_id}" 2>/dev/null || true

if [[ "${ready}" -ne 1 ]]; then
    echo "Packaged NeoForge server did not reach all readiness markers." >&2
    tail -n 240 "${SERVER_LOG}" >&2
    exit 1
fi

if grep -Eq 'NoClassDefFoundError|ClassNotFoundException|ModLoadingException|ModLoadingCrashException' "${SERVER_LOG}"; then
    echo "Packaged NeoForge server log contains a class-side or mod-loading failure." >&2
    tail -n 240 "${SERVER_LOG}" >&2
    exit 1
fi

if ! grep -Fq "Anti Fullbright" "${SERVER_LOG}" || ! grep -Fq "${MOD_VERSION}" "${SERVER_LOG}"; then
    echo "Packaged server log does not contain the expected mod name and version." >&2
    tail -n 240 "${SERVER_LOG}" >&2
    exit 1
fi

grep -F "Anti Fullbright" "${SERVER_LOG}" | tail -n 1
grep -F "AntiFullbright dark-mining detection is ready" "${SERVER_LOG}" | tail -n 1
grep -F "Done (" "${SERVER_LOG}" | tail -n 1
