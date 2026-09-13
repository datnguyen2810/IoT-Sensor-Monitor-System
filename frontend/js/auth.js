/**
 * auth.js - IoT Sensor Monitor System
 * Quản lý phiên đăng nhập, Auth Guard và Đăng xuất
 */

const Auth = {
  /**
   * Kiểm tra Auth Guard cho các trang cần bảo vệ
   * Chưa đăng nhập sẽ tự động chuyển hướng về index.html
   */
  requireAuth() {
    const token = localStorage.getItem(API_CONFIG.TOKEN_KEY);
    if (!token) {
      window.location.href = 'index.html';
      return false;
    }
    return true;
  },

  /**
   * Kiểm tra nếu đã đăng nhập thì tự động chuyển vào dashboard.html (dùng cho index.html)
   */
  redirectIfAuthenticated() {
    const token = localStorage.getItem(API_CONFIG.TOKEN_KEY);
    if (token) {
      window.location.href = 'dashboard.html';
    }
  },

  /**
   * Xử lý đăng nhập
   * @param {string} username 
   * @param {string} password 
   * @param {boolean} rememberMe 
   */
  async login(username, password, rememberMe = false) {
    try {
      const response = await apiRequest('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password })
      });

      if (response && response.token) {
        localStorage.setItem(API_CONFIG.TOKEN_KEY, response.token);
        localStorage.setItem(API_CONFIG.USER_KEY, JSON.stringify({
          name: 'Nguyễn Xuân Đạt',
          username: username
        }));

        if (rememberMe) {
          localStorage.setItem('iot_remember_username', username);
        } else {
          localStorage.removeItem('iot_remember_username');
        }

        showToast('Đăng nhập thành công!', 'success');
        setTimeout(() => {
          window.location.href = 'dashboard.html';
        }, 800);
        return true;
      } else {
        showToast('Phản hồi đăng nhập không hợp lệ!', 'error');
        return false;
      }
    } catch (error) {
      // Nếu máy chủ Backend chưa bật, hỗ trợ tự động kích hoạt Demo Token để tiện xem giao diện
      console.warn('Backend offline, kích hoạt chế độ xem thử (Demo Mode):', error);
      const useDemo = confirm('Máy chủ Backend (localhost:8080) chưa được bật. Bạn có muốn kích hoạt chế độ Xem thử (Demo Mode) để vào thẳng Dashboard xem giao diện không?');
      if (useDemo) {
        localStorage.setItem(API_CONFIG.TOKEN_KEY, 'demo_token_frontend_preview_2026');
        localStorage.setItem(API_CONFIG.USER_KEY, JSON.stringify({
          name: 'Nguyễn Xuân Đạt',
          username: username || 'admin'
        }));
        showToast('Đã kích hoạt chế độ Demo để xem giao diện!', 'success');
        setTimeout(() => {
          window.location.href = 'dashboard.html';
        }, 600);
        return true;
      }
      return false;
    }
  },

  /**
   * Đăng xuất khỏi hệ thống
   */
  logout() {
    localStorage.removeItem(API_CONFIG.TOKEN_KEY);
    localStorage.removeItem(API_CONFIG.USER_KEY);
    showToast('Đã đăng xuất khỏi hệ thống!', 'info');
    setTimeout(() => {
      window.location.href = 'index.html';
    }, 600);
  }
};

// Gắn sự kiện đăng xuất cho nút logout trên top header nếu có
document.addEventListener('DOMContentLoaded', () => {
  const logoutBtn = document.getElementById('btnLogout');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', (e) => {
      e.preventDefault();
      Auth.logout();
    });
  }

  // Hỗ trợ nút mở menu mobile trên màn hình nhỏ
  const mobileBtn = document.getElementById('mobileMenuBtn');
  const sidebar = document.querySelector('.sidebar');
  if (mobileBtn && sidebar) {
    mobileBtn.addEventListener('click', () => {
      sidebar.classList.toggle('show');
    });
  }
});

