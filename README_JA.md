# Anti Fullbright

[English documentation](README.md)

Minecraft 1.21.1 / NeoForge 21.1.235 向けのサーバー専用 Mod です。Fullbright の使用を断定せず、プレイヤー本人による「完全な暗所での長時間採掘」を段階的に警告します。

Mod ID は `antifullbright` です。ソース、タグ namespace、設定ファイル名、ログ用スレッド名、ビルド成果物名まで同じ ID に統一しています。

## ビルドと導入

Java 21 を使用します。

```bash
./gradlew build
```

生成された `build/libs/antifullbright-1.0.0.jar` を NeoForge 1.21.1 サーバーの `mods` ディレクトリへ入れてください。クライアント側への導入や通信 payload は不要です。

## 検出動作

- `BlockEvent.BreakEvent` のうち、実プレイヤーが対象タグの自然ブロックを破壊した場合だけを記録します。
- FakePlayer、クリエイティブ、スペクテイター、既定では OP と暗視効果中のプレイヤーを除外します。
- プレイヤーの目と破壊位置の両方で、ブロック光・天空光が設定値と一致する必要があります。
- 未ロードチャンクの光は読みません。
- 既定では 60 秒と 20 ブロックの両方を満たした時だけ警告します。警告後はセッションをリセットします。
- 最後の対象ブロック破壊から 10 秒経過、明所への移動、死亡、ログアウト、ディメンション移動、テレポート、発光ブロック設置でセッションをリセットします。
- 手に持ったタグ付き光源の猶予はセッション冒頭の 20 秒だけです。猶予後は所持していてもカウントします。
- Create 等の機械や Deployer による破壊は、プレイヤーの BreakEvent 以外または FakePlayer として除外されます。通常プレイヤーが他 Mod の道具を使う場合は通常どおり判定します。
- 最近プレイヤー自身が設置したブロックは、期限付き・UUID ごとの件数上限付きで記憶して再破壊を除外します。チャンクアンロード時にも該当記録を破棄します。

状態確認はセッション中プレイヤーだけを 20 tick ごとに行います。周囲探索や強制チャンクロードは行いません。

## 設定

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
- `excludeUnderwater`（目の位置が水中のプレイヤーを除外。既定値 `true`）
- `notifyOperatorsAtWarning`（OP 通知を開始する警告レベル。既定値 2）
- `persistWarnings`
- `enableDedicatedLog`
- `placedBlockTrackingEnabled`
- `placedBlockTrackingExpirationMinutes`
- `placedBlockTrackingMaximumEntriesPerPlayer`

`/darkmining reload` はこのファイルを同期的に再読込します。値は定義済みの安全な範囲へ制限されます。

### 表示言語

警告、キック理由、OP 通知、管理コマンドの結果は `language` で切り替えます。

```toml
# English
language = "en_us"

# 日本語
language = "ja_jp"
```

変更後に `/darkmining reload` を実行してください。この Mod はサーバーが翻訳済みの文章を送信するため、クライアント側への言語ファイルや Mod の導入は不要です。クライアント自身の言語設定による自動切り替えではなく、サーバー全体で共通の表示言語になります。

## データパック用タグ

- 光源アイテム: `antifullbright:dark_mining_light_sources`
- 採掘カウント対象: `antifullbright:dark_mining_counted_blocks`

組み込みタグはそれぞれ次の場所です。

- `data/antifullbright/tags/item/dark_mining_light_sources.json`
- `data/antifullbright/tags/block/dark_mining_counted_blocks.json`

同じ ID のタグをデータパックから追加できます。NeoForge の `remove` 配列を使えば組み込み対象の削除もできます。他 Mod の要素は `{ "id": "othermod:item", "required": false }` の形式を推奨します。

## 管理コマンド

すべて権限レベル 2 以上が必要です。

```text
/darkmining status <player>
/darkmining reset <player>
/darkmining setwarning <player> <count>
/darkmining reload
/darkmining debug <player>
```

`reset` は警告と現在セッションをリセットします。`debug` は Y、目・足元の光、セッション時間、破壊数、光源所持猶予、警告状態、現在の除外理由を表示します。

## 永続化とログ

警告回数と最終警告時刻は UUID をキーに Overworld の SavedData (`antifullbright_warnings.dat`) へ保存されます。既定では新しい警告が 30 分なければ、参照時または次回警告時に経過時間分だけ段階的に減衰します。

警告・キックの証拠は通常ロガーへ出し、`logs/dark-mining-detections.jsonl` に 1 イベント 1 行で非同期追記します。ワールドや Entity の値はサーバースレッドで不変レコードへ変換してから、専用の単一 writer thread でファイルへ書き込みます。
