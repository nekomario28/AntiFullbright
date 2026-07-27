# Anti Fullbright

[English documentation](README.md)

Minecraft 1.21.1 / NeoForge 21.1.235 向けの Mod です。

- サーバー側機能は Fullbright の使用を断定せず、プレイヤー本人による「完全な暗所での長時間採掘」を段階的に警告します。
- 任意導入のクライアント側betaスキャナーは、Client Setup時にローカルMODとリソースパックを検査し、起動後のリソースパック変更も監視します。

Mod ID は `antifullbright` です。クライアントスキャナーは現在 `1.1.0-beta.1` 段階であり、改変不能なアンチチートではありません。

## ビルドと導入

Java 21 を使用します。

```bash
./gradlew build
```

beta成果物は `build/libs/antifullbright-1.1.0-beta.1.jar` です。

- サーバー側の暗所採掘検知には、サーバーの `mods` ディレクトリへ導入します。
- ローカルスキャナーが必要な場合だけ、同じJARをクライアントの `mods` にも導入します。

サーバー側検知はクライアントへ導入しなくても動作します。このbetaには、スキャナーの導入や検査結果の真正性を証明するサーバーハンドシェイクはありません。

## サーバー側の検出動作

- `BlockEvent.BreakEvent` のうち、実プレイヤーが対象タグの自然ブロックを破壊した場合だけを記録します。
- FakePlayer、クリエイティブ、スペクテイター、既定ではOP、暗視効果中、目の位置が水中のプレイヤーを除外します。
- プレイヤーの目と破壊位置の両方で、ブロック光・天空光が設定値と一致する必要があります。
- 未ロードチャンクの光は読みません。
- 既定では60秒と20ブロックの両方を満たした時だけ警告します。警告後はセッションをリセットします。
- 最後の対象ブロック破壊から10秒経過、明所への移動、死亡、ログアウト、ディメンション移動、テレポート、発光ブロック設置でセッションをリセットします。
- 手に持ったタグ付き光源の猶予はセッション冒頭の20秒だけです。猶予後は所持していてもカウントします。
- Create等の機械やDeployerによる破壊は、プレイヤーのBreakEvent以外またはFakePlayerとして除外されます。通常プレイヤーが他Modの道具を使う場合は通常どおり判定します。
- 最近プレイヤー自身が設置したブロックは、期限付き・UUIDごとの件数上限付きで記憶して再破壊を除外します。チャンクアンロード時にも該当記録を破棄します。

状態確認はセッション中プレイヤーだけを20 tickごとに行います。周囲探索や強制チャンクロードは行いません。

## サーバー設定

初回起動後の `config/antifullbright-server.toml` で全項目を変更できます。

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

### 表示言語

警告、キック理由、OP通知、管理コマンドの結果は `language` で切り替えます。

```toml
# English
language = "en_us"

# 日本語
language = "ja_jp"
```

変更後に `/darkmining reload` を実行してください。サーバー側検知では、サーバーが翻訳済みの文章を送信します。

## クライアントスキャナーbeta

検査は `FMLClientSetupEvent` で実行します。通常のクライアント起動完了を阻止できますが、検査前に他MODの初期化コードが一切動かないことまでは保証しません。

`mods` 直下の `.jar`／`.zip`、`resourcepacks` 直下のZIP形式／展開済みフォルダ、設定されたハッシュを検査します。

### 強制ブロック判定

次は強制ブロックになります。

- `blockedModIds` に登録した正確なMod ID
- `blockedModSha256` または `blockedResourcePackSha256` に登録したSHA-256
- `blockedResourcePackPaths` に登録したリソースパック内部パス
- `failClosed = true` のときの読込不能、壊れたアーカイブ、上限超過

実行中のAntiFullbright本体は、実際のコードソースパスと完全一致するファイルだけを除外します。別のJARが同じMod IDを名乗ってもスキャン回避にはなりません。

### 警告判定

`suspiciousModTokens` と `suspiciousResourcePackTokens` は警告だけを生成します。例えば「fullbright互換機能を無効化する」という無害な説明にも同じ単語が含まれるため、曖昧な部分一致だけでは起動を止めません。

### リソースパック変更監視

`watchResourcePacks = true` の場合、Java `WatchService` で `resourcepacks` 以下を再帰監視します。

- 作成・変更・削除・OVERFLOW後に、デバウンス付きの全体再スキャンを実行
- 新しく作られたサブディレクトリも監視対象へ登録
- `resourcepacks` 自体が削除・再作成された場合も親ディレクトリから監視を復旧
- 実行中に強制ブロックを検出した場合はMinecraftのメインスレッドへ処理を渡す
- 通常の切断経路で現在のワールドを離れ、ブロック理由画面を表示
- JVMを直接強制終了しない

`WatchService`は変更通知の補助であり、完全なセキュリティ境界ではありません。後続フェーズでは、サーバー接続直前とリソースパック選択変更後にも再スキャンする必要があります。

## クライアント設定

初回クライアント起動後の `config/antifullbright-client.toml` で変更できます。

- `enabled`
- `scanMods`
- `scanResourcePacks`
- `watchResourcePacks`
- `failClosed`
- `disconnectOnRuntimeDetection`
- `watchDebounceMillis`
- `maximumArchiveEntries`
- `maximumTextBytes`
- `blockedModIds`
- `suspiciousModTokens`
- `suspiciousResourcePackTokens`
- `blockedResourcePackPaths`
- `blockedModSha256`
- `blockedResourcePackSha256`

識別子・トークンはカンマ区切りで、英字の大文字・小文字を区別しません。ハッシュは64桁の16進SHA-256で、任意で `sha256:` を先頭につけられます。

### プライバシーと強制範囲

このbetaは、検査したファイル内容、ファイル名、ローカルパス、ハッシュ、判定結果をネットワーク送信しません。判定はクライアント内で完結し、結果はローカルログとブロック画面にだけ表示されます。

ローカルログやクラッシュレポートには対象ファイルのパスが含まれる場合があります。第三者へ共有する前に、ユーザー名やホームディレクトリ等を含むパスを確認・伏せ字化してください。

この設定はプレイヤーが管理するローカル設定です。現段階のサーバーは有効化状態やポリシー内容を固定・検証できないため、サーバー強制型アンチチートとしては扱いません。

## データパック用タグ

- 光源アイテム: `antifullbright:dark_mining_light_sources`
- 採掘カウント対象: `antifullbright:dark_mining_counted_blocks`

組み込みタグはそれぞれ次の場所です。

- `data/antifullbright/tags/item/dark_mining_light_sources.json`
- `data/antifullbright/tags/block/dark_mining_counted_blocks.json`

同じIDのタグをデータパックから追加できます。NeoForgeの `remove` 配列を使えば組み込み対象の削除もできます。他Modの要素は `{ "id": "othermod:item", "required": false }` の形式を推奨します。

## 管理コマンド

すべて権限レベル2以上が必要です。

```text
/darkmining status <player>
/darkmining reset <player>
/darkmining setwarning <player> <count>
/darkmining reload
/darkmining debug <player>
```

`reset` は警告と現在セッションをリセットします。`debug` はY、目・足元の光、セッション時間、破壊数、光源所持猶予、警告状態、現在の除外理由を表示します。

## 永続化とログ

警告回数と最終警告時刻はUUIDをキーにOverworldのSavedData (`antifullbright_warnings.dat`) へ保存されます。既定では新しい警告が30分なければ、参照時または次回警告時に経過時間分だけ段階的に減衰します。

警告・キックの証拠は通常ロガーへ出し、`logs/dark-mining-detections.jsonl` に1イベント1行で非同期追記します。

## セキュリティ上の限界

クライアントスキャナーは改変不能ではありません。

- プレイヤーはスキャナーを削除・改変できます。
- 未知の実装は正確なID・内部パス・既知ハッシュを回避する可能性があります。
- 通常のサーバーMODだけでは、クライアントが管理する情報を完全には信頼できません。
- 厳格な運用では、専用ランチャー、署名済みマニフェスト、既存のサーバー側行動検知を併用してください。

通常のランチャー管理下にあるNeoForgeクライアントプロファイルで生成JARを確認し、アイコンと最終ポリシーを確定するまで、`1.1.0`正式版として公開しません。
