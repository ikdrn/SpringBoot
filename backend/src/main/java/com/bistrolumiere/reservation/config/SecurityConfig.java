package com.bistrolumiere.reservation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  SecurityConfig - Spring Security 設定                          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>なぜ Spring Security を使うのか？</h2>
 * <p>
 * Spring Security を dependency に追加した瞬間、デフォルトで
 * 「すべてのエンドポイントにベーシック認証が必要」になります。
 * 今回は REST API なので、そのデフォルト設定を上書きして
 * アプリに合ったセキュリティポリシーを定義します。
 * </p>
 *
 * <h2>CSRF 無効化について</h2>
 * <p>
 * CSRF（クロスサイトリクエストフォージェリ）対策は
 * セッションベースの認証を使う場合に必要です。
 * REST API はステートレス（セッションを持たない）なので、
 * CSRF トークンは不要であり、無効化が正しい設計です。
 * </p>
 *
 * <h2>追加されるセキュリティヘッダー（Spring Security が自動で付与）</h2>
 * <ul>
 *   <li>X-Content-Type-Options: nosniff → MIMEスニッフィング攻撃防止</li>
 *   <li>X-Frame-Options: DENY → クリックジャッキング防止</li>
 *   <li>X-XSS-Protection: 1; mode=block → XSS フィルター有効化</li>
 *   <li>Strict-Transport-Security → HTTPS 強制</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity // Spring Security の詳細な設定を有効化するアノテーション
public class SecurityConfig {

    /**
     * SecurityFilterChain は Spring Security の「関所のルールブック」です。
     * HTTP リクエストがコントローラーに届く前に、ここで定義したフィルターを通ります。
     *
     * @Bean アノテーション: このメソッドの戻り値を Spring の管理対象（Bean）として登録します。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 無効化: REST API はステートレスなので不要
            .csrf(AbstractHttpConfigurer::disable)

            // セッション管理: STATELESS = セッションを一切使わない
            // これが REST API の基本スタイルです
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 認可設定: 今回はすべてのエンドポイントを認証なしで公開
            // 本番でユーザー認証を追加する場合はここを変更します
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").permitAll()  // API エンドポイントは全公開
                .requestMatchers("/h2-console/**").permitAll()  // H2コンソール（開発用）
                .anyRequest().authenticated()            // その他は認証必須
            )

            // H2 コンソールを iframe で表示するために X-Frame-Options を緩和
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            );

        return http.build();
    }
}
