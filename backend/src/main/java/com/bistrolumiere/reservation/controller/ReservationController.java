package com.bistrolumiere.reservation.controller;

import com.bistrolumiere.reservation.dto.ReservationRequest;
import com.bistrolumiere.reservation.dto.ReservationResponse;
import com.bistrolumiere.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  ReservationController - REST API エンドポイント               ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>Controller 層の役割</h2>
 * <p>
 * Controller はお店の「受付カウンター」です。
 * お客様（フロントエンド）からのリクエストを受け取り、
 * 厨房（Service 層）に依頼し、結果をお客様に返します。
 * ビジネスロジック（計算、判断）はここには書きません。
 * </p>
 *
 * <h2>@RestController について</h2>
 * <p>
 * {@code @RestController} は2つのアノテーションの組み合わせです：
 * </p>
 * <ul>
 *   <li>{@code @Controller}: このクラスが HTTP リクエストを処理すると宣言</li>
 *   <li>{@code @ResponseBody}: 戻り値を JSON として HTTP レスポンスボディに書き込む</li>
 * </ul>
 * <p>
 * 昔は Controller に HTML テンプレートを返す用途が主でしたが、
 * REST API では JSON を返すため、@RestController を使います。
 * </p>
 *
 * <h2>エンドポイント一覧</h2>
 * <pre>
 *   POST   /api/reservations          - 予約作成
 *   GET    /api/reservations          - 全予約一覧取得
 *   GET    /api/reservations/{id}     - 予約詳細取得
 *   DELETE /api/reservations/{id}     - 予約キャンセル
 *   GET    /api/reservations/date     - 日付別予約一覧
 *   GET    /api/reservations/availability - 空席確認
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/reservations") // このコントローラー全体の URL プレフィックス
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * 予約を新規作成します。
     *
     * <h3>@Valid について</h3>
     * <p>
     * {@code @Valid} を付けると、{@code ReservationRequest} に定義した
     * バリデーションアノテーション（@NotBlank, @Min など）が自動で実行されます。
     * バリデーション失敗時は {@code GlobalExceptionHandler} が自動で
     * 400 Bad Request を返します。
     * </p>
     *
     * <h3>@RequestBody について</h3>
     * <p>
     * HTTP リクエストの Body（JSON）を自動で Java オブジェクトに変換します。
     * Jackson ライブラリが裏で動いています。
     * </p>
     *
     * @param request フロントエンドから送られてくる予約情報（JSON → Java オブジェクト）
     * @return 作成された予約情報と HTTP 201 Created ステータス
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody ReservationRequest request) {
        log.info("予約作成 API 呼び出し: {}", request.getGuestName());
        ReservationResponse response = reservationService.createReservation(request);
        // HTTP 201 Created: リソースの作成が成功したことを表すステータスコード
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 全予約一覧を取得します。
     *
     * @return 予約一覧と HTTP 200 OK
     */
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getAllReservations() {
        log.info("全予約一覧取得 API 呼び出し");
        return ResponseEntity.ok(reservationService.getAllReservations());
    }

    /**
     * 指定した予約 ID の詳細を取得します。
     *
     * <h3>@PathVariable について</h3>
     * <p>
     * URL パスの一部（{id} の部分）を Java の引数として受け取ります。
     * {@code GET /api/reservations/42} のリクエストなら {@code id = 42} になります。
     * </p>
     *
     * @param id 予約 ID（URL パスから取得）
     * @return 予約詳細と HTTP 200 OK
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservationById(
            @PathVariable Long id) {
        log.info("予約詳細取得 API 呼び出し: id={}", id);
        return ResponseEntity.ok(reservationService.getReservationById(id));
    }

    /**
     * 指定した日付の予約一覧を取得します。
     *
     * <h3>@RequestParam について</h3>
     * <p>
     * URL クエリパラメータ（? 以降）を受け取ります。
     * {@code GET /api/reservations/date?date=2024-12-25} のような形式です。
     * </p>
     *
     * @param date 予約日（ISO 形式: yyyy-MM-dd）
     * @return 該当日の予約一覧と HTTP 200 OK
     */
    @GetMapping("/date")
    public ResponseEntity<List<ReservationResponse>> getReservationsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("日付別予約一覧取得 API 呼び出し: date={}", date);
        return ResponseEntity.ok(reservationService.getReservationsByDate(date));
    }

    /**
     * 指定した日時の空席状況を確認します。
     *
     * @param date 確認する日付
     * @param time 確認する時間（HH:mm 形式）
     * @return 残席数を含む情報と HTTP 200 OK
     */
    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {
        log.info("空席確認 API 呼び出し: date={}, time={}", date, time);

        int availableSeats = reservationService.getAvailableSeats(date, time);

        // Map.of() で即席のレスポンスオブジェクトを作成
        Map<String, Object> result = Map.of(
            "date", date.toString(),
            "time", time.toString(),
            "availableTables", availableSeats,
            "totalTables", 5,
            "isAvailable", availableSeats > 0
        );

        return ResponseEntity.ok(result);
    }

    /**
     * 予約をキャンセルします（論理削除）。
     *
     * <h3>DELETE メソッドについて</h3>
     * <p>
     * REST API の設計原則（RESTful）では、削除操作には DELETE メソッドを使います。
     * ただし今回は物理削除ではなくステータスを CANCELLED に変更する「論理削除」です。
     * </p>
     *
     * @param id キャンセルする予約の ID
     * @return キャンセル後の予約情報と HTTP 200 OK
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ReservationResponse> cancelReservation(
            @PathVariable Long id) {
        log.info("予約キャンセル API 呼び出し: id={}", id);
        return ResponseEntity.ok(reservationService.cancelReservation(id));
    }
}
