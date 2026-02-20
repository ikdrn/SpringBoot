package com.bistrolumiere.reservation.service;

import com.bistrolumiere.reservation.dto.ReservationRequest;
import com.bistrolumiere.reservation.dto.ReservationResponse;
import com.bistrolumiere.reservation.entity.Reservation;
import com.bistrolumiere.reservation.exception.BusinessException;
import com.bistrolumiere.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  ReservationServiceTest - Service 層の単体テスト               ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>なぜこのテストが必要か？</h2>
 * <p>
 * Service 層にはビジネスロジックが集中しています。
 * 「満席なら予約できない」「過去日付はNG」「1ヶ月先はNG」など、
 * これらのルールが正しく機能していることを自動で検証するためのテストです。
 * </p>
 * <p>
 * 人手でのテスト（手動テスト）は：
 * </p>
 * <ul>
 *   <li>毎回同じ手順で確認するのが大変</li>
 *   <li>コードを変更するたびに「壊れていないか」を確認する工数がかかる</li>
 *   <li>見落としが発生しやすい</li>
 * </ul>
 * <p>
 * 自動テストならコマンド1つで数秒のうちに全パターンを検証できます。
 * これが「リグレッション（回帰）テスト」と呼ばれる仕組みで、
 * 安心してコードを改修できる土台になります。
 * </p>
 *
 * <h2>Mockito（モック）とは何か？</h2>
 * <p>
 * モック（Mock）は「偽物の協力者」です。
 * Service は Repository に依存していますが、単体テストでは
 * 本物の DB に接続したくありません（テストが遅くなる、環境が必要）。
 * </p>
 * <p>
 * Mockito を使うと、「countByDateAndTime を呼んだら 5 を返すフリをして」と
 * Repository の振る舞いを制御できます。これにより：
 * </p>
 * <ul>
 *   <li>DB なしでテストが動く（高速・安定）</li>
 *   <li>「満席の場合」を簡単に再現できる（テストが壊れにくい）</li>
 *   <li>Service のロジックだけに集中してテストできる（単体テストの本質）</li>
 * </ul>
 *
 * <h2>テスト構造（@Nested）について</h2>
 * <p>
 * {@code @Nested} クラスでテストをグループ化することで、
 * 「予約作成 > 成功ケース」「予約作成 > 満席ケース」のように
 * テスト結果が整理されて見やすくなります。
 * </p>
 *
 * <h2>テスト技法：Given-When-Then パターン</h2>
 * <p>
 * 各テストは以下の3つのフェーズで書かれています：
 * </p>
 * <ul>
 *   <li>Given（前提）: テストの前提条件をセットアップ</li>
 *   <li>When（操作）: テスト対象のメソッドを呼び出す</li>
 *   <li>Then（検証）: 結果が期待通りか確認する</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class) // Mockito を JUnit 5 で使うための設定
@DisplayName("ReservationService 単体テスト")
class ReservationServiceTest {

    @Mock // Repository の「偽物」を作成（DBに接続しない）
    private ReservationRepository reservationRepository;

    @InjectMocks // テスト対象クラス。@Mock を自動で注入してくれる
    private ReservationService reservationService;

    // テストデータの定数
    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);
    private static final LocalTime NOON_TIME = LocalTime.of(12, 0);
    private static final LocalDate PAST_DATE = LocalDate.now().minusDays(1);
    private static final LocalDate TOO_FAR_DATE = LocalDate.now().plusDays(31);

    // テスト用の Reservation エンティティを生成するヘルパーメソッド
    private Reservation buildSavedReservation(Long id, String guestName) {
        return Reservation.builder()
            .id(id)
            .guestName(guestName)
            .guestEmail("test@example.com")
            .partySize(2)
            .reservationDate(FUTURE_DATE)
            .reservationTime(NOON_TIME)
            .status(Reservation.ReservationStatus.CONFIRMED)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    // テスト用のリクエスト DTO を生成するヘルパーメソッド
    private ReservationRequest buildRequest(LocalDate date, LocalTime time, int partySize) {
        ReservationRequest request = new ReservationRequest();
        request.setGuestName("田中 太郎");
        request.setGuestEmail("tanaka@example.com");
        request.setPartySize(partySize);
        request.setReservationDate(date);
        request.setReservationTime(time);
        return request;
    }

    // ─────────────────────────────────────────────────────────────────
    // 予約作成のテスト群
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("予約作成 (createReservation)")
    class CreateReservation {

        @Test
        @DisplayName("正常系: 空席がある場合、予約が正常に作成される")
        void should_create_reservation_when_seats_available() {
            // ─── Given: 前提条件のセットアップ ───────────────────────────
            ReservationRequest request = buildRequest(FUTURE_DATE, NOON_TIME, 2);

            // モック設定: 現在の予約数 = 3（5テーブル中3つ埋まっている）
            when(reservationRepository.countByDateAndTime(FUTURE_DATE, NOON_TIME))
                .thenReturn(3L);

            // モック設定: save() が呼ばれたら、IDを付与した予約を返すフリをする
            Reservation savedReservation = buildSavedReservation(1L, "田中 太郎");
            when(reservationRepository.save(any(Reservation.class)))
                .thenReturn(savedReservation);

            // ─── When: テスト対象メソッドを実行 ─────────────────────────
            ReservationResponse response = reservationService.createReservation(request);

            // ─── Then: 結果の検証 ─────────────────────────────────────────
            // AssertJ の流暢な記法でアサーション
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getGuestName()).isEqualTo("田中 太郎");
            assertThat(response.getStatus()).isEqualTo("CONFIRMED");

            // save() が1回だけ呼ばれたことを検証（余計な DB アクセスがないこと）
            verify(reservationRepository, times(1)).save(any(Reservation.class));
        }

        @Test
        @DisplayName("異常系: 満席の場合、BusinessException がスローされる")
        void should_throw_exception_when_fully_booked() {
            // ─── Given ────────────────────────────────────────────────────
            ReservationRequest request = buildRequest(FUTURE_DATE, NOON_TIME, 2);

            // 重要テスト: 予約数 = 5（満席！）をシミュレート
            when(reservationRepository.countByDateAndTime(FUTURE_DATE, NOON_TIME))
                .thenReturn(5L);

            // ─── When & Then ──────────────────────────────────────────────
            // assertThatThrownBy: 例外がスローされることを検証する AssertJ の記法
            assertThatThrownBy(() -> reservationService.createReservation(request))
                .isInstanceOf(BusinessException.class)  // BusinessException が発生すること
                .hasMessageContaining("満席");           // メッセージに「満席」が含まれること

            // 満席なので save() は絶対に呼ばれないはず
            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        @DisplayName("異常系: 過去の日付を指定した場合、BusinessException がスローされる")
        void should_throw_exception_when_past_date() {
            // ─── Given ────────────────────────────────────────────────────
            ReservationRequest request = buildRequest(PAST_DATE, NOON_TIME, 2);

            // ─── When & Then ──────────────────────────────────────────────
            assertThatThrownBy(() -> reservationService.createReservation(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("過去");

            // 過去日付チェックで弾かれるので DB に一切アクセスしないはず
            verify(reservationRepository, never()).countByDateAndTime(any(), any());
            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        @DisplayName("異常系: 1ヶ月以上先の日付を指定した場合、BusinessException がスローされる")
        void should_throw_exception_when_date_too_far_in_future() {
            // ─── Given ────────────────────────────────────────────────────
            ReservationRequest request = buildRequest(TOO_FAR_DATE, NOON_TIME, 2);

            // ─── When & Then ──────────────────────────────────────────────
            assertThatThrownBy(() -> reservationService.createReservation(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1ヶ月");

            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        @DisplayName("正常系: 4席ちょうど空いている場合（境界値）、予約が作成できる")
        void should_create_reservation_when_one_seat_is_available() {
            // ─── Given: 境界値テスト（残り1テーブル）────────────────────
            ReservationRequest request = buildRequest(FUTURE_DATE, NOON_TIME, 2);

            // 4組埋まっていて残り1組ぶん空いている
            when(reservationRepository.countByDateAndTime(FUTURE_DATE, NOON_TIME))
                .thenReturn(4L);

            Reservation savedReservation = buildSavedReservation(2L, "田中 太郎");
            when(reservationRepository.save(any(Reservation.class)))
                .thenReturn(savedReservation);

            // ─── When ─────────────────────────────────────────────────────
            ReservationResponse response = reservationService.createReservation(request);

            // ─── Then ─────────────────────────────────────────────────────
            assertThat(response).isNotNull();
            verify(reservationRepository, times(1)).save(any(Reservation.class));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 予約キャンセルのテスト群
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("予約キャンセル (cancelReservation)")
    class CancelReservation {

        @Test
        @DisplayName("正常系: 確定済み予約のキャンセルが正常に完了する")
        void should_cancel_confirmed_reservation() {
            // ─── Given ────────────────────────────────────────────────────
            Reservation confirmedReservation = buildSavedReservation(1L, "山田 花子");

            when(reservationRepository.findById(1L))
                .thenReturn(Optional.of(confirmedReservation));

            // キャンセル後の状態を返す
            Reservation cancelledReservation = buildSavedReservation(1L, "山田 花子");
            cancelledReservation.setStatus(Reservation.ReservationStatus.CANCELLED);
            when(reservationRepository.save(any(Reservation.class)))
                .thenReturn(cancelledReservation);

            // ─── When ─────────────────────────────────────────────────────
            ReservationResponse response = reservationService.cancelReservation(1L);

            // ─── Then ─────────────────────────────────────────────────────
            assertThat(response.getStatus()).isEqualTo("CANCELLED");
            verify(reservationRepository, times(1)).save(any(Reservation.class));
        }

        @Test
        @DisplayName("異常系: 存在しない予約 ID のキャンセルは例外が発生する")
        void should_throw_exception_when_reservation_not_found() {
            // ─── Given ────────────────────────────────────────────────────
            when(reservationRepository.findById(999L))
                .thenReturn(Optional.empty()); // 予約が見つからない状態をモック

            // ─── When & Then ──────────────────────────────────────────────
            assertThatThrownBy(() -> reservationService.cancelReservation(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("999");

            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        @DisplayName("異常系: すでにキャンセル済みの予約は再キャンセルできない")
        void should_throw_exception_when_already_cancelled() {
            // ─── Given ────────────────────────────────────────────────────
            Reservation cancelledReservation = buildSavedReservation(1L, "鈴木 一郎");
            cancelledReservation.setStatus(Reservation.ReservationStatus.CANCELLED);

            when(reservationRepository.findById(1L))
                .thenReturn(Optional.of(cancelledReservation));

            // ─── When & Then ──────────────────────────────────────────────
            assertThatThrownBy(() -> reservationService.cancelReservation(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("キャンセル済み");

            verify(reservationRepository, never()).save(any(Reservation.class));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 空席確認のテスト群
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("空席確認 (getAvailableSeats)")
    class GetAvailableSeats {

        @Test
        @DisplayName("正常系: 予約なしの場合、5席が空いている")
        void should_return_5_when_no_reservations() {
            when(reservationRepository.countByDateAndTime(FUTURE_DATE, NOON_TIME))
                .thenReturn(0L);

            int availableSeats = reservationService.getAvailableSeats(FUTURE_DATE, NOON_TIME);

            assertThat(availableSeats).isEqualTo(5);
        }

        @Test
        @DisplayName("正常系: 満席の場合、0席が返される")
        void should_return_0_when_fully_booked() {
            when(reservationRepository.countByDateAndTime(FUTURE_DATE, NOON_TIME))
                .thenReturn(5L);

            int availableSeats = reservationService.getAvailableSeats(FUTURE_DATE, NOON_TIME);

            assertThat(availableSeats).isEqualTo(0);
        }

        @Test
        @DisplayName("正常系: 3組予約済みの場合、2席が空いている")
        void should_return_2_when_3_reservations() {
            when(reservationRepository.countByDateAndTime(FUTURE_DATE, NOON_TIME))
                .thenReturn(3L);

            int availableSeats = reservationService.getAvailableSeats(FUTURE_DATE, NOON_TIME);

            assertThat(availableSeats).isEqualTo(2);
        }
    }
}
