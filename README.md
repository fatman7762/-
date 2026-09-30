# 株式会社ライトパス 総合受付システム

キオスク用 Android アプリと、受付通知用 Node.js API です。

## 構成

- `api/` … Express API（受付保存・Slack 通知）
- `android/` … Jetpack Compose + Material 3 キオスク画面

## UI（Android）

画面は**ボタンだけ**です。見出し・会社名・案内文・「受付する」ボタンはありません。

### 操作（2回押しで成立）

1. 担当者（野坂 Nosaka / 伊藤 Ito / 梁瀬 VTuber / 中原 dismissed / 坂本 Sakamoto / 合田 gouda / その他 Other）を押す  
2. 人数（1〜5 / 6～）を押す  
→ その瞬間に API へ送信。選択は短いフラッシュ後に解除され、次の来客待ちへ戻ります。名前は大きめ表示、下にローマ字を小さく併記します。その他の横の空きには現在時刻のポップアウトを表示します。

人数を先に押してから担当者を押しても同じです。1回だけでは送信しません。

### レイアウト（タブレット縦横両対応）

実機はタブレット想定です。回転してもスクロールなしで画面いっぱいに再配置します。

```
【縦向き】                        【横向き】
┌─────────┬─────────┐          ┌─────────┬─────────┬────┐
│ 野坂    │ 伊藤    │          │ 野坂    │ 伊藤    │ 1  │
│ Nosaka  │ Ito     │          │ Nosaka  │ Ito     │    │
├─────────┼─────────┤          ├─────────┼─────────┼────┤
│ 梁瀬    │ 中原    │          │ 梁瀬    │ 中原    │ 2  │
├─────────┼─────────┤          ├─────────┼─────────┼────┤
│ 坂本    │ 合田    │          │ 坂本    │ 合田    │ … │
├─────────┼─────────┤          ├─────────┼─────────┼────┤
│ その他  │ 12:34:56│          │ その他  │ 時刻    │6～ │
├─────────┴─────────┤          └─────────┴─────────┴────┘
│ 1  2  3  4  5  6～ │
└───────────────────┘
```

- `fullSensor` で縦横どちらでも利用可（回転時にレイアウト切替）
- 文字サイズは画面サイズに合わせて自動調整
- スクロールなし（`weight` で画面いっぱい）
- 常時点灯（`FLAG_KEEP_SCREEN_ON`）
- 選択中: 青 / 受付成功: 緑フラッシュ（梁瀬・中原以外）＋ Intercom01 Ding Dong 音 / 通信失敗: 赤フラッシュ
- 中原は赤のまま「クビ」、人数ボタンも赤、成功音は Explosion01 Short / 梁瀬は黄のまま「おかえりなさい」、人数ボタンも黄、成功音は Onoma-Sparkle03 Short
- **レイアウト編集**: 時刻を長押しで編集モード。ボタン右上の − で削除、空きの ＋ で追加、タップ同士で入れ替え。名前／人数は編集モードで長押しして変更。時刻・梁瀬・中原は削除不可
- Android Studio Preview「タブレット縦」「タブレット横」で確認できます

**Slack 通知は任意**です。Webhook 未設定でも受付は成立します（あとから `.env` を足せば有効化できます）。会社名「株式会社ライトパス」は Slack 通知文側の文言です。

## API の起動

```bash
cd api
cp .env.example .env   # Slack Webhook 等を記入
npm install
npm start              # 既定ポート 43123
```

ヘルスチェック:

```bash
curl http://127.0.0.1:43123/health
```

ブラウザで操作テスト（Android と同じ 2 タップ UI）:

```text
http://127.0.0.1:43123/
```

表示がおかしいときはレイアウトを初期化:

```text
http://127.0.0.1:43123/?reset=1
```

自動テスト:

```bash
cd api
npm test
```

### Docker

```bash
cd api
docker build -t lightpath-reception-api .
docker run --rm -p 8081:8081 -v reception-data:/app/data --env-file .env lightpath-reception-api
```

### エンドポイント

| Method | Path | 説明 |
|--------|------|------|
| GET | `/health` | 稼働確認 |
| POST | `/receptions` | 受付登録 `{ staffName, partySize }` |
| GET | `/receptions` | 当日の受付一覧 |

担当者（`staffName`）: `野坂` / `伊藤` / `梁瀬` / `中原` / `坂本` / `合田` / `その他`  
人数（`partySize`）: 整数 `1`〜`6`（画面上の「6～」は API 値 `6`）

成功時は `201` と `id`, `staffName`, `partySize`, `acceptedAt`, `notified` を返します。`SLACK_WEBHOOK_URL` が空なら通知はスキップされ `notified: false` のまま受付は保存されます。受付データは `RECEPTIONS_FILE`（既定 `data/receptions.json`）に保存されます。

## 環境変数

| 変数 | 説明 |
|------|------|
| `PORT` | API ポート（既定 `8081`） |
| `RECEPTIONS_FILE` | 受付データの JSON パス |
| `SLACK_WEBHOOK_URL` | Slack Incoming Webhook URL（テスト用 Slack でも可） |

### Slack Incoming Webhook の設定

1. Slack ワークスペースで Incoming Webhooks を有効化
2. 通知先チャンネルを選び Webhook URL を発行
3. `api/.env` の `SLACK_WEBHOOK_URL` に設定して API を再起動

通知文例（メンションは `@氏名` の文字のみ。Slack メンバー ID は使いません）:

```
株式会社ライトパス 総合受付
@野坂 さん、お客様が 2名 お見えです。
担当: 野坂
人数: 2名
受付時刻: 2026-09-30 10:15
```

中原のとき:

```
お前はクビだ！
@中原 2名
```

梁瀬のとき:

```
おかえりなさい
@梁瀬 2名
```

## Android アプリ

1. [Android Studio](https://developer.android.com/studio) で `android/` フォルダを Open
2. Gradle 同期を待つ
3. エミュレータまたは実機で実行（または Preview で UI 確認）

- パッケージ: `jp.co.lightpath.reception`
- 既定の API URL: `http://10.0.2.2:8081`（エミュレータからホスト側 API）

API URL の変更:

- `android/app/build.gradle.kts` の `buildConfigField("String", "API_BASE_URL", ...)`
- または `android/app/src/main/res/values/strings.xml` の `api_base_url`

実機から PC 上の API に繋ぐ場合は、PC の LAN IP（例: `http://192.168.x.x:8081`）に変更してください。
