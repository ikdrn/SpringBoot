package com.bistrolumiere.reservation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  CorsConfig - CORS（クロスオリジンリソース共有）設定             ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>CORS とは何か？</h2>
 * <p>
 * ブラウザには「同一オリジンポリシー」というセキュリティルールがあります。
 * 「オリジン = プロトコル + ドメイン + ポート」であり、
 * 異なるオリジン間の通信はデフォルトでブロックされます。
 * </p>
 *
 * <h2>例え話</h2>
 * <p>
 * CORS は「入館証」のようなものです。
 * バックエンド（ビル A）が「ビル B から来た人（フロントエンド）は入館OK」と
 * 明示的に許可証を発行しないと、ブラウザが通信をブロックします。
 * このクラスがその「入館証発行窓口」の役割を担います。
 * </p>
 *
 * <h2>今回の構成</h2>
 * <ul>
 *   <li>フロントエンド: Vercel (https://xxx.vercel.app)</li>
 *   <li>バックエンド: Spring Boot API (https://api.xxx.com または localhost:8080)</li>
 * </ul>
 */
@Configuration // Spring の設定クラスだと宣言するアノテーション
public class CorsConfig implements WebMvcConfigurer {

    // application.yml や環境変数から CORS 許可オリジンを読み込む
    // 環境変数 ALLOWED_ORIGINS が未設定の場合は localhost を許可（開発用）
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5500,http://127.0.0.1:5500}")
    private String[] allowedOrigins;

    /**
     * CORS のルールをグローバルに設定します。
     * @RestController ごとに設定するより、ここで一括設定する方がメンテナンスしやすい。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
            // /api/** 以下のすべてのエンドポイントに対して CORS を設定
            .addMapping("/api/**")
            // 許可するオリジン（環境変数から読み込み）
            .allowedOriginPatterns("*")
            // 許可する HTTP メソッド
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            // 許可するリクエストヘッダー
            .allowedHeaders("*")
            // 認証情報（Cookie など）の送信を許可
            .allowCredentials(true)
            // プリフライトリクエストのキャッシュ時間（秒）
            // ブラウザがOPTIONSリクエストを毎回送らなくて済む
            .maxAge(3600);
    }
}
