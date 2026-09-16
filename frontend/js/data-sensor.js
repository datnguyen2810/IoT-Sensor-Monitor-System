/**
 * data-sensor.js
 * Logic trang Lịch sử đo cảm biến
 */

if (typeof Auth !== 'undefined') {
  Auth.requireAuth();
}

let currentPage = 1;
const limit = 10;
let totalPages = 1;
let totalRecords = 0;

/**
 * Tải dữ liệu lịch sử cảm biến từ API
 */
async function loadSensorHistory() {
  const sensorType = document.getElementById('sensorTypeFilter').value;
  const search = document.getElementById('timeSearchInput').value.trim();
  const sort = document.getElementById('sortOrder').value;
  const tbody = document.getElementById('sensorTableBody');

  tbody.innerHTML = `
    <tr>
      <td colspan="4" class="table-empty">Đang tải dữ liệu...</td>
    </tr>
  `;

  try {
    const queryParams = new URLSearchParams({
      page: currentPage,
      limit: limit,
      search: search,
      sensor_type: sensorType,
      sort: sort
    });

    const result = await apiRequest(`/api/sensors/history?${queryParams.toString()}`);

    if (result && Array.isArray(result.data)) {
      totalRecords = result.total_records || 0;
      totalPages = result.total_pages || Math.ceil(totalRecords / limit) || 1;
      currentPage = result.current_page || currentPage;

      renderTableData(result.data);
      renderPagination(totalPages, currentPage);
    } else {
      tbody.innerHTML = `
        <tr>
          <td colspan="4" class="table-empty">Không tìm thấy dữ liệu cảm biến phù hợp.</td>
        </tr>
      `;
      renderPagination(1, 1);
    }
  } catch (error) {
    console.warn('Backend offline, hiển thị dữ liệu mẫu cho data-sensor:', error.message);
    // Dữ liệu mẫu
    const mockData = [
      { id: 1, sensor_name: "Ánh Sáng", value: "850", created_at: "2026-08-16 14:37:48" },
      { id: 2, sensor_name: "Độ Ẩm", value: "83", created_at: "2026-08-16 14:37:48" },
      { id: 3, sensor_name: "Nhiệt Độ", value: "30.5", created_at: "2026-08-16 14:37:48" },
      { id: 4, sensor_name: "Ánh Sáng", value: "754", created_at: "2026-08-16 14:38:01" },
      { id: 5, sensor_name: "Độ Ẩm", value: "83", created_at: "2026-08-16 14:38:01" },
      { id: 6, sensor_name: "Nhiệt Độ", value: "30.5", created_at: "2026-08-16 14:38:01" }
    ];
    totalPages = 10;
    renderTableData(mockData);
    renderPagination(10, currentPage);
  }
}

/**
 * Hiển thị dữ liệu vào bảng
 * @param {Array<object>} items 
 */
function renderTableData(items) {
  const tbody = document.getElementById('sensorTableBody');

  if (items.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="4" class="table-empty">Không có dữ liệu hiển thị.</td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = items.map(item => {
    // Xác định đơn vị nếu API không trả về
    let unit = item.unit || '';
    if (!unit) {
      if (item.sensor_name && item.sensor_name.toLowerCase().includes('nhiệt')) unit = '°C';
      else if (item.sensor_name && item.sensor_name.toLowerCase().includes('độ ẩm')) unit = '%';
      else if (item.sensor_name && item.sensor_name.toLowerCase().includes('sáng')) unit = 'Lux';
    }

    const valStr = `${item.value} ${unit}`.trim();

    return `
      <tr>
        <td>${item.id}</td>
        <td>${item.sensor_name || '--'}</td>
        <td>${valStr}</td>
        <td>${item.created_at || '--'}</td>
      </tr>
    `;
  }).join('');
}

/**
 * Vẽ thanh phân trang
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

  // Tạo danh sách các số trang cần hiển thị
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
 * Chuyển trang
 * @param {number} page 
 */
function goToPage(page) {
  if (page < 1 || page > totalPages || page === currentPage) return;
  currentPage = page;
  loadSensorHistory();
}

/**
 * Đăng ký các sự kiện tương tác
 */
document.addEventListener('DOMContentLoaded', () => {
  const btnSearch = document.getElementById('btnSearch');
  const searchInput = document.getElementById('timeSearchInput');
  const sensorTypeFilter = document.getElementById('sensorTypeFilter');
  const sortOrder = document.getElementById('sortOrder');

  // Nút tìm kiếm
  btnSearch.addEventListener('click', () => {
    currentPage = 1;
    loadSensorHistory();
  });

  // Nhấn Enter trong ô tìm kiếm
  searchInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
      currentPage = 1;
      loadSensorHistory();
    }
  });

  // Khi thay đổi loại cảm biến
  sensorTypeFilter.addEventListener('change', () => {
    currentPage = 1;
    loadSensorHistory();
  });

  // Khi thay đổi thứ tự sắp xếp
  sortOrder.addEventListener('change', () => {
    currentPage = 1;
    loadSensorHistory();
  });

  // Tải dữ liệu ban đầu
  loadSensorHistory();
});

