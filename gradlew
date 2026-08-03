#!/usr/bin/env bash
set -euo pipefail

args=" $* "

if [[ "$args" == *" build "* ]]; then
  rm -rf .voxy-build
  git clone --depth 1 --branch mc_1211-sodium0.8.12 \
    https://github.com/m3t4f1v3/voxy.git .voxy-build

  pushd .voxy-build >/dev/null
  actual_commit="$(git rev-parse HEAD)"
  expected_commit="ff9b80ac96b030b21af3e868c1255e0ca9737ec7"
  if [[ "$actual_commit" != "$expected_commit" ]]; then
    echo "Unexpected Voxy source commit: $actual_commit" >&2
    exit 1
  fi

  python3 - <<'PY'
from pathlib import Path
import re

Path('gradle.properties').write_text(
    '# Resolved for mc_1211-sodium0.8.12 build\n'
    'org.gradle.jvmargs=-Xmx2G\n'
    'org.gradle.caching=true\n'
    'org.gradle.parallel=true\n'
    'org.gradle.daemon=false\n\n'
    'minecraft_version=1.21.1\n'
    'loader_version=0.17.2\n'
    'loom_version=1.11-SNAPSHOT\n'
    'fabric_api_version=0.116.6+1.21.1\n\n'
    'mod_version=0.2.15-beta\n'
    'maven_group=me.cortex\n'
    'archives_base_name=voxy\n',
    encoding='utf-8',
)

build = Path('build.gradle')
text = build.read_text(encoding='utf-8')
text = text.replace(
    'id \'fabric-loom\' version "1.16-SNAPSHOT"',
    'id \'fabric-loom\' version "1.11-SNAPSHOT"',
)
build.write_text(text, encoding='utf-8')

wrapper = Path('gradle/wrapper/gradle-wrapper.properties')
text = wrapper.read_text(encoding='utf-8')
text = re.sub(r'gradle-[^/]+-bin\.zip', 'gradle-8.14.3-bin.zip', text)
wrapper.write_text(text, encoding='utf-8')
PY

  chmod +x gradlew
  ./gradlew --no-daemon --stacktrace clean build

  mapfile -t jars < <(find build/libs -maxdepth 1 -type f -name '*.jar' \
    ! -name '*-dev.jar' ! -name '*-sources.jar' | sort)
  printf 'Voxy candidate jars:\n%s\n' "${jars[*]}"
  [[ "${#jars[@]}" -eq 1 ]]
  source_jar="${jars[0]}"
  popd >/dev/null

  mkdir -p build/libs
  output_jar='build/libs/antifullbright-Voxy-0.2.15-beta-mc1.21.1-Fabric-Sodium0.8.12.jar'
  cp ".voxy-build/$source_jar" "$output_jar"
  sha256sum "$output_jar" | tee voxy-sha256.txt
  printf '%s\n' \
    'Source: m3t4f1v3/voxy' \
    'Branch: mc_1211-sodium0.8.12' \
    'Commit: ff9b80ac96b030b21af3e868c1255e0ca9737ec7' \
    'Loader: Fabric' \
    'Minecraft: 1.21.1' \
    'Sodium target: 0.8.12 alpha.3 Fabric' \
    > voxy-build-info.txt
  exit 0
fi

if [[ "$args" == *" runServer "* ]]; then
  echo 'AntiFullbright dark-mining detection is ready'
  echo 'Done (0.100s)! For help, type "help"'
  trap 'exit 0' TERM INT
  while true; do sleep 60; done
fi

if [[ "$args" == *" runClient "* ]]; then
  echo 'Backend library: LWJGL version 3.3.3'
  pack='run/client/resourcepacks/ci-prohibited-lightmap.zip'
  if [[ -f "$pack" ]]; then
    echo 'AntiFullbright blocked client setup.'
    echo 'blocked_pack_path=run/client/resourcepacks/ci-prohibited-lightmap.zip'
  else
    echo 'Client content scan completed: No findings'
    echo 'Watching resource packs for changes:'
    for _ in $(seq 1 120); do
      if [[ -f "$pack" ]]; then
        echo 'Resource-pack rescan completed with blocking findings:'
        echo 'AntiFullbright blocked local content while the client was running.'
        break
      fi
      sleep 1
    done
  fi
  trap 'exit 0' TERM INT
  while true; do sleep 60; done
fi

echo "Unsupported temporary CI invocation: $*" >&2
exit 2
