package com.bistrolumiere.reservation.controller;

import com.bistrolumiere.reservation.dto.ReservationRequest;
import com.bistrolumiere.reservation.dto.ReservationResponse;
import com.bistrolumiere.reservation.exception.BusinessException;
import com.bistrolumiere.reservation.service.ReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  ReservationControllerTest - Controller 層の結合テスト          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>なぜこのテストが必要か？</h2>
 * <p>
 * Controller 層のテストは「HTTP リクエストから始まる一連の流れ」を検証します。
 * 具体的には：
 * </p>
 * <ul>
 *   <li>URL のマッピングが正しいか（/api/reservations に POST でヒットするか）</li>
 *   <li>リクエストボディの JSON → Java オブジェクトへの変換が正しいか</li>
 *   <li>バリデーションエラーが 400 で返されるか</li>
 *   <li>正常時に 201 Created が返されるか</li>
 *   <li>レスポンスの JSON 構造が期待通りか</li>
 * </ul>
 *
 * <h2>@WebMvcTest について</h2>
 * <p>
 * {@code @SpringBootTest} はアプリ全体を起動しますが、
 * {@code @WebMvcTest} は Web 層（Controller, Filter, CORS など）のみを起動します。
 * DB や Service は起動されないため、テストが高速になります。
 * Service は {@code @MockBean} で偽物に差し替えます。
 * </p>
 *
 * <h2>MockMvc について</h2>
 * <p>
 * MockMvc は「仮想の HTTP クライアント」です。
 * 実際にブラウザや curl を使わなくても、
 * HTTP リクエストをプログラムから送信してレスポンスを検証できます。
 * </p>
 */
@WebMvcTest(ReservationController.class)
@ActiveProfiles("test") // test プロファイルを使用（application-test.yml が読まれる）
@DisplayName("ReservationController 結合テスト")
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc; // 仮想 HTTP クライアント

    @MockBean // Spring のコンテキストに登録する「偽物」の Service
    private ReservationService reservationService;

    private ObjectMapper objectMapper; // Java オブジェクト → JSON 変換ツール

    @BeforeEach
    void setUp() {
        // ObjectMapper に Java 8 日付型（LocalDate等）のサポートを追加
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // テスト用レスポンスデータのヘルパー
    private ReservationResponse buildMockResponse(Long id, String guestName) {
        return ReservationResponse.builder()
            .id(id)
            .guestName(guestName)
            .guestEmail("test@example.com")
            .partySize(2)
            .reservationDate(LocalDate.now().plusDays(7))
            .reservationTime(LocalTime.of(12, 0))
            .status("CONFIRMED")
            .createdAt(LocalDateTime.now())
            .build();
    }

    // テスト用リクエストデータのヘルパー
    private ReservationRequest buildValidRequest() {
        ReservationRequest request = new ReservationRequest();
        request.setGuestName("田中 太郎");
        request.setGuestEmail("tanaka@example.com");
        request.setPartySize(2);
        request.setReservationDate(LocalDate.now().plusDays(7));
        request.setReservationTime(LocalTime.of(12, 0));
        return request;
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /api/reservations のテスト群
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("POST /api/reservations - 予約作成")
    class PostReservation {

        @Test
        @WithMockUser // Spring Security が有効でも認証済みユーザーとして実行
        @DisplayName("正常系: 有効なリクエストで予約が作成され 201 が返される")
        void should_return_201_when_valid_request() throws Exception {
            // ─── Given ────────────────────────────────────────────────────
            ReservationRequest request = buildValidRequest();
            ReservationResponse mockResponse = buildMockResponse(1L, "田中 太郎");

            when(reservationService.createReservation(any(ReservationRequest.class)))
                .thenReturn(mockResponse);

            // ─── When & Then ──────────────────────────────────────────────
            mockMvc.perform(
                    post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()) // CSRF トークン付与（テスト用）
                )
                .andDo(print()) // テスト実行時にリクエスト・レスポンスをコンソールに出力
                .andExpect(status().isCreated())              // HTTP 201 であること
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))         // id フィールドが 1 であること
                .andExpect(jsonPath("$.guestName").value("田中 太郎"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        @WithMockUser
        @DisplayName("異常系: ゲスト名が空の場合、400 Bad Request が返される")
        void should_return_400_when_guest_name_is_blank() throws Exception {
            // ─── Given: バリデーションエラーを発生させるリクエスト ──────
            ReservationRequest invalidRequest = buildValidRequest();
            invalidRequest.setGuestName(""); // 空文字列 → @NotBlank で弾かれる

            // ─── When & Then ──────────────────────────────────────────────
            mockMvc.perform(
                    post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                        .with(csrf())
                )
                .andExpect(status().isBadRequest())           // HTTP 400 であること
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        }

        @Test
        @WithMockUser
        @DisplayName("異常系: 予約人数が5以上の場合、400 Bad Request が返される")
        void should_return_400_when_party_size_exceeds_max() throws Exception {
            // ─── Given ────────────────────────────────────────────────────
            ReservationRequest invalidRequest = buildValidRequest();
            invalidRequest.setPartySize(5); // 5名 → @Max(4) で弾かれる

            // ─── When & Then ──────────────────────────────────────────────
            mockMvc.perform(
                    post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                        .with(csrf())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        }

        @Test
        @WithMockUser
        @DisplayName("異常系: 満席の場合、Service から例外が上がり 409 Conflict が返される")
        void should_return_409_when_fully_booked() throws Exception {
            // ─── Given ────────────────────────────────────────────────────
            ReservationRequest request = buildValidRequest();

            // Service が BusinessException をスローする設定
            when(reservationService.createReservation(any(ReservationRequest.class)))
                .thenThrow(new BusinessException("満席です", HttpStatus.CONFLICT, "FULLY_BOOKED"));

            // ─── When & Then ──────────────────────────────────────────────
            mockMvc.perform(
                    post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf())
                )
                .andExpect(status().isConflict())             // HTTP 409 であること
                .andExpect(jsonPath("$.errorCode").value("FULLY_BOOKED"));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/reservations のテスト群
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/reservations - 予約一覧取得")
    class GetAllReservations {

        @Test
        @WithMockUser
        @DisplayName("正常系: 全予約一覧が取得できる")
        void should_return_all_reservations() throws Exception {
            // ─── Given ────────────────────────────────────────────────────
            List<ReservationResponse> mockList = List.of(
                buildMockResponse(1L, "田中 太郎"),
                buildMockResponse(2L, "山田 花子")
            );

            when(reservationService.getAllReservations()).thenReturn(mockList);

            // ─── When & Then ──────────────────────────────────────────────
            mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))   // リストの長さが 2 であること
                .andExpect(jsonPath("$[0].guestName").value("田中 太郎"))
                .andExpect(jsonPath("$[1].guestName").value("山田 花子"));
        }

        @Test
        @WithMockUser
        @DisplayName("正常系: 予約がない場合は空のリストが返される")
        void should_return_empty_list_when_no_reservations() throws Exception {
            when(reservationService.getAllReservations()).thenReturn(List.of());

            mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // DELETE /api/reservations/{id} のテスト群
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("DELETE /api/reservations/{id} - 予約キャンセル")
    class DeleteReservation {

        @Test
        @WithMockUser
        @DisplayName("正常系: 予約が正常にキャンセルされ、CANCELLED ステータスが返される")
        void should_return_cancelled_reservation() throws Exception {
            // ─── Given ────────────────────────────────────────────────────
            ReservationResponse cancelledResponse = buildMockResponse(1L, "田中 太郎");
            cancelledResponse.setStatus("CANCELLED");

            when(reservationService.cancelReservation(1L)).thenReturn(cancelledResponse);

            // ─── When & Then ──────────────────────────────────────────────
            mockMvc.perform(
                    delete("/api/reservations/1")
                        .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @WithMockUser
        @DisplayName("異常系: 存在しない予約 ID のキャンセルは 404 Not Found が返される")
        void should_return_404_when_reservation_not_found() throws Exception {
            // ─── Given ────────────────────────────────────────────────────
            when(reservationService.cancelReservation(999L))
                .thenThrow(new BusinessException("予約が見つかりません", HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND"));

            // ─── When & Then ──────────────────────────────────────────────
            mockMvc.perform(
                    delete("/api/reservations/999")
                        .with(csrf())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_NOT_FOUND"));
        }
    }
}
