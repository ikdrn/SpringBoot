package com.bistrolumiere.reservation.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  GlobalExceptionHandler - 全例外の統一ハンドラー                ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>@RestControllerAdvice とは何か？</h2>
 * <p>
 * {@code @RestControllerAdvice} はすべての {@code @RestController} の
 * 「共通の緊急対応チーム」です。
 * </p>
 * <p>
 * Controller から例外がスローされた場合、このクラスが自動的にキャッチして
 * 適切な HTTP レスポンスに変換します。
 * Controller 側は例外処理を一切書かなくてよくなります。
 * </p>
 *
 * <h2>例え話</h2>
 * <p>
 * レストランで何か問題が起きたとき（食材切れ、注文ミスなど）、
 * 各ウェイター（Controller）が対応を考えるのではなく、
 * マネージャー（GlobalExceptionHandler）が一括対応するイメージです。
 * お客様への謝罪の仕方も統一されています。
 * </p>
 *
 * <h2>セキュリティ上の重要性</h2>
 * <p>
 * 予期しない例外（NullPointerException、DB接続エラーなど）が
 * そのままフロントエンドに返ると、システムの内部情報が漏れます。
 * このクラスがすべての例外を安全な形式（ErrorResponse）に変換するので、
 * スタックトレースや DB の情報が外部に漏れることがありません。
 * </p>
 */
@Slf4j // Lombok の @Slf4j: log.info(), log.error() などのロガーを自動生成
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * ビジネスルール違反の例外ハンドラー。
     * 満席、過去日付、予約不存在などのビジネスロジックエラーを処理します。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        // ビジネス例外はWARNレベルで記録（システム異常ではなく業務上の事象）
        log.warn("ビジネス例外が発生しました: errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
            .status(ex.getHttpStatus().value())
            .errorCode(ex.getErrorCode())
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * Bean Validation のバリデーションエラーハンドラー。
     * {@code @Valid} によるチェックが失敗した場合にここが呼ばれます。
     * 複数のフィールドにエラーがある場合は、すべてまとめて返します。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("バリデーションエラーが発生しました: {}", ex.getMessage());

        BindingResult bindingResult = ex.getBindingResult();

        // すべてのフィールドエラーを収集して変換
        List<ErrorResponse.FieldError> fieldErrors = bindingResult.getFieldErrors().stream()
            .map(fieldError -> new ErrorResponse.FieldError(
                fieldError.getField(),
                fieldError.getRejectedValue(),
                fieldError.getDefaultMessage()
            ))
            .collect(Collectors.toList());

        ErrorResponse response = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .errorCode("VALIDATION_FAILED")
            .message("入力内容に誤りがあります。各フィールドを確認してください。")
            .timestamp(LocalDateTime.now())
            .fieldErrors(fieldErrors)
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 予期しない例外のフォールバックハンドラー。
     * このハンドラーが呼ばれた場合は何らかのバグの可能性が高いため、
     * ERRORレベルでログを出力し（スタックトレース付き）、
     * ユーザーには一般的なメッセージのみを返します。
     *
     * 【セキュリティポイント】
     * スタックトレースや DB エラーの詳細は絶対にレスポンスに含めません。
     * それらは内部ログにのみ記録します。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        // スタックトレースをサーバーログに記録（調査のため）
        log.error("予期しない例外が発生しました", ex);

        ErrorResponse response = ErrorResponse.builder()
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .errorCode("INTERNAL_SERVER_ERROR")
            // ユーザーには詳細を見せない。ログIDなどを返す運用も効果的。
            .message("サーバーで予期しないエラーが発生しました。しばらく経ってから再度お試しください。")
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.internalServerError().body(response);
    }
}
