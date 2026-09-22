/**
 * api.js
 * Quản lý cấu hình BASE_URL, wrapper fetch tự động gắn Bearer Token và Toast Notification
 */

const API_CONFIG = {
  BASE_URL: 'http://localhost:8080',
  TOKEN_KEY: 'iot_auth_token',
  USER_KEY: 'iot_user_info',
  DEFAULT_TIMEOUT: 10000 // 10s
};

/**
 * Hiển thị Toast thông báo nổi góc trên bên phải
 * @param {string} message - Nội dung thông báo
 * @param {'success' | 'error' | 'warning' | 'info'} type - Loại thông báo
 * @param {number} duration - Thời gian hiển thị (ms)
 */
function showToast(message, type = 'info', duration = 3500) {
  let container = document.querySelector('.toast-container');
  if (!container) {
    container = document.createElement('div');
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = `toast-item ${type}`;

  let iconSvg = '';
  if (type === 'success') {
    iconSvg = `<svg class="toast-icon" viewBox="0 0 24 24" fill="#05CD99"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>`;
  } else if (type === 'error') {
    iconSvg = `<svg class="toast-icon" viewBox="0 0 24 24" fill="#EE5D50"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>`;
  } else if (type === 'warning') {
    iconSvg = `<svg class="toast-icon" viewBox="0 0 24 24" fill="#FFB547"><path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/></svg>`;
  } else {
    iconSvg = `<svg class="toast-icon" viewBox="0 0 24 24" fill="#4318FF"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>`;
  }

  toast.innerHTML = `
    ${iconSvg}
    <div class="toast-message">${message}</div>
    <button class="toast-close" title="Đóng">&times;</button>
  `;

  const closeBtn = toast.querySelector('.toast-close');
  closeBtn.addEventListener('click', () => {
    toast.remove();
  });

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.animation = 'fadeOut 0.3s forwards';
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

/**
 * Hàm fetch bao bọc tự động gắn Authorization Header và xử lý lỗi tập trung
 * @param {string} endpoint - Đường dẫn API (ví dụ: /api/devices/status)
 * @param {object} options - Tùy chọn fetch (method, headers, body, v.v.)
 * @returns {Promise<any>}
 */
async function apiRequest(endpoint, options = {}) {
  const url = endpoint.startsWith('http') ? endpoint : `${API_CONFIG.BASE_URL}${endpoint}`;
  
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  const token = localStorage.getItem(API_CONFIG.TOKEN_KEY);
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), options.timeout || API_CONFIG.DEFAULT_TIMEOUT);

  try {
    const response = await fetch(url, {
      ...options,
      headers,
      signal: controller.signal
    });

    clearTimeout(timeoutId);

    // Xử lý lỗi xác thực 401 Unauthorized
    if (response.status === 401) {
      localStorage.removeItem(API_CONFIG.TOKEN_KEY);
      localStorage.removeItem(API_CONFIG.USER_KEY);
      showToast('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!', 'warning');
      setTimeout(() => {
        if (!window.location.pathname.endsWith('index.html') && window.location.pathname !== '/') {
          window.location.href = 'index.html';
        }
      }, 1200);
      throw new Error('401 Unauthorized');
    }

    // Nếu không OK, đọc phản hồi lỗi từ server
    if (!response.ok) {
      let errorData = null;
      try {
        errorData = await response.json();
      } catch (e) {
        errorData = { message: `Lỗi máy chủ (${response.status})` };
      }
      const errMsg = errorData.message || `Lỗi máy chủ: ${response.status} ${response.statusText}`;
      throw new Error(errMsg);
    }

    // Kiểm tra content-type để parse JSON hoặc text
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      const json = await response.json();
      // Tự động giải nén (unwrap) trường data nếu backend trả về chuẩn ApiResponse { status, message, data }
      if (json && typeof json === 'object' && 'status' in json && 'data' in json) {
        return json.data !== null && json.data !== undefined ? json.data : json;
      }
      return json;
    }
    return await response.text();

  } catch (error) {
    clearTimeout(timeoutId);
    if (error.name === 'AbortError') {
      showToast('Yêu cầu hết thời gian chờ (Timeout 10s)!', 'error');
      throw new Error('Request Timeout');
    }
    // Không bắn toast nếu là endpoint polling ngầm để tránh spam màn hình
    if (!endpoint.includes('/latest')) {
      showToast(error.message || 'Lỗi kết nối đến máy chủ Backend!', 'error');
    }
    throw error;
  }
}

