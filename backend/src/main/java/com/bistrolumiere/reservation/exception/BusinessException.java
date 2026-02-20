package com.bistrolumiere.reservation.exception;

import org.springframework.http.HttpStatus;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  BusinessException - ビジネスルール違反を表すカスタム例外       ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>カスタム例外を作る理由</h2>
 * <p>
 * Java の標準例外（IllegalArgumentException, IllegalStateException など）を
 * そのまま使っても動きますが、以下の問題があります：
 * </p>
 * <ul>
 *   <li>例外の意味が曖昧で、ハンドラーで「どの例外か」を判別しにくい</li>
 *   <li>HTTP ステータスコードの情報を例外に含められない</li>
 *   <li>業務ロジック起因のエラーか、プログラムのバグかが区別できない</li>
 * </ul>
 *
 * <p>
 * カスタム例外を作ることで、{@code @RestControllerAdvice} が
 * 「ビジネスルール違反なら 400 を返す」「認証エラーなら 401 を返す」など
 * 例外の種類に応じて適切な HTTP ステータスを返せるようになります。
 * </p>
 *
 * <h2>例え話</h2>
 * <p>
 * 標準例外は「何か問題が起きました」という汎用的な警告ランプ。
 * カスタム例外は「燃料切れです」「エンジン過熱です」という
 * 具体的なアラートランプです。対処方法がすぐわかります。
 * </p>
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    /**
     * @param message    ユーザーに見せるエラーメッセージ
     * @param httpStatus 返却する HTTP ステータスコード
     * @param errorCode  フロントエンドが処理しやすいエラー識別コード
     */
    public BusinessException(String message, HttpStatus httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // ─── よく使うビジネス例外のファクトリメソッド ──────────────────────────

    /** 満席時の例外 */
    public static BusinessException fullyBooked(String dateTime) {
        return new BusinessException(
            "申し訳ございません。" + dateTime + " は満席となっております。別の日時をお選びください。",
            HttpStatus.CONFLICT,
            "FULLY_BOOKED"
        );
    }

    /** 過去日付の予約 */
    public static BusinessException pastDateNotAllowed() {
        return new BusinessException(
            "過去の日付への予約はできません。本日以降の日付を選択してください。",
            HttpStatus.BAD_REQUEST,
            "PAST_DATE_NOT_ALLOWED"
        );
    }

    /** 1ヶ月超の先日付予約 */
    public static BusinessException tooFarInFuture() {
        return new BusinessException(
            "予約は1ヶ月先までのみ受け付けております。",
            HttpStatus.BAD_REQUEST,
            "TOO_FAR_IN_FUTURE"
        );
    }

    /** 予約が見つからない */
    public static BusinessException reservationNotFound(Long id) {
        return new BusinessException(
            "予約番号 " + id + " の予約が見つかりません。",
            HttpStatus.NOT_FOUND,
            "RESERVATION_NOT_FOUND"
        );
    }

    /** すでにキャンセル済み */
    public static BusinessException alreadyCancelled(Long id) {
        return new BusinessException(
            "予約番号 " + id + " はすでにキャンセル済みです。",
            HttpStatus.CONFLICT,
            "ALREADY_CANCELLED"
        );
    }
}
