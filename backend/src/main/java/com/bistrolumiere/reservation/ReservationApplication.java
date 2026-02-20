package com.bistrolumiere.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  ReservationApplication - アプリケーションのエントリーポイント  ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>@SpringBootApplication とは何か？</h2>
 * <p>
 * これは Spring Boot の「魔法の呪文」とも言えるアノテーションです。
 * 実は3つのアノテーションをまとめたショートカットです：
 * </p>
 * <ul>
 *   <li>{@code @SpringBootConfiguration} : このクラスが設定クラスだと宣言</li>
 *   <li>{@code @EnableAutoConfiguration} : classpath を見て、使いそうなものを自動設定
 *       （例：PostgreSQL ドライバーがあれば DB設定を自動で行う）</li>
 *   <li>{@code @ComponentScan} : このパッケージ以下の @Service, @Controller などを
 *       自動で検出して Spring の管理下に置く</li>
 * </ul>
 *
 * <h2>例え話</h2>
 * <p>
 * {@code @SpringBootApplication} は「開店準備ボタン」のようなものです。
 * このボタンを押すだけで、厨房の設備確認・スタッフの配置・お客様の誘導ルートの設定など、
 * 開店に必要なすべてが自動で整います。
 * </p>
 */
@SpringBootApplication
public class ReservationApplication {

    public static void main(String[] args) {
        // SpringApplication.run() がすべてのお膳立てをして、組み込み Tomcat を起動します。
        // この1行でWebサーバーが立ち上がるのが Spring Boot の凄さです。
        SpringApplication.run(ReservationApplication.class, args);
    }
}
