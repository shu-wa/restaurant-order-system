# 飲食店注文・在庫管理システム

大学の講義で4人チームで制作した、Java・MySQLのCLIアプリケーションです。店舗ごとのメニュー検索から注文、在庫・売上確認までを扱います。このリポジトリは、授業提出後に構成とデータ整合性を見直した保守版です。

## 起動する

Java 21が必要です。Maven Wrapperの初回実行時にはネットワーク接続が必要です。

Windows PowerShell:

```powershell
.\mvnw.cmd verify
java -jar target/restaurant.jar --demo
```

macOS / Linux:

```sh
./mvnw verify
java -jar target/restaurant.jar --demo
```

`--demo`はH2の一時DBを作成します。外部サービスへの接続や実DBの変更は行わず、終了すると注文・在庫変更は消えます。管理側を試す場合は次のコマンドを使います。

```sh
java -jar target/restaurant.jar --demo --admin
```

デモの店舗・商品・アレルゲン情報は架空のテストデータです。実際の食物アレルギーの判断には使用できません。

## できること

- 店舗別メニューの検索。アレルゲン除外、価格・カロリー上限、限定商品の条件を組み合わせられます。
- ID・価格・カロリー・販売数での表示。条件なし・該当なし・1件のみの結果にも対応します。
- 複数商品の一括注文。在庫の減算、注文、明細を1トランザクションで保存します。
- 店舗別の在庫確認・更新。確認後に別の注文や更新が入った場合は上書きを拒否します。
- 商品別の注文数・売上集計。現在価格ではなく、注文時点の単価を使います。
- メニューとアレルゲンの登録、店舗への商品登録。

## 制作時の担当

授業当時は、システムフローとクラス構成の設計、`Main`・`CalorieFilter`・`PriceFilter`・`LimitedMenu`の実装、各クラスの統合、出力形式の調整を担当しました。チーム制作の後、個人で管理メニューの継続操作と`AddMenu`・`AddSupply`による登録機能を追加しました。

2026年10月の保守改修ではCodexの支援を受けて、責務分離、DB制約、トランザクション、回帰テスト、ビルド手順を追加・再構成しています。これらは授業当時の実装成果とは区別しています。

## 構成

```text
src/main/java/jp/shuwa/restaurant/
  Main.java             起動設定と管理モードの認証
  cli/                  入出力・操作の流れ
  domain/               検索条件・結果・入力の制約
  db/                   接続・検索SQL・トランザクション
  service/              注文・管理操作
  security/             管理パスワードのハッシュ
src/main/resources/db/
  schema.sql            空のDB用のテーブル定義
  demo.sql              架空の動作確認用データ
src/test/               回帰・整合性・競合・CLIテスト
docs/                   設計と旧版からの変更
```

詳細は[設計](docs/design.md)、[旧版からの変更と資料の整理](docs/provenance.md)、[セキュリティ上の範囲](SECURITY.md)を参照してください。

## MySQLで動かす

MySQL 8.4を対象にしています。**新規の空DB**を用意し、スキーマ管理用ユーザーでMySQLクライアントを起動して、リポジトリ直下から次を実行します。

```sql
SOURCE src/main/resources/db/schema.sql;
```

架空データも使う新規検証DBに限り、続けて次を実行できます。既存DBには実行しないでください。

```sql
SOURCE src/main/resources/db/demo.sql;
```

アプリケーションは起動時にスキーマを作成・変更しません。旧版DBの自動移行機能もありません。

PowerShellで、実行ユーザーの環境変数を設定します。パスワードをコードやGitに書き込まないでください。

```powershell
$env:RESTAURANT_DB_HOST = 'localhost'
$env:RESTAURANT_DB_PORT = '3306'
$env:RESTAURANT_DB_NAME = 'restaurant'
$env:RESTAURANT_DB_USER = 'restaurant_app'
$secret = Read-Host 'DB password' -AsSecureString
$env:RESTAURANT_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
$env:RESTAURANT_DB_TLS = 'REQUIRED'
java -jar target/restaurant.jar
Remove-Item Env:RESTAURANT_DB_PASSWORD
```

`REQUIRED`と`DISABLED`はループバック接続だけに許可しています。外部DBには`VERIFY_IDENTITY`が必須です。証明書の信頼設定はJVMのトラストストアで行います。`root`・空パスワード、URLへの任意オプション追加は拒否します。

アプリ用DBユーザーには必要な`SELECT`・`INSERT`・`UPDATE`だけを付与し、`CREATE`・`DROP`・`FILE`などは付与しないでください。顧客用と管理用は別のOSユーザー・DB資格情報で運用する必要があります。

管理モードでは、対話型ターミナルでハッシュを作成し、環境変数へ設定します。平文パスワードを引数に指定する方法はありません。

```powershell
java -jar target/restaurant.jar --hash-password
$env:RESTAURANT_ADMIN_PASSWORD_HASH = Read-Host '表示されたハッシュ'
java -jar target/restaurant.jar --admin
Remove-Item Env:RESTAURANT_ADMIN_PASSWORD_HASH
```

## 検証

`verify`でコンパイルとテスト、実行用JARの生成を行います。GitHub Actionsは同じテストをH2と使い捨てのMySQL 8.4の両方で実行します。H2のMySQL互換モードだけでMySQLの挙動を保証していません。

テスト対象には、検索先頭行、複数アレルゲン、店舗別提供、SQLの文字列入力、負数、注文途中のSQL失敗による全件ロールバック、20件の同時注文、在庫更新の競合、注文時単価、整数上限を超える合計、CLIのEOF・再入力を含みます。

## 対象外

これは学習用のローカルCLIであり、公開サービス用の認可境界ではありません。決済・会計・税計算・取消/返金・監査ログ・実店舗のアレルゲン保証・バックアップ運用は含みません。Web化や実運用には認証基盤、権限分離、運用設計を別途追加する必要があります。

チームソースの公開許可を得て掲載しています。講義の配布資料、個人名・学籍番号を含む提出物、旧DB設定や元CSVは同梱していません。リポジトリの公開と第三者への再配布ライセンス付与は別のため、OSSライセンスは設定していません。
