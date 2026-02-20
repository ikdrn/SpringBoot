package com.bistrolumiere.reservation.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  ErrorResponse - エラー時の統一 JSON 形式                       ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>なぜ統一されたエラーフォーマットが必要か？</h2>
 * <p>
 * エラーの JSON 形式がバラバラだと、フロントエンドが
 * 「どのフィールドを見ればエラーメッセージが取れるのか」を
 * エンドポイントごとに知らなければなりません。
 * </p>
 * <p>
 * このクラスで「すべてのエラーレスポンスは必ずこの形」と決めることで、
 * フロントエンドは常に {@code error.message} を見ればよくなります。
 * </p>
 *
 * <h2>セキュリティ上の重要ポイント</h2>
 * <p>
 * このクラスには {@code stackTrace} フィールドがありません。
 * DB のエラー内容（テーブル名・SQLなど）や内部的なスタックトレースを
 * フロントエンドに返すと、攻撃者にシステム構造を教えてしまいます。
 * {@code @RestControllerAdvice} がすべての例外をここで定義した
 * 安全な形式に変換してから返します。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /** HTTP ステータスコード（例: 400, 404, 409） */
    private int status;

    /** エラーを識別するコード（フロントエンドの条件分岐用） */
    private String errorCode;

    /** ユーザーに表示するエラーメッセージ */
    private String message;

    /** エラー発生日時 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /** バリデーションエラーの詳細リスト（複数フィールドにエラーがある場合） */
    private List<FieldError> fieldErrors;

    /**
     * バリデーションエラーの個別フィールド情報。
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FieldError {
        /** エラーが発生したフィールド名 */
        private String field;
        /** 入力された値 */
        private Object rejectedValue;
        /** エラーメッセージ */
        private String message;
    }
}
