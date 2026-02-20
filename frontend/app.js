/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  app.js - Bistro Lumiere フロントエンド アプリケーション        ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 技術: Vanilla JavaScript (ES2020+)
 * フレームワーク: なし（React/Vue 等を使わない純粋な JS）
 *
 * セキュリティポリシー:
 *   - XSS 対策: ユーザー入力値の表示には textContent を使用。
 *               innerHTML は静的な HTML 文字列にのみ使用し、
 *               ユーザーデータを絶対に混入しない。
 *   - API 通信: fetch API を使用し、エラーは必ずキャッチして
 *               ユーザーフレンドリーなメッセージで表示する。
 */

'use strict';

// ── 設定 ─────────────────────────────────────────────────────────────
/**
 * API のベース URL。
 * Vercel にデプロイすると /api/config エンドポイント（サーバーレス関数）から
 * バックエンドの URL を自動取得します。
 *
 * 【仕組み】
 *  ブラウザ → GET /api/config → Vercel サーバーレス関数 → { apiBaseUrl: "https://xxx.railway.app/api" }
 *
 * ローカル開発時は /api/config が存在しないため、
 * フォールバックとして localhost:8080 を使います。
 */
let API_BASE_URL = 'http://localhost:8080/api'; // デフォルト（ローカル開発用）

/**
 * Vercel の /api/config エンドポイントからバックエンド URL を取得します。
 * ページ読み込み時に一度だけ呼ばれます。
 */
async function loadConfig() {
  const isLocal = window.location.hostname === 'localhost'
               || window.location.hostname === '127.0.0.1';

  if (isLocal) {
    // ローカルではそのまま localhost を使う
    return;
  }

  try {
    const res = await fetch('/api/config');
    if (res.ok) {
      const config = await res.json();
      if (config.apiBaseUrl) {
        API_BASE_URL = config.apiBaseUrl;
      }
    }
  } catch {
    // /api/config が存在しない環境（ローカル Live Server など）はスキップ
  }
}

// ── DOM 要素のキャッシュ ──────────────────────────────────────────────
// 毎回 querySelector を呼ぶのはコストがかかるため、起動時に一度取得してキャッシュ
const DOM = {
  // ローディング
  loadingOverlay: document.getElementById('loading-overlay'),
  loadingText:    document.getElementById('loading-text'),

  // タブ
  tabBtns:   document.querySelectorAll('.tab-btn'),
  tabPanels: { reserve: document.getElementById('tab-reserve'), list: document.getElementById('tab-list') },

  // 空席確認
  checkDate:           document.getElementById('check-date'),
  checkTime:           document.getElementById('check-time'),
  checkAvailabilityBtn: document.getElementById('check-availability-btn'),
  availabilityResult:  document.getElementById('availability-result'),

  // 予約フォーム
  reservationForm:   document.getElementById('reservation-form'),
  guestName:         document.getElementById('guest-name'),
  guestEmail:        document.getElementById('guest-email'),
  reservationDate:   document.getElementById('reservation-date'),
  reservationTime:   document.getElementById('reservation-time'),
  partySize:         document.getElementById('party-size'),
  specialRequest:    document.getElementById('special-request'),
  charCount:         document.getElementById('special-request-count'),
  formError:         document.getElementById('form-error'),

  // 予約一覧
  reservationList: document.getElementById('reservation-list'),
  skeletonList:    document.getElementById('skeleton-list'),
  refreshBtn:      document.getElementById('refresh-btn'),

  // モーダル
  successModal:  document.getElementById('success-modal'),
  modalBody:     document.getElementById('modal-body'),
  modalOverlay:  document.getElementById('modal-overlay'),
  modalCloseBtn: document.getElementById('modal-close-btn'),
};

// ── ユーティリティ関数 ────────────────────────────────────────────────

/**
 * ローディングオーバーレイの表示/非表示を切り替えます。
 * Neon (Singapore リージョン) との通信遅延対策として、
 * API コール中は必ずローディングを表示してユーザーを安心させます。
 */
function showLoading(message = '処理中...') {
  DOM.loadingText.textContent = message; // textContent: XSS安全
  DOM.loadingOverlay.classList.remove('hidden');
  DOM.loadingOverlay.setAttribute('aria-hidden', 'false');
}

function hideLoading() {
  DOM.loadingOverlay.classList.add('hidden');
  DOM.loadingOverlay.setAttribute('aria-hidden', 'true');
}

/**
 * スケルトンローディング（一覧用）の表示/非表示
 */
function showSkeleton() {
  DOM.skeletonList.classList.remove('hidden');
  DOM.reservationList.classList.add('hidden');
}

function hideSkeleton() {
  DOM.skeletonList.classList.add('hidden');
  DOM.reservationList.classList.remove('hidden');
}

/**
 * フォームエラーメッセージを表示します。
 * @param {string} message - 表示するメッセージ
 */
function showFormError(message) {
  DOM.formError.textContent = message; // ← XSS対策: textContent を使用
  DOM.formError.classList.remove('hidden');
}

function hideFormError() {
  DOM.formError.classList.add('hidden');
  DOM.formError.textContent = '';
}

/**
 * フィールドエラーを表示します。
 * @param {string} fieldName - エラーを表示するフィールド名（例: 'guest-name'）
 * @param {string} message   - エラーメッセージ
 */
function showFieldError(fieldName, message) {
  const errorEl = document.getElementById(`error-${fieldName}`);
  const inputEl = document.getElementById(fieldName);
  if (errorEl) {
    errorEl.textContent = message; // textContent で XSS 対策
  }
  if (inputEl) {
    inputEl.classList.add('is-error');
  }
}

function clearFieldErrors() {
  document.querySelectorAll('.field-error').forEach(el => { el.textContent = ''; });
  document.querySelectorAll('.is-error').forEach(el => el.classList.remove('is-error'));
  hideFormError();
}

/**
 * API からのエラーレスポンスを処理します。
 * バリデーションエラーは各フィールドに表示し、
 * その他のエラーはフォーム全体に表示します。
 */
function handleApiError(errorData) {
  if (errorData.errorCode === 'VALIDATION_FAILED' && errorData.fieldErrors) {
    // フィールドごとのバリデーションエラーを表示
    errorData.fieldErrors.forEach(fieldError => {
      // field 名を HTML の id 形式に変換（例: guestName → guest-name）
      const htmlFieldName = camelToKebab(fieldError.field);
      showFieldError(htmlFieldName, fieldError.message);
    });
    showFormError('入力内容をご確認ください。');
  } else {
    // ビジネスロジックエラー（満席、過去日付など）
    showFormError(errorData.message || 'エラーが発生しました。');
  }
}

/**
 * camelCase を kebab-case に変換します（例: guestName → guest-name）。
 * Java の DTO フィールド名と HTML の id 属性を対応付けるために使います。
 */
function camelToKebab(str) {
  return str.replace(/([A-Z])/g, '-$1').toLowerCase();
}

/**
 * 日付を日本語形式にフォーマットします（例: 2025-12-25 → 2025年12月25日）。
 * XSS 安全: この関数は固定フォーマットのデータを処理するため textContent 経由で使います。
 */
function formatDate(dateStr) {
  if (!dateStr) return '';
  const date = new Date(dateStr + 'T00:00:00'); // タイムゾーン問題を防ぐ
  return new Intl.DateTimeFormat('ja-JP', {
    year: 'numeric', month: 'long', day: 'numeric', weekday: 'short'
  }).format(date);
}

/**
 * 時刻を HH:mm 形式にフォーマットします（例: 12:00:00 → 12:00）。
 */
function formatTime(timeStr) {
  if (!timeStr) return '';
  return timeStr.slice(0, 5); // "12:00:00" → "12:00"
}

// ── API 通信関数 ──────────────────────────────────────────────────────

/**
 * fetch のラッパー関数。共通の設定とエラーハンドリングを担います。
 * @param {string} path    - API パス（ベースURL以降）
 * @param {object} options - fetch オプション
 * @returns {Promise<any>} - レスポンス JSON
 */
async function apiFetch(path, options = {}) {
  const url = `${API_BASE_URL}${path}`;
  const defaultOptions = {
    headers: { 'Content-Type': 'application/json' },
  };
  const mergedOptions = { ...defaultOptions, ...options };

  const response = await fetch(url, mergedOptions);

  // レスポンスが JSON かどうかを確認
  const contentType = response.headers.get('content-type');
  const isJson = contentType && contentType.includes('application/json');
  const data = isJson ? await response.json() : null;

  if (!response.ok) {
    // API がエラーを返した場合、エラーオブジェクトとして throw
    const error = new Error('API Error');
    error.status = response.status;
    error.data = data;
    throw error;
  }

  return data;
}

/**
 * 予約を作成します。
 */
async function createReservation(requestData) {
  return apiFetch('/reservations', {
    method: 'POST',
    body: JSON.stringify(requestData),
  });
}

/**
 * 全予約一覧を取得します。
 */
async function fetchAllReservations() {
  return apiFetch('/reservations');
}

/**
 * 空席確認を行います。
 */
async function checkAvailability(date, time) {
  return apiFetch(`/reservations/availability?date=${date}&time=${time}`);
}

/**
 * 予約をキャンセルします。
 */
async function cancelReservation(id) {
  return apiFetch(`/reservations/${id}`, { method: 'DELETE' });
}

// ── タブ制御 ──────────────────────────────────────────────────────────

function switchTab(tabName) {
  // すべてのタブボタンとパネルを非アクティブに
  DOM.tabBtns.forEach(btn => {
    btn.classList.remove('active');
    btn.setAttribute('aria-selected', 'false');
  });

  Object.values(DOM.tabPanels).forEach(panel => panel.classList.add('hidden'));

  // 選択されたタブをアクティブに
  const activeBtn = document.querySelector(`[data-tab="${tabName}"]`);
  if (activeBtn) {
    activeBtn.classList.add('active');
    activeBtn.setAttribute('aria-selected', 'true');
  }

  const activePanel = DOM.tabPanels[tabName];
  if (activePanel) {
    activePanel.classList.remove('hidden');
  }

  // 一覧タブに切り替わったら予約を読み込む
  if (tabName === 'list') {
    loadReservationList();
  }
}

// ── 空席確認 ──────────────────────────────────────────────────────────

async function handleCheckAvailability() {
  const date = DOM.checkDate.value;
  const time = DOM.checkTime.value;

  if (!date || !time) {
    DOM.availabilityResult.innerHTML = ''; // 空にする（innerHTML は静的コンテンツのみ）
    DOM.availabilityResult.classList.remove('hidden');
    // XSS 安全: 固定メッセージのみ
    const p = document.createElement('p');
    p.textContent = '日付と時間を選択してください。';
    p.className = 'alert alert--warning';
    DOM.availabilityResult.appendChild(p);
    return;
  }

  DOM.checkAvailabilityBtn.disabled = true;

  try {
    const result = await checkAvailability(date, time);
    renderAvailabilityResult(result);
  } catch (err) {
    const p = document.createElement('p');
    p.textContent = '空席確認中にエラーが発生しました。もう一度お試しください。';
    p.className = 'alert alert--error';
    DOM.availabilityResult.innerHTML = '';
    DOM.availabilityResult.appendChild(p);
  } finally {
    DOM.checkAvailabilityBtn.disabled = false;
    DOM.availabilityResult.classList.remove('hidden');
  }
}

/**
 * 空席確認の結果を描画します。
 * XSS 対策: サーバーから受け取った値は textContent で表示します。
 */
function renderAvailabilityResult(data) {
  const container = DOM.availabilityResult;
  container.innerHTML = ''; // 既存の内容をクリア

  // 日時ラベル（textContent で XSS 対策）
  const dateLabel = document.createElement('p');
  dateLabel.style.fontSize = '0.85rem';
  dateLabel.style.color = 'var(--color-text-muted)';
  dateLabel.style.marginBottom = '8px';
  dateLabel.textContent = `${formatDate(data.date)} ${formatTime(data.time)}`;
  container.appendChild(dateLabel);

  // バッジ表示
  const badgesDiv = document.createElement('div');
  badgesDiv.className = 'availability-badges';

  const badge = document.createElement('span');
  if (data.isAvailable) {
    badge.className = 'badge badge--available';
    badge.textContent = `残り ${data.availableTables} テーブル`;
  } else {
    badge.className = 'badge badge--full';
    badge.textContent = '満席';
  }
  badgesDiv.appendChild(badge);

  const totalBadge = document.createElement('span');
  totalBadge.className = 'badge';
  totalBadge.style.background = '#f0f0ee';
  totalBadge.style.color = 'var(--color-text-muted)';
  totalBadge.textContent = `全 ${data.totalTables} テーブル`;
  badgesDiv.appendChild(totalBadge);

  container.appendChild(badgesDiv);
}

// ── 予約フォーム ──────────────────────────────────────────────────────

/**
 * フォーム送信イベントハンドラー。
 * @param {Event} event - submit イベント
 */
async function handleReservationSubmit(event) {
  event.preventDefault(); // デフォルトのフォーム送信（ページリロード）を防ぐ
  clearFieldErrors();

  // フォームデータを収集
  const requestData = {
    guestName:       DOM.guestName.value.trim(),
    guestEmail:      DOM.guestEmail.value.trim(),
    partySize:       parseInt(DOM.partySize.value, 10),
    reservationDate: DOM.reservationDate.value,
    reservationTime: DOM.reservationTime.value + ':00', // HH:mm → HH:mm:ss
    specialRequest:  DOM.specialRequest.value.trim() || null,
  };

  // 簡易クライアントサイドバリデーション（サーバーサイドのバリデーションと二重チェック）
  if (!requestData.guestName) {
    showFieldError('guest-name', 'お名前を入力してください');
    DOM.guestName.focus();
    return;
  }
  if (!requestData.guestEmail) {
    showFieldError('guest-email', 'メールアドレスを入力してください');
    return;
  }
  if (!requestData.reservationDate) {
    showFieldError('reservation-date', '予約日を選択してください');
    return;
  }
  if (!DOM.reservationTime.value) {
    showFieldError('reservation-time', '予約時間を選択してください');
    return;
  }
  if (!requestData.partySize || isNaN(requestData.partySize)) {
    showFieldError('party-size', '人数を選択してください');
    return;
  }

  showLoading('予約を確定しています...');

  try {
    const created = await createReservation(requestData);
    hideLoading();
    showSuccessModal(created);
    DOM.reservationForm.reset();
    DOM.charCount.textContent = '0 / 500';
  } catch (err) {
    hideLoading();
    if (err.data) {
      handleApiError(err.data);
    } else {
      showFormError('通信エラーが発生しました。ネットワーク接続をご確認ください。');
    }
  }
}

/**
 * 予約完了モーダルを表示します。
 * XSS 対策: サーバーから受け取った値は textContent で表示します。
 */
function showSuccessModal(reservation) {
  const body = DOM.modalBody;
  body.innerHTML = ''; // 既存をクリア（innerHTML は空にするだけ）

  // 各情報を createElement + textContent で安全に表示
  const info = [
    { label: '予約番号', value: `#${reservation.id}` },
    { label: 'お名前',   value: reservation.guestName },
    { label: '日時',     value: `${formatDate(reservation.reservationDate)} ${formatTime(reservation.reservationTime)}` },
    { label: '人数',     value: `${reservation.partySize}名様` },
  ];

  const dl = document.createElement('dl');
  dl.style.cssText = 'text-align:left; display:grid; grid-template-columns: 1fr 1fr; gap: 8px 16px;';

  info.forEach(({ label, value }) => {
    const dt = document.createElement('dt');
    dt.textContent = label; // ← textContent: XSS 安全
    dt.style.cssText = 'font-size:0.75rem; font-weight:700; color:var(--color-text-muted); letter-spacing:0.05em; text-transform:uppercase;';

    const dd = document.createElement('dd');
    dd.textContent = value; // ← textContent: XSS 安全
    dd.style.cssText = 'font-weight:600; color:var(--color-text);';

    dl.appendChild(dt);
    dl.appendChild(dd);
  });

  body.appendChild(dl);
  DOM.successModal.classList.remove('hidden');
}

// ── 予約一覧 ──────────────────────────────────────────────────────────

/**
 * 予約一覧を読み込んで表示します。
 * Neon (Singapore) との通信遅延を考慮し、スケルトンUI を表示します。
 */
async function loadReservationList() {
  showSkeleton();

  try {
    const reservations = await fetchAllReservations();
    hideSkeleton();
    renderReservationList(reservations);
  } catch (err) {
    hideSkeleton();
    DOM.reservationList.innerHTML = '';
    const p = document.createElement('p');
    p.textContent = '予約一覧の取得に失敗しました。再度お試しください。';
    p.className = 'empty-state';
    DOM.reservationList.appendChild(p);
  }
}

/**
 * 予約一覧を DOM に描画します。
 * XSS 対策: すべての動的な値は textContent を使用します。
 */
function renderReservationList(reservations) {
  const list = DOM.reservationList;
  list.innerHTML = ''; // 既存をクリア

  if (!reservations || reservations.length === 0) {
    const p = document.createElement('p');
    p.textContent = '現在、予約はありません。';
    p.className = 'empty-state';
    list.appendChild(p);
    return;
  }

  reservations.forEach(reservation => {
    const card = createReservationCard(reservation);
    list.appendChild(card);
  });
}

/**
 * 予約カードの DOM 要素を作成します。
 * createElement + textContent を使い、innerHTML にユーザーデータを混入しません。
 */
function createReservationCard(reservation) {
  const isCancelled = reservation.status === 'CANCELLED';

  // カード本体
  const article = document.createElement('article');
  article.className = `reservation-card${isCancelled ? ' is-cancelled' : ''}`;

  // カードヘッダー
  const header = document.createElement('div');
  header.className = 'card-header';

  const idSpan = document.createElement('span');
  idSpan.className = 'card-id';
  idSpan.textContent = `予約番号 #${reservation.id}`;

  const statusBadge = document.createElement('span');
  statusBadge.className = `status-badge ${isCancelled ? 'status-badge--cancelled' : 'status-badge--confirmed'}`;
  statusBadge.textContent = isCancelled ? 'キャンセル済み' : '確定';

  header.appendChild(idSpan);
  header.appendChild(statusBadge);

  // カードボディ
  const body = document.createElement('div');
  body.className = 'card-body';

  const fields = [
    { label: 'お名前',   value: reservation.guestName },
    { label: '人数',     value: `${reservation.partySize}名様` },
    { label: '日付',     value: formatDate(reservation.reservationDate) },
    { label: '時間',     value: formatTime(reservation.reservationTime) },
  ];

  fields.forEach(({ label, value }) => {
    const field = document.createElement('div');
    field.className = 'card-field';

    const labelEl = document.createElement('span');
    labelEl.className = 'card-field__label';
    labelEl.textContent = label; // textContent: XSS 安全

    const valueEl = document.createElement('span');
    valueEl.className = 'card-field__value';
    valueEl.textContent = value; // textContent: XSS 安全

    field.appendChild(labelEl);
    field.appendChild(valueEl);
    body.appendChild(field);
  });

  // カードフッター（キャンセルボタン）
  const footer = document.createElement('div');
  footer.className = 'card-footer';

  if (!isCancelled) {
    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'btn btn--small btn--secondary';
    cancelBtn.textContent = 'キャンセル';
    cancelBtn.addEventListener('click', () => handleCancelReservation(reservation.id, cancelBtn));
    footer.appendChild(cancelBtn);
  }

  article.appendChild(header);
  article.appendChild(body);
  article.appendChild(footer);

  return article;
}

/**
 * 予約キャンセル処理のハンドラー。
 */
async function handleCancelReservation(id, btn) {
  // 確認ダイアログ
  if (!confirm(`予約番号 #${id} をキャンセルしますか？\nこの操作は取り消せません。`)) {
    return;
  }

  btn.disabled = true;
  btn.textContent = 'キャンセル中...';

  try {
    await cancelReservation(id);
    // 一覧を再読み込み
    await loadReservationList();
  } catch (err) {
    btn.disabled = false;
    btn.textContent = 'キャンセル';
    const message = err.data?.message || 'キャンセルに失敗しました。';
    alert(message);
  }
}

// ── 初期化 ────────────────────────────────────────────────────────────

function init() {
  // 日付入力の最小値・最大値を設定（今日から1ヶ月後まで）
  const today = new Date();
  const maxDate = new Date();
  maxDate.setDate(maxDate.getDate() + 30);

  const toDateString = (d) => d.toISOString().split('T')[0];
  DOM.reservationDate.min = toDateString(today);
  DOM.reservationDate.max = toDateString(maxDate);
  DOM.checkDate.min = toDateString(today);
  DOM.checkDate.max = toDateString(maxDate);

  // タブの切り替えイベント
  DOM.tabBtns.forEach(btn => {
    btn.addEventListener('click', () => switchTab(btn.dataset.tab));
  });

  // 空席確認ボタン
  DOM.checkAvailabilityBtn.addEventListener('click', handleCheckAvailability);

  // 予約フォーム送信
  DOM.reservationForm.addEventListener('submit', handleReservationSubmit);

  // 特別なリクエストの文字数カウント
  DOM.specialRequest.addEventListener('input', () => {
    const count = DOM.specialRequest.value.length;
    DOM.charCount.textContent = `${count} / 500`;
  });

  // 一覧の更新ボタン
  DOM.refreshBtn.addEventListener('click', loadReservationList);

  // モーダルを閉じる
  DOM.modalCloseBtn.addEventListener('click', () => {
    DOM.successModal.classList.add('hidden');
  });
  DOM.modalOverlay.addEventListener('click', () => {
    DOM.successModal.classList.add('hidden');
  });

  // キーボード操作: Esc でモーダルを閉じる
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !DOM.successModal.classList.contains('hidden')) {
      DOM.successModal.classList.add('hidden');
    }
  });
}

// DOM が読み込まれたら設定を取得してから初期化
document.addEventListener('DOMContentLoaded', async () => {
  await loadConfig(); // バックエンド URL を Vercel 環境変数から取得
  init();
});
