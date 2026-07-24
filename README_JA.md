# Anti Fullbright

[English documentation](README.md)

Minecraft 1.21.1 / NeoForge 21.1.235 向けの Mod です。

- サーバーでは、Fullbright の使用を断定せず、プレイヤー本人による「完全な暗所での長時間採掘」を段階的に警告します。
- クライアントへ導入した場合は、起動時にローカルの `mods` と `resourcepacks` を検査し、設定された Fullbright シグネチャを検出すると起動を失敗させます。
- 起動後は `resourcepacks` 以下を再帰監視し、作成・変更・削除後にフォルダ全体を再検査します。

Mod ID は `antifullbright` です。バージョン 1.1.0 からクライアントスキャナーが追加されています。

## ビルドと導入

Java 21 を使用します。

```bash
./gradlew build
```

生成されるファイルは `build/libs/antifullbright-1.1.0.jar` です。

- 暗所採掘のサーバー検知だけを利用する場合は、サーバーの `mods` に導入します。
- ローカルMOD・リソースパック検査を利用するプレイヤーは、同じJARをクライアントの `mods` にも導入します。

クライアントスキャナーはローカルで動作します。現段階では、サーバーがスキャナーの導入や検査結果を暗号学的に証明させる通信ハンドシェイクは実装していません。

## クライアントスキャナー

クライアント起動時、設定が有効なら次を検査します。

### MOD

`mods` 直下の `.jar` と `.zip` を対象に、次を確認します。

- ファイル名
- アーカイブ内のパス
- `META-INF/neoforge.mods.toml`
- `META-INF/mods.toml`
- `fabric.mod.json`
- `quilt.mod.json`
- 設定された SHA-256

実行中の AntiFullbright 本体は、MOD ID ではなく実際のコードソースJARの絶対パスでのみ除外します。別のMODが `antifullbright` を名乗っても自動的には除外されません。

### リソースパック

`resourcepacks` 直下のZIP形式と展開済みフォルダを対象に、次を確認します。

- パック名
- `pack.mcmeta`
- アーカイブまたはフォルダ内のパス
- 設定された SHA-256

既定では次のライトマップ上書きを禁止シグネチャとして扱います。

```text
assets/minecraft/optifine/lightmap/
assets/minecraft/mcpatcher/lightmap/
assets/minecraft/shaders/core/lightmap
```

違反または、`failClosed = true` の状態で読めないアーカイブを検出すると、クライアントセットアップ中に例外を発生させてModロードを失敗させます。

### 変更監視

起動時検査を通過した後、`watchResourcePacks = true` なら Java `WatchService` で `resourcepacks` 以下を再帰監視します。

- 作成・変更・削除を監視
- 新しく作られたサブディレクトリも監視対象へ追加
- 連続イベントを `watchDebounceMillis` でまとめる
- `OVERFLOW` を含む変更後はパック全体を再検査
- 違反検出時は既定で終了コード `23` によりクライアントを終了

## クライアント設定

初回クライアント起動後の `config/antifullbright-client.toml` で変更できます。

- `enabled`
- `scanMods`
- `scanResourcePacks`
- `watchResourcePacks`
- `failClosed`
- `exitOnRuntimeDetection`
- `watchDebounceMillis`
- `maximumArchiveEntries`
- `maximumTextBytes`
- `blockedModTokens`
- `blockedResourcePackTokens`
- `blockedResourcePackPaths`
- `blockedModSha256`
- `blockedResourcePackSha256`

トークンとハッシュはカンマ区切りです。比較は英字の大文字・小文字を区別しません。ハッシュは64桁の16進SHA-256で、任意で `sha256:` を先頭につけられます。

## サーバー側の暗所採掘検知

- `BlockEvent.BreakEvent` のうち、実プレイヤーが対象タグの自然ブロックを破壊した場合だけを記録します。
- FakePlayer、クリエイティブ、スペクテイター、既定では OP、暗視効果中、水中のプレイヤーを除外します。
- プレイヤーの目と破壊位置の両方で、ブロック光・天空光が設定値と一致する必要があります。
- 未ロードチャンクの光は読みません。
- 既定では60秒と20ブロックの両方を満たした時だけ警告します。警告後はセッションをリセットします。
- 最後の対象ブロック破壊から10秒経過、明所への移動、死亡、ログアウト、ディメンション移動、テレポート、発光ブロック設置でセッションをリセットします。
- 手に持ったタグ付き光源の猶予はセッション冒頭の20秒だけです。
- Create等の機械やDeployerによる破壊は、プレイヤーのBreakEvent以外またはFakePlayerとして除外されます。
- 最近プレイヤー自身が設置したブロックは、期限付き・UUIDごとの件数上限付きで記憶して再破壊を除外します。

状態確認はセッション中プレイヤーだけを20 tickごとに行います。周囲探索や強制チャンクロードは行いません。

## サーバー設定

初回サーバー起動後の `config/antifullbright-server.toml` で変更できます。

- `language`（`ja_jp` または `en_us`。既定値 `en_us`）
- `enabled`
- `maximumY`
- `requiredBlockLight`
- `requiredSkyLight`
- `continuousMiningSeconds`
- `minimumBlocks`
- `inactivityResetSeconds`
- `torchHoldingGraceSeconds`
- `warningsBeforeKick`
- `warningDecayMinutes`
- `excludeOperators`
- `excludeNightVision`
- `excludeUnderwater`
- `notifyOperatorsAtWarning`
- `persistWarnings`
- `enableDedicatedLog`
- `placedBlockTrackingEnabled`
- `placedBlockTrackingExpirationMinutes`
- `placedBlockTrackingMaximumEntriesPerPlayer`

`/darkmining reload` はこのファイルを同期的に再読込します。値は定義済みの安全な範囲へ制限されます。

## データパック用タグ

- 光源アイテム: `antifullbright:dark_mining_light_sources`
- 採掘カウント対象: `antifullbright:dark_mining_counted_blocks`

組み込みタグは次の場所です。

- `data/antifullbright/tags/item/dark_mining_light_sources.json`
- `data/antifullbright/tags/block/dark_mining_counted_blocks.json`

## 管理コマンド

すべて権限レベル2以上が必要です。

```text
/darkmining status <player>
/darkmining reset <player>
/darkmining setwarning <player> <count>
/darkmining reload
/darkmining debug <player>
```

## 永続化とログ

警告回数と最終警告時刻はUUIDをキーにOverworldのSavedData (`antifullbright_warnings.dat`) へ保存されます。警告・キックの証拠は通常ロガーと `logs/dark-mining-detections.jsonl` へ記録します。

## セキュリティ上の限界

この機能は一般的なNeoForgeクライアントMODであり、改造されたクライアントに対する完全なアンチチートではありません。

- プレイヤーはスキャナーMOD自体を削除・改変できます。
- 名称・メタデータ・パス・既知ハッシュに一致しない独自実装は検出できない場合があります。
- クライアント自身が報告する情報を、通常のサーバーMODだけで完全には信頼できません。
- 厳格な運用には、配布物を固定する専用ランチャー、署名済みマニフェスト、サーバー側の行動検知を併用してください。

既存のサーバー側暗所採掘検知は、クライアント検査が回避された場合の補助的な検出として残しています。
