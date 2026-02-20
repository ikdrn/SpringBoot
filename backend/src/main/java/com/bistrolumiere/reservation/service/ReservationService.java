package com.bistrolumiere.reservation.service;

import com.bistrolumiere.reservation.dto.ReservationRequest;
import com.bistrolumiere.reservation.dto.ReservationResponse;
import com.bistrolumiere.reservation.entity.Reservation;
import com.bistrolumiere.reservation.exception.BusinessException;
import com.bistrolumiere.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  ReservationService - ビジネスロジック層                        ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>Service 層の役割</h2>
 * <p>
 * Service（サービス）層はアプリの「脳みそ」です。
 * 「予約できる条件は何か」「キャンセルはいつまで可能か」といった
 * ビジネスルール（業務ルール）をすべてここに集約します。
 * </p>
 *
 * <h2>レイヤードアーキテクチャの依存関係</h2>
 * <pre>
 *   [フロントエンド]
 *        ↓ HTTP リクエスト
 *   [Controller 層] ← リクエストの受付と返却だけ。ロジックは持たない
 *        ↓ メソッド呼び出し
 *   [Service 層]   ← ビジネスロジックはここだけに書く（このクラス）
 *        ↓ メソッド呼び出し
 *   [Repository 層] ← DBとのやり取りだけ。ロジックは持たない
 *        ↓ SQL
 *   [データベース]
 * </pre>
 *
 * <h2>@Transactional（トランザクション制御）の重要性</h2>
 * <p>
 * トランザクションとは「一連の処理を一体として扱う」仕組みです。
 * 予約処理の例で考えます：
 * </p>
 * <ol>
 *   <li>空席確認（SELECT）</li>
 *   <li>予約レコード作成（INSERT）</li>
 * </ol>
 * <p>
 * もし①と②の間に別のユーザーも同時に予約しようとしたら？
 * 両方のユーザーが「空席あり」を確認して、両方が INSERT してしまう
 * 「ダブルブッキング」が発生します！
 * </p>
 * <p>
 * {@code @Transactional} を付けることで、この一連の処理が
 * データベースのトランザクション内で実行されます。
 * 並行処理時のデータ整合性を守る、本番品質の必須機能です。
 * </p>
 *
 * <h2>@Transactional の動作の仕組み（Spring AOP）</h2>
 * <p>
 * Spring は {@code @Service} を付けたクラスを「プロキシ（代理人）」で
 * ラップします。Controller が Service のメソッドを呼ぶ際、
 * 実際にはプロキシが呼ばれ、プロキシがトランザクション開始・コミット・
 * ロールバックを自動で行います。開発者はトランザクション管理コードを
 * 一切書かなくてよい。これが Spring の美しさです。
 * </p>
 *
 * <h2>@RequiredArgsConstructor（コンストラクタインジェクション）</h2>
 * <p>
 * {@code @RequiredArgsConstructor} は {@code final} フィールドを引数に持つ
 * コンストラクタを自動生成します。Spring がこのコンストラクタを通じて
 * {@code ReservationRepository} のインスタンスを自動で注入します。
 * </p>
 * <p>
 * フィールドインジェクション（{@code @Autowired} フィールドに付ける方法）より
 * コンストラクタインジェクションが推奨される理由：
 * テストが書きやすい（テスト時に {@code new Service(mockRepo)} と書けばよい）。
 * </p>
 */
@Slf4j
@Service // Spring にこのクラスをビジネスロジック層として認識させる
@RequiredArgsConstructor // final フィールドのコンストラクタを自動生成（コンストラクタインジェクション）
public class ReservationService {

    // ビジネスルールの定数
    private static final int MAX_TABLES = 5;               // 最大テーブル数
    private static final int MAX_ADVANCE_DAYS = 30;        // 最大予約可能日数（1ヶ月）
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm");

    // コンストラクタインジェクション: Spring がこのフィールドに Repository を自動で注入します
    private final ReservationRepository reservationRepository;

    /**
     * 予約を作成します。
     *
     * <h3>@Transactional の注意点</h3>
     * <p>
     * デフォルトでは RuntimeException がスローされると自動でロールバックされます。
     * {@code readOnly = false}（デフォルト）なので、INSERT/UPDATE が可能です。
     * </p>
     *
     * <h3>ダブルブッキング防止の仕組み</h3>
     * <p>
     * 空席確認と予約作成が同一トランザクション内で行われるため、
     * 並行リクエストが来ても DB のトランザクション分離レベルによって
     * 整合性が保たれます。本番環境では DB レベルのロック
     * (SELECT FOR UPDATE) と組み合わせることでさらに強固になります。
     * </p>
     *
     * @param request フロントエンドからの予約リクエスト
     * @return 作成された予約情報
     */
    @Transactional // このメソッド全体をひとつのトランザクションとして処理
    public ReservationResponse createReservation(ReservationRequest request) {
        LocalDate reservationDate = request.getReservationDate();
        LocalTime reservationTime = request.getReservationTime();

        log.info("予約作成リクエスト: 日付={}, 時間={}, 人数={}, 名前={}",
            reservationDate, reservationTime, request.getPartySize(), request.getGuestName());

        // ─── ビジネスルール①: 過去の日付への予約禁止 ─────────────────────
        // Bean Validation (@FutureOrPresent) でも弾けますが、
        // Service 層でも明示的にチェックすることで多層防御になります
        if (reservationDate.isBefore(LocalDate.now())) {
            throw BusinessException.pastDateNotAllowed();
        }

        // ─── ビジネスルール②: 1ヶ月超の先日付予約禁止 ────────────────────
        LocalDate maxAllowedDate = LocalDate.now().plusDays(MAX_ADVANCE_DAYS);
        if (reservationDate.isAfter(maxAllowedDate)) {
            throw BusinessException.tooFarInFuture();
        }

        // ─── ビジネスルール③: 満席チェック（ダブルブッキング防止の核心）──
        // 同じ日時の確定済み予約数を取得
        long currentBookings = reservationRepository.countByDateAndTime(reservationDate, reservationTime);
        log.debug("日時 {} {} の現在の予約数: {}/{}", reservationDate, reservationTime, currentBookings, MAX_TABLES);

        if (currentBookings >= MAX_TABLES) {
            String dateTimeStr = reservationDate.format(DATE_FORMATTER) + " " +
                                 reservationTime.format(TIME_FORMATTER);
            throw BusinessException.fullyBooked(dateTimeStr);
        }

        // ─── 予約エンティティの作成と保存 ────────────────────────────────
        // DTO → Entity への変換
        // ビルダーパターンを使うことで、どのフィールドに何をセットするか明確になります
        Reservation reservation = Reservation.builder()
            .guestName(request.getGuestName())
            .guestEmail(request.getGuestEmail())
            .partySize(request.getPartySize())
            .reservationDate(reservationDate)
            .reservationTime(reservationTime)
            .specialRequest(request.getSpecialRequest())
            .status(Reservation.ReservationStatus.CONFIRMED)
            .build();

        // save() が呼ばれると JPA が INSERT 文を発行します
        // @PrePersist により createdAt と updatedAt が自動でセットされます
        Reservation savedReservation = reservationRepository.save(reservation);
        log.info("予約が作成されました: id={}", savedReservation.getId());

        // Entity → Response DTO への変換（DBの内部構造を隠蔽）
        return ReservationResponse.from(savedReservation);
    }

    /**
     * 全予約一覧を取得します。
     *
     * <h3>@Transactional(readOnly = true) について</h3>
     * <p>
     * 読み取り専用のトランザクションを明示することで：
     * </p>
     * <ul>
     *   <li>Hibernate の「ダーティチェック」（変更検知）をスキップして高速化</li>
     *   <li>READ REPLICA（読み取り専用DBサーバー）にルーティングできる</li>
     *   <li>コードの意図（このメソッドはデータを変更しない）が明確になる</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations() {
        return reservationRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(ReservationResponse::from) // メソッド参照で Entity → DTO 変換
            .collect(Collectors.toList());
    }

    /**
     * 指定した予約IDの詳細を取得します。
     */
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> BusinessException.reservationNotFound(id));
        return ReservationResponse.from(reservation);
    }

    /**
     * 指定した日付の予約一覧を取得します。
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByDate(LocalDate date) {
        return reservationRepository.findByReservationDateOrderByReservationTimeAsc(date)
            .stream()
            .map(ReservationResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * 予約をキャンセルします。
     *
     * <p>
     * 物理削除（DELETE）ではなく論理削除（status を CANCELLED に変更）を使います。
     * これにより予約の履歴を残せるため、トラブル時の調査や売上分析に活用できます。
     * 実務では物理削除より論理削除が好まれることが多いです。
     * </p>
     *
     * @param id キャンセルする予約の ID
     * @return キャンセル後の予約情報
     */
    @Transactional
    public ReservationResponse cancelReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> BusinessException.reservationNotFound(id));

        // すでにキャンセル済みかチェック
        if (reservation.getStatus() == Reservation.ReservationStatus.CANCELLED) {
            throw BusinessException.alreadyCancelled(id);
        }

        log.info("予約をキャンセルします: id={}", id);

        // ステータスを CANCELLED に変更（論理削除）
        reservation.setStatus(Reservation.ReservationStatus.CANCELLED);

        // save() は UPDATE 文を発行（既存レコードの更新）
        // @PreUpdate により updatedAt が自動更新されます
        Reservation cancelledReservation = reservationRepository.save(reservation);
        return ReservationResponse.from(cancelledReservation);
    }

    /**
     * 指定日時の空席状況を確認します。
     *
     * @param date 確認する日付
     * @param time 確認する時間
     * @return 残席数（0の場合は満席）
     */
    @Transactional(readOnly = true)
    public int getAvailableSeats(LocalDate date, LocalTime time) {
        long currentBookings = reservationRepository.countByDateAndTime(date, time);
        return (int) Math.max(0, MAX_TABLES - currentBookings);
    }
}
