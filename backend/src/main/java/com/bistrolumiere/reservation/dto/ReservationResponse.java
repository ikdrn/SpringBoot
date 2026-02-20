package com.bistrolumiere.reservation.dto;

import com.bistrolumiere.reservation.entity.Reservation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  ReservationResponse DTO - フロントエンドへの返却データ         ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>なぜ Response 用の DTO が必要か？</h2>
 * <p>
 * Entity をそのまま JSON に変換して返すと以下の問題が起きます：
 * </p>
 * <ol>
 *   <li>DB の内部構造（テーブル名、カラム名の命名規則）が外部に漏れる</li>
 *   <li>不要な情報（例えば将来の internal_code フィールドなど）も全部返ってしまう</li>
 *   <li>フロントエンドが必要とする「計算済みの値」を含められない
 *       （例：予約日と時間を組み合わせた読みやすい表示文字列など）</li>
 *   <li>Entity に Hibernate のプロキシオブジェクトが混じって JSON 変換でエラーが起きることがある</li>
 * </ol>
 * <p>
 * Response DTO はフロントエンドが「ちょうど欲しい形」に整形したデータを
 * 提供するための「お皿」です。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private Long id;
    private String guestName;
    private String guestEmail;
    private Integer partySize;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private String status;
    private String specialRequest;
    private LocalDateTime createdAt;

    /**
     * Entity から Response DTO への変換メソッド（静的ファクトリメソッド）。
     *
     * <p>
     * このメソッドを DTO 側に持たせることで、Controller や Service が
     * 変換ロジックを知らなくて済みます。責務の分離です。
     * </p>
     *
     * <p>
     * 例え話：料理（Entity）を、レストランが用意したお皿（Response DTO）に
     * 盛り付けるのがこのメソッドの仕事です。
     * </p>
     *
     * @param reservation 変換元の Reservation エンティティ
     * @return フロントエンドに返すための ReservationResponse
     */
    public static ReservationResponse from(Reservation reservation) {
        return ReservationResponse.builder()
            .id(reservation.getId())
            .guestName(reservation.getGuestName())
            .guestEmail(reservation.getGuestEmail())
            .partySize(reservation.getPartySize())
            .reservationDate(reservation.getReservationDate())
            .reservationTime(reservation.getReservationTime())
            .status(reservation.getStatus().name())
            .specialRequest(reservation.getSpecialRequest())
            .createdAt(reservation.getCreatedAt())
            .build();
    }
}
