package com.bistrolumiere.reservation.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  DataSourceConfig - Neon接続URL の自動パースと DataSource 生成  ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>なぜこのクラスが必要か？</h2>
 * <p>
 * Neon (および Railway, Render, Heroku などのクラウドサービス) が提供する
 * 接続文字列は以下の形式です：
 * </p>
 * <pre>
 *   postgresql://ユーザー名:パスワード@ホスト/データベース名?オプション
 *   例: postgresql://neondb_owner:xxx@ep-xxx.neon.tech/neondb?sslmode=require
 * </pre>
 *
 * <p>
 * しかし Spring Boot（JDBC）が必要とする形式は：
 * </p>
 * <pre>
 *   jdbc:postgresql://ホスト/データベース名?オプション
 *   ユーザー名とパスワードは別のプロパティで指定
 * </pre>
 *
 * <p>
 * このクラスが Neon の URL を自動的に解析して
 * Spring Boot が使えるJDBC形式に変換します。
 * これにより、環境変数は {@code DATABASE_URL} の1つだけ設定すればOKになります。
 * </p>
 *
 * <h2>@ConditionalOnProperty について</h2>
 * <p>
 * {@code matchIfMissing = false} を指定しているため、
 * {@code DATABASE_URL} 環境変数が未設定（ローカル開発やテスト時）の場合、
 * このBeanは生成されません。その場合は {@code application.yml} の設定が使われます。
 * </p>
 *
 * <h2>例え話</h2>
 * <p>
 * Neonが渡してくる接続情報は「住所・氏名・電話番号がまとめて書かれた名刺」。
 * このクラスはその名刺を受け取って「住所」「氏名」「電話番号」に分けて
 * Spring Bootに渡す「翻訳係」の役割を担います。
 * </p>
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    /**
     * DATABASE_URL 環境変数を読み込みます。
     * 未設定の場合は null（Hikari のデフォルト設定にフォールバック）。
     */
    @Value("${DATABASE_URL:#{null}}")
    private String databaseUrl;

    /**
     * DATABASE_URL を解析して HikariDataSource を構築します。
     *
     * <p>
     * このBeanは DATABASE_URL プロパティ（環境変数）が存在する場合のみ生成されます。
     * {@code @Primary} により、他のDataSource定義よりも優先されます。
     * </p>
     *
     * @return 設定済みの HikariDataSource
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "DATABASE_URL")
    public DataSource neonDataSource() {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalStateException("DATABASE_URL が設定されていません。環境変数を確認してください。");
        }

        log.info("DATABASE_URL から DataSource を構築します（URL の詳細はセキュリティのためログに出力しません）");

        try {
            // ─── URL の解析 ────────────────────────────────────────────
            // Neon URL 例: postgresql://neondb_owner:pass@ep-xxx.neon.tech/neondb?sslmode=require
            // "postgresql://" を "//  " に置換して java.net.URI でパースできる形にする
            String normalizedUrl = databaseUrl;
            if (normalizedUrl.startsWith("postgresql://")) {
                normalizedUrl = normalizedUrl.replace("postgresql://", "//");
            } else if (normalizedUrl.startsWith("postgres://")) {
                normalizedUrl = normalizedUrl.replace("postgres://", "//");
            } else if (normalizedUrl.startsWith("jdbc:postgresql://")) {
                // すでに JDBC 形式の場合はそのまま使えるので、別処理へ
                return buildFromJdbcUrl(databaseUrl);
            }

            URI uri = new URI(normalizedUrl);

            String host     = uri.getHost();
            int    port     = uri.getPort() == -1 ? 5432 : uri.getPort();
            // パス先頭の "/" を除去してDB名を取得
            String dbName   = uri.getPath().replaceFirst("^/", "");
            String query    = uri.getRawQuery(); // sslmode=require&channel_binding=require など

            // ユーザー情報を "user:password" 形式で取得
            String userInfo  = uri.getUserInfo(); // "neondb_owner:npg_xxx"
            String username  = "";
            String password  = "";
            if (userInfo != null && userInfo.contains(":")) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts[1];
            } else if (userInfo != null) {
                username = userInfo;
            }

            // JDBC URL を組み立てる
            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s%s",
                host, port, dbName,
                query != null ? "?" + query : "");

            log.info("接続先ホスト: {}:{}/{}", host, port, dbName);

            return buildHikariDataSource(jdbcUrl, username, password);

        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                "DATABASE_URL の形式が不正です。" +
                "postgresql://user:pass@host/dbname?sslmode=require の形式で設定してください。", e);
        }
    }

    /**
     * すでに jdbc:postgresql:// 形式の URL を持つ場合のビルダー。
     * URL からユーザー情報は取れないため、別の環境変数から取得します。
     */
    private DataSource buildFromJdbcUrl(String jdbcUrl) {
        String username = System.getenv("DATABASE_USERNAME");
        String password = System.getenv("DATABASE_PASSWORD");
        return buildHikariDataSource(jdbcUrl, username, password);
    }

    /**
     * HikariDataSource を構築する共通メソッド。
     *
     * @param jdbcUrl  JDBC 形式の URL
     * @param username DB ユーザー名
     * @param password DB パスワード
     * @return 設定済み HikariDataSource
     */
    private DataSource buildHikariDataSource(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");

        // Neon Serverless に最適化したプール設定
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);      // 30秒
        config.setIdleTimeout(600_000);           // 10分
        config.setMaxLifetime(1_800_000);         // 30分

        // Neon はアイドル時に接続を切断するため、使用前に疎通確認を行います
        config.setConnectionTestQuery("SELECT 1");

        // SSL 接続の追加設定（Neon は SSL 必須）
        config.addDataSourceProperty("ssl", "true");
        config.addDataSourceProperty("sslmode", "require");

        log.info("HikariDataSource を構築しました: poolSize=5, sslmode=require");
        return new HikariDataSource(config);
    }
}
