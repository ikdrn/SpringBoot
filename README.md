# Bistro Lumiere - レストラン座席予約システム

人気フレンチビストロ「Bistro Lumiere」の座席予約を管理する Web アプリケーションです。

## プロジェクト構成

```
spring/
├── backend/    # Spring Boot REST API (Maven)
├── frontend/   # Vercel デプロイ用静的ファイル
└── docs/       # 設計ドキュメント
```

## 技術スタック

- **バックエンド**: Java 17 + Spring Boot 3.2.5 + Spring Data JPA
- **データベース**: Neon (Serverless PostgreSQL)
- **フロントエンド**: Vanilla HTML / CSS / JavaScript
- **ホスティング**: バックエンド=Railway/Render、フロントエンド=Vercel

## ローカル開発環境の起動手順

### 1. 環境変数の設定

```bash
export DATABASE_URL=jdbc:postgresql://<host>/<db>?sslmode=require
export DATABASE_USERNAME=<username>
export DATABASE_PASSWORD=<password>
```

### 2. バックエンドの起動

```bash
cd backend
mvn spring-boot:run
```

### 3. フロントエンドの確認

`frontend/index.html` をブラウザで開く、または Live Server 等を使用してください。

## テストの実行

```bash
cd backend
mvn test
```

## API ドキュメント

詳細は `docs/` フォルダのドキュメントを参照してください。

| ドキュメント | 内容 |
|------------|------|
| [01_要件定義書](docs/01_要件定義書.md) | システムの目的・機能要件 |
| [02_基本設計書](docs/02_基本設計書.md) | アーキテクチャ・API一覧 |
| [03_詳細設計書](docs/03_詳細設計書.md) | ER図・クラス図・セキュリティ設計 |
| [04_テスト仕様書](docs/04_単体・結合・総合テスト仕様書.md) | テスト仕様・実行結果 |
