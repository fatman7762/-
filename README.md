# 株式会社ライトパス 総合受付システム

キオスク用 Android アプリと、受付通知用 Node.js API です。

## 構成

- `api/` … Express API（受付保存・Slack 通知）
- `android/` … Jetpack Compose + Material 3 キオスク画面

## API の起動

```bash
cd api
cp .env.example .env   # 必要に応じて編集
npm install
npm start              # 既定ポート 8081
```

ヘルスチェック:

```bash
curl http://127.0.0.1:8081/health
```

テスト:

```bash
cd api
npm test
```

### Docker

```bash
cd api
docker build -t lightpath-reception-api .
docker run --rm -p 8081:8081 --env-file .env lightpath-reception-api
```

### エンドポイント

| Method | Path | 説明 |
|--------|------|------|
| GET | `/health` | 稼働確認 |
| POST | `/receptions` | 受付登録 `{ staffName, partySize }` |
| GET | `/receptions` | 当日の受付一覧 |

担当者（`staffName`）: `野坂` / `伊藤` / `梁瀬` / `中原` / `坂本` / `合田` / `その他`  
人数（`partySize`）: 整数 `1`〜`6`

成功時は `201` と `id`, `staffName`, `partySize`, `acceptedAt`, `notified` を返します。Slack 通知に失敗しても受付は保存され、`notified: false` になります。

## 環境変数

| 変数 | 説明 |
|------|------|
| `PORT` | API ポート（既定 `8081`） |
| `SLACK_WEBHOOK_URL` | Slack Incoming Webhook URL |
| `SLACK_MENTION_NOSAKA` | 野坂さんの Slack メンバー ID（例: `U0123...`） |
| `SLACK_MENTION_ITO` | 伊藤 |
| `SLACK_MENTION_YANASE` | 梁瀬 |
| `SLACK_MENTION_NAKAHARA` | 中原 |
| `SLACK_MENTION_SAKAMOTO` | 坂本 |
| `SLACK_MENTION_AIDA` | 合田 |

メンション ID が無い場合は「野坂 さん、…」のように氏名で通知します。`その他` にはメンション用変数はありません。

### Slack Incoming Webhook の設定

1. Slack ワークスペースで Incoming Webhooks を有効化
2. 通知先チャンネルを選び Webhook URL を発行
3. `.env` の `SLACK_WEBHOOK_URL` に設定
4. 必要なら各担当のメンバー ID を `SLACK_MENTION_*` に設定

通知文例:

```
株式会社ライトパス 総合受付
<@U...> さん、お客様が 2名 お見えです。
担当: 野坂
受付時刻: 2026-09-30 10:15
```

## Android アプリ

1. [Android Studio](https://developer.android.com/studio) で `android/` フォルダを Open
2. Gradle 同期を待つ
3. エミュレータまたは実機で実行

- パッケージ: `jp.co.lightpath.reception`
- 画面はボタンのみ。担当者または人数を選んだあと、もう一方を選ぶと即 API へ POST
- 成功時は選択ボタンが短く tertiary（緑系）にフラッシュ、Slack 失敗時は error（赤）にフラッシュしてから選択解除
- 画面は常時点灯（`FLAG_KEEP_SCREEN_ON`）
- 既定の API URL: `http://10.0.2.2:8081`（エミュレータからホスト側 API）

API URL の変更:

- `android/app/build.gradle.kts` の `buildConfigField("String", "API_BASE_URL", ...)`
- または `android/app/src/main/res/values/strings.xml` の `api_base_url`

実機から PC 上の API に繋ぐ場合は、PC の LAN IP（例: `http://192.168.x.x:8081`）に変更してください。
