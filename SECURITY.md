# セキュリティの範囲

このアプリケーションは、信頼するPC上で実行する学習用CLIです。インターネットへ公開するHTTPサーバーは含みません。DB接続権限や環境変数を持つOSユーザーに対して、CLI内の管理パスワードだけで権限を隔離することはできません。

## 実装している対策

- SQL値のプレースホルダー化と、並び順のenum限定。
- 非負在庫・数量・金額の範囲、主キー・外部キー・非NULLのDB制約。
- 注文と登録のトランザクション、行ロック、在庫更新の競合検出。
- DBのroot接続と空パスワードを拒否。外部接続はTLS証明書とホスト名を検証。
- 接続タイムアウト、複文SQL・LOCAL INFILE・公開鍵自動取得を無効化。
- 管理モードはソルト付きPBKDF2-HMAC-SHA256、600,000回。ハッシュ未設定では無効。
- パスワードの非表示入力、プロセス内3回の試行制限。デモ管理モードだけは認証なし。
- 入力の長さ・件数の上限。DB例外の詳細や接続情報を画面へ出さない。

PBKDF2はJava標準APIで実装できる選択肢として採用しました。[OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)のパラメーターを参考にしています。これをもってアプリ全体の安全性やFIPS認証を保証するものではありません。

接続検証の意味は[MySQL Connector/JのTLS設定](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html)、H2とMySQLの差異は[H2の互換モード](https://h2database.com/html/features.html#compatibility)を参照してください。

## 本番運用に追加で必要なもの

認証・認可の独立した実装、顧客/管理者のDB資格情報とOS権限の分離、監査ログ、注文確認と冪等性、障害時復旧、DBバックアップ・復元検証、依存関係の継続監査、食物アレルゲン情報の管理・確認が必要です。プロセスを再起動すると認証試行回数はリセットされます。決済や会計には使用しないでください。

問題の報告時は、パスワード・DB接続情報・個人情報を公開Issueへ貼らず、再現条件を秘密情報なしで共有してください。
