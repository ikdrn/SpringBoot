package com.bistrolumiere.reservation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  Reservation Entity - データベースのテーブルと対応するクラス    ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>Entity とは何か？</h2>
 * <p>
 * Entity（エンティティ）は「データベースのテーブル構造を Java で表現したクラス」です。
 * {@code @Entity} を付けると JPA が自動で SQL テーブルとマッピングしてくれます。
 * </p>
 *
 * <h2>例え話</h2>
 * <p>
 * Entity はデータベースの「設計図」です。
 * 建築でいえば、設計図（Entity）を元に実際の建物（テーブル）が作られます。
 * Hibernate（JPA 実装）が {@code ddl-auto: update} の設定に従い、
 * このクラスを見て自動で CREATE TABLE 文を発行してくれます。
 * </p>
 *
 * <h2>Entity と DTO の分離について</h2>
 * <p>
 * Entity はデータベースの内部構造をそのまま反映するため、
 * フロントエンドに直接返すと問題が起きることがあります：
 * </p>
 * <ul>
 *   <li>不必要なフィールド（created_at など）が漏れる</li>
 *   <li>セキュリティ上まずい情報（将来的なパスワードフィールドなど）が漏れる</li>
 *   <li>DB のテーブル構造変更が即 API の破壊的変更になる</li>
 * </ul>
 * <p>
 * そのため、Entity → DTO への変換を挟むことで「DB の変更を API に伝播させない」
 * 設計にします。これは実務では非常に重要なパターンです。
 * </p>
 *
 * <h2>Lombok アノテーションの説明</h2>
 * <ul>
 *   <li>{@code @Data} : getter/setter/equals/hashCode/toString を自動生成</li>
 *   <li>{@code @Builder} : ビルダーパターンでオブジェクト生成を可能にする</li>
 *   <li>{@code @NoArgsConstructor} : 引数なしコンストラクタを生成（JPA 必須）</li>
 *   <li>{@code @AllArgsConstructor} : 全フィールドを引数に持つコンストラクタを生成</li>
 * </ul>
 */
@Entity
@Table(name = "reservations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    /**
     * 主キー（Primary Key）。
     * @GeneratedValue により、DB が自動で連番を振ります。
     * IDENTITY 戦略 = PostgreSQL の SERIAL / BIGSERIAL に相当。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 予約者名。null 禁止、最大100文字。
     * @Column でテーブルのカラム属性を細かく設定できます。
     */
    @Column(name = "guest_name", nullable = false, length = 100)
    private String guestName;

    /** 予約者のメールアドレス */
    @Column(name = "guest_email", nullable = false, length = 255)
    private String guestEmail;

    /** 予約人数（1〜4名） */
    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    /** 予約日 */
    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    /** 予約時間 */
    @Column(name = "reservation_time", nullable = false)
    private LocalTime reservationTime;

    /**
     * 予約ステータス。
     * 文字列として DB に保存（CONFIRMED, CANCELLED など）。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    /** 特別なリクエスト（アレルギー対応など） */
    @Column(name = "special_request", length = 500)
    private String specialRequest;

    /**
     * レコード作成日時。
     * @PrePersist により、INSERT 時に自動でセットされます。
     * 手動でセットする必要がないため、誰かがセット忘れるリスクがありません。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** レコード更新日時 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA のライフサイクルコールバック。
     * エンティティが最初に INSERT される直前に自動で呼ばれます。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ReservationStatus.CONFIRMED;
        }
    }

    /**
     * UPDATE される直前に自動で呼ばれます。
     * 更新日時を自動で最新化します。
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 予約ステータスを表す列挙型（Enum）。
     * 文字列で DB に保存することで、DB の型縛りを緩くしつつ、
     * Java コード側では型安全に扱えます。
     */
    public enum ReservationStatus {
        CONFIRMED,   // 予約確定
        CANCELLED    // キャンセル済み
    }
}
