package com.bistrolumiere.reservation.repository;

import com.bistrolumiere.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  ReservationRepository - データアクセス層（DAO）                ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <h2>Repository とは何か？</h2>
 * <p>
 * Repository（リポジトリ）はデータベースとのやり取りを担う「倉庫係」です。
 * 「このデータを保存して」「この条件でデータを探して」という命令を
 * SQL に翻訳してDBに伝えます。
 * </p>
 *
 * <h2>Spring Data JPA の魔法</h2>
 * <p>
 * {@code JpaRepository} を extends するだけで、以下のメソッドが
 * 自動で使えるようになります（実装コードを書かなくていい！）：
 * </p>
 * <ul>
 *   <li>{@code save(entity)} : INSERT または UPDATE</li>
 *   <li>{@code findById(id)} : 主キーで1件取得</li>
 *   <li>{@code findAll()} : 全件取得</li>
 *   <li>{@code deleteById(id)} : 主キーで削除</li>
 *   <li>{@code count()} : 件数取得</li>
 * </ul>
 *
 * <h2>依存性の注入（DI: Dependency Injection）について</h2>
 * <p>
 * {@code @Repository} を付けることで、Spring がこのクラスのインスタンスを
 * 管理してくれます。Service クラスが {@code @Autowired} や
 * コンストラクタインジェクションで「ください」と頼むと、
 * Spring が用意したインスタンスを自動で渡してくれます。
 * </p>
 * <p>
 * 例え話：「総務部（Spring）に申請すれば、必要な備品（Repository インスタンス）を
 * 勝手に手配してくれる」イメージです。自分で {@code new} する必要がありません。
 * </p>
 *
 * <h2>SQLインジェクション対策</h2>
 * <p>
 * Spring Data JPA はすべてのパラメーターをプリペアドステートメントで処理します。
 * ユーザーが「' OR '1'='1」のような悪意ある文字列を入力しても、
 * SQL として解釈されることなく、ただの文字列として扱われます。
 * </p>
 */
@Repository // Spring にこのクラスをデータアクセス層として認識させるアノテーション
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 指定した日付・時間帯の有効な予約数を取得します。
     *
     * <p>
     * このメソッドはダブルブッキング防止の核心です。
     * 予約確認から予約確定までの間にこのカウントを確認し、
     * 5組未満であれば予約を受け付けます。
     * </p>
     *
     * <p>
     * {@code @Query} アノテーションで JPQL（Java Persistence Query Language）を
     * 直接書いています。JPQL はテーブル名ではなく Entity 名で記述する SQL 方言です。
     * これにより、DBの方言（PostgreSQL, MySQL など）の差異を吸収できます。
     * </p>
     */
    @Query("SELECT COUNT(r) FROM Reservation r " +
           "WHERE r.reservationDate = :date " +
           "AND r.reservationTime = :time " +
           "AND r.status = 'CONFIRMED'")
    long countByDateAndTime(
        @Param("date") LocalDate date,
        @Param("time") LocalTime time
    );

    /**
     * 指定した日付の全予約を時間順で取得します。
     * メソッド名から自動でクエリが生成される Spring Data JPA の機能を活用。
     * 「findBy + フィールド名 + OrderBy + フィールド名 + Asc」で
     * 「WHERE reservation_date = ? ORDER BY reservation_time ASC」に変換されます。
     */
    List<Reservation> findByReservationDateOrderByReservationTimeAsc(LocalDate date);

    /**
     * 全予約を作成日の降順で取得（管理画面などで最新の予約を先に表示するため）。
     */
    List<Reservation> findAllByOrderByCreatedAtDesc();

    /**
     * 指定した日付範囲の予約を取得します（JPQL の Between 句を使用）。
     */
    @Query("SELECT r FROM Reservation r " +
           "WHERE r.reservationDate BETWEEN :startDate AND :endDate " +
           "AND r.status = 'CONFIRMED' " +
           "ORDER BY r.reservationDate ASC, r.reservationTime ASC")
    List<Reservation> findConfirmedReservationsBetweenDates(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
