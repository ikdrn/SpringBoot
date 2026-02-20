/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  api/config.js - Vercel サーバーレス関数                        ║
 * ║                                                                  ║
 * ║  フロントエンド（ブラウザ）が「バックエンドAPIのURLはどこ？」   ║
 * ║  と尋ねるときに答える設定エンドポイントです。                   ║
 * ║                                                                  ║
 * ║  アクセス: GET /api/config                                       ║
 * ║  レスポンス: { "apiBaseUrl": "https://your-backend.railway.app" }║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【Vercel に設定する環境変数】
 *   BACKEND_API_URL = https://your-backend.railway.app
 *
 * ├── この関数は Vercel の「サーバーレス関数」として動作します。
 * ├── ブラウザ上の JS と違い、サーバー側で実行されるため
 * │   process.env から環境変数を安全に読み取れます。
 * └── DATABASE_URL などの DB 接続情報はここには不要です（DBに触れない）。
 */
export default function handler(req, res) {
  // Vercel ダッシュボードで設定した環境変数を読み取る
  const apiBaseUrl = process.env.BACKEND_API_URL;

  if (!apiBaseUrl) {
    // 環境変数が未設定の場合はデフォルト値を返す（ローカル開発用）
    return res.status(200).json({
      apiBaseUrl: 'http://localhost:8080/api',
      environment: 'development'
    });
  }

  // CORS ヘッダー（念のため）
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Cache-Control', 'public, max-age=60'); // 60秒キャッシュ

  return res.status(200).json({
    apiBaseUrl: apiBaseUrl.replace(/\/$/, '') + '/api', // 末尾スラッシュを除去して /api を付加
    environment: 'production'
  });
}
