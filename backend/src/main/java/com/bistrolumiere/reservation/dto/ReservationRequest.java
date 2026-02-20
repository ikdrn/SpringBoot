package com.bistrolumiere.reservation.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  ReservationRequest DTO - 予約作成のリクエストデータ            ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>DTO（Data Transfer Object）とは何か？</h2>
 * <p>
 * DTO は「データの搬送用コンテナ」です。
 * フロントエンドが送ってくる JSON を受け取り、Service 層に渡すための
 * 中間的なオブジェクトです。Entity とは別に定義することで、
 * 「外部に公開するデータの形」と「DBに保存するデータの形」を
 * 独立して管理できます。
 * </p>
 *
 * <h2>Bean Validation（入力値チェック）について</h2>
 * <p>
 * Jakarta Bean Validation（旧: javax.validation）のアノテーションを使うと、
 * コントローラーで {@code @Valid} を付けるだけで自動的にバリデーションが実行されます。
 * バリデーションNGの場合は自動で 400 Bad Request が返されます。
 * </p>
 * <p>
 * 例え話：「フォームの必須チェックをサーバー側でもやってくれる門番」のイメージです。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequest {

    /**
     * 予約者名。
     * @NotBlank : null でも空文字でも空白文字だけでもNG。
     * これが @NotNull より厳しいのは、空白文字まで禁止するためです。
     */
    @NotBlank(message = "お名前は必須です")
    @Size(max = 100, message = "お名前は100文字以内で入力してください")
    private String guestName;

    /**
     * 予約者のメールアドレス。
     * @Email : 「xxx@xxx.xxx」の形式かチェックします。
     */
    @NotBlank(message = "メールアドレスは必須です")
    @Email(message = "メールアドレスの形式が正しくありません")
    @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
    private String guestEmail;

    /**
     * 予約人数。
     * ビジネスルール：1〜4名。
     * @Min と @Max を組み合わせることで範囲制限を表現します。
     */
    @NotNull(message = "予約人数は必須です")
    @Min(value = 1, message = "予約人数は1名以上で入力してください")
    @Max(value = 4, message = "1テーブルの最大予約人数は4名です")
    private Integer partySize;

    /**
     * 予約日。
     * @FutureOrPresent : 今日以降の日付のみ許可（過去日付はNG）。
     * これがビジネスルール「予約は当日から」を自動で担保してくれます。
     */
    @NotNull(message = "予約日は必須です")
    @FutureOrPresent(message = "予約日は本日以降の日付を選択してください")
    private LocalDate reservationDate;

    /**
     * 予約時間。
     * バリデーション可能な時間帯のチェックはService層で行います（ビジネスロジック）。
     */
    @NotNull(message = "予約時間は必須です")
    private LocalTime reservationTime;

    /**
     * 特別なリクエスト（任意）。
     * アレルギー情報、席の希望など。
     */
    @Size(max = 500, message = "特別なリクエストは500文字以内で入力してください")
    private String specialRequest;
}
