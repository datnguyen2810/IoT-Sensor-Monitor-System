/**
 * history.js
 * Logic trang Lịch sử điều khiển thiết bị
 */

if (typeof Auth !== 'undefined') {
  Auth.requireAuth();
}

let currentPage = 1;
const limit = 10;
let totalPages = 1;
let totalRecords = 0;

/**
 * Tải danh sách lịch sử điều khiển thiết bị từ API
 */
async function loadDeviceHistory() {
  const device = document.getElementById('deviceFilter').value;
  const status = document.getElementById('statusFilter').value;
  const search = document.getElementById('timeSearchInput').value.trim();
  const sort = document.getElementById('sortOrder').value;
  const tbody = document.getElementById('historyTableBody');

  tbody.innerHTML = `
    <tr>
      <td colspan="5" class="table-empty">Đang tải dữ liệu...</td>
    </tr>
  `;

  try {
    const queryParams = new URLSearchParams({
      page: currentPage,
      limit: limit,
      search: search,
      device: device,
      status: status,
      sort: sort
    });

    const result = await apiRequest(`/api/devices/history?${queryParams.toString()}`);

    if (result && Array.isArray(result.data)) {
      totalRecords = result.total_records || 0;
      totalPages = result.total_pages || Math.ceil(totalRecords / limit) || 1;
      currentPage = result.current_page || currentPage;

      renderTableData(result.data);
      renderPagination(totalPages, currentPage);
    } else {
      tbody.innerHTML = `
        <tr>
          <td colspan="5" class="table-empty">Không tìm thấy bản ghi lịch sử điều khiển nào.</td>
        </tr>
      `;
      renderPagination(1, 1);
    }
  } catch (error) {
    console.warn('Backend offline, hiển thị dữ liệu mẫu cho history:', error.message);
    // Dữ liệu mẫu
    const mockData = [
      { id: 1, device_name: "Điều hoà", action_sent: "ON", status_received: "ON", executed_at: "2026-08-16 16:44:14" },
      { id: 2, device_name: "Điều hoà", action_sent: "OFF", status_received: "OFF", executed_at: "2026-08-16 16:44:35" },
      { id: 3, device_name: "Đèn LED", action_sent: "ON", status_received: "ON", executed_at: "2026-08-16 10:59:45" },
      { id: 4, device_name: "Quạt", action_sent: "OFF", status_received: "OFF", executed_at: "2026-08-16 10:59:48" },
      { id: 5, device_name: "Đèn LED", action_sent: "OFF", status_received: "OFF", executed_at: "2026-08-16 10:59:54" },
      { id: 6, device_name: "Điều hoà", action_sent: "ON", status_received: "ON", executed_at: "2026-08-16 11:45:55" }
    ];
    totalPages = 10;
    renderTableData(mockData);
    renderPagination(10, currentPage);
  }
}

/**
 * Render dữ liệu vào các hàng của bảng
 * @param {Array<object>} items 
 */
function renderTableData(items) {
  const tbody = document.getElementById('historyTableBody');

  if (items.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5" class="table-empty">Không có dữ liệu hiển thị.</td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = items.map(item => {
    const action = item.action_sent || item.action || '--';
    const status = item.status_received || item.status || '--';
    const statusClass = (status.toUpperCase() === 'ON') ? 'on' : 'off';
    const time = item.executed_at || item.created_at || item.timestamp || '--';

    return `
      <tr>
        <td>${item.id}</td>
        <td>${item.device_name || '--'}</td>
        <td style="font-weight: 700;">${action}</td>
        <td>
          <span class="badge-status ${statusClass}">${status}</span>
        </td>
        <td>${time}</td>
      </tr>
    `;
  }).join('');
}

/**
 * Render các nút phân trang
 * @param {number} total 
 * @param {number} current 
 */
function renderPagination(total, current) {
  const container = document.getElementById('pagination');
  if (!container) return;

  if (total <= 1) {
    container.innerHTML = '';
    return;
  }

  let html = '';

  // Nút Prev (<)
  html += `
    <button class="page-btn" ${current === 1 ? 'disabled' : ''} onclick="goToPage(${current - 1})" title="Trang trước">
      &lt;
    </button>
  `;

  // Danh sách trang
  const pages = [];
  if (total <= 7) {
    for (let i = 1; i <= total; i++) pages.push(i);
  } else {
    pages.push(1);
    if (current > 3) pages.push('...');
    
    const start = Math.max(2, current - 1);
    const end = Math.min(total - 1, current + 1);
    for (let i = start; i <= end; i++) {
      pages.push(i);
    }

    if (current < total - 2) pages.push('...');
    pages.push(total);
  }

  pages.forEach(p => {
    if (p === '...') {
      html += `<span class="page-dots">...</span>`;
    } else {
      html += `
        <button class="page-btn ${p === current ? 'active' : ''}" onclick="goToPage(${p})">
          ${p}
        </button>
      `;
    }
  });

  // Nút Next (>)
  html += `
    <button class="page-btn" ${current === total ? 'disabled' : ''} onclick="goToPage(${current + 1})" title="Trang sau">
      &gt;
    </button>
  `;

  container.innerHTML = html;
}

/**
 * Chuyển tới trang chỉ định
 * @param {number} page 
 */
function goToPage(page) {
  if (page < 1 || page > totalPages || page === currentPage) return;
  currentPage = page;
  loadDeviceHistory();
}

/**
 * Khởi tạo sự kiện
 */
document.addEventListener('DOMContentLoaded', () => {
  const btnSearch = document.getElementById('btnSearch');
  const searchInput = document.getElementById('timeSearchInput');
  const deviceFilter = document.getElementById('deviceFilter');
  const statusFilter = document.getElementById('statusFilter');
  const sortOrder = document.getElementById('sortOrder');

  btnSearch.addEventListener('click', () => {
    currentPage = 1;
    loadDeviceHistory();
  });

  searchInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
      currentPage = 1;
      loadDeviceHistory();
    }
  });

  deviceFilter.addEventListener('change', () => {
    currentPage = 1;
    loadDeviceHistory();
  });

  statusFilter.addEventListener('change', () => {
    currentPage = 1;
    loadDeviceHistory();
  });

  sortOrder.addEventListener('change', () => {
    currentPage = 1;
    loadDeviceHistory();
  });

  // Tải dữ liệu ban đầu
  loadDeviceHistory();
});

