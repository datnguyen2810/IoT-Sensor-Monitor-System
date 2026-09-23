/**
 * Logic trang Lịch sử đo cảm biến
 */

if (typeof Auth !== 'undefined') {
  Auth.requireAuth();
}

let currentPage = 1;
let limit = 10;
let totalPages = 1;
let totalRecords = 0;

/**
 * Tải dữ liệu lịch sử cảm biến từ API
 */
async function loadSensorHistory() {
  const pageSizeSelect = document.getElementById('pageSizeSelect');
  if (pageSizeSelect) {
    limit = parseInt(pageSizeSelect.value, 10) || limit;
  }
  const sensorType = document.getElementById('sensorTypeFilter')?.value || '';
  const search = document.getElementById('timeSearchInput')?.value.trim() || '';
  const sort = document.getElementById('sortOrder')?.value || 'desc';
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
      sort: sort
    });

    if (sensorType === 'Thời Gian') {
      // Khi chọn Thời Gian: tìm kiếm chuỗi thời gian
      queryParams.set('search', search);
      queryParams.set('sensor_type', '');
      queryParams.set('search_type', 'time');
    } else if (sensorType) {
      // Khi chọn loại cảm biến cụ thể: lọc theo loại và giá trị
      queryParams.set('sensor_type', sensorType);
      queryParams.set('search', search);
      queryParams.set('search_type', 'value');
    } else {
      // Khi chọn Tất cả
      queryParams.set('sensor_type', '');
      queryParams.set('search', search);
    }

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
    const rawMockData = [
      { id: 1, sensor_name: "Ánh Sáng", value: "850", created_at: "2026-08-16 14:37:48" },
      { id: 2, sensor_name: "Độ Ẩm", value: "83", created_at: "2026-08-16 14:37:48" },
      { id: 3, sensor_name: "Nhiệt Độ", value: "30.5", created_at: "2026-08-16 14:37:48" },
      { id: 4, sensor_name: "Ánh Sáng", value: "754", created_at: "2026-08-16 14:38:01" },
      { id: 5, sensor_name: "Độ Ẩm", value: "83", created_at: "2026-08-16 14:38:01" },
      { id: 6, sensor_name: "Nhiệt Độ", value: "30.5", created_at: "2026-08-16 14:38:01" },
      { id: 7, sensor_name: "Ánh Sáng", value: "620", created_at: "2026-08-16 14:39:15" },
      { id: 8, sensor_name: "Độ Ẩm", value: "80", created_at: "2026-08-16 14:39:15" },
      { id: 9, sensor_name: "Nhiệt Độ", value: "29.8", created_at: "2026-08-16 14:39:15" },
      { id: 10, sensor_name: "Ánh Sáng", value: "910", created_at: "2026-08-16 14:40:30" },
      { id: 11, sensor_name: "Độ Ẩm", value: "78", created_at: "2026-08-16 14:40:30" },
      { id: 12, sensor_name: "Nhiệt Độ", value: "31.2", created_at: "2026-08-16 14:40:30" }
    ];

    let filtered = [...rawMockData];

    if (sensorType === 'Thời Gian') {
      if (search) {
        filtered = filtered.filter(item => (item.created_at || '').includes(search));
      }
    } else if (sensorType) {
      filtered = filtered.filter(item => item.sensor_name === sensorType);
      if (search) {
        filtered = filtered.filter(item => String(item.value).includes(search));
      }
    } else {
      if (search) {
        filtered = filtered.filter(item =>
          item.sensor_name.toLowerCase().includes(search.toLowerCase()) ||
          String(item.value).includes(search) ||
          (item.created_at || '').includes(search)
        );
      }
    }

    if (sort === 'asc') {
      filtered.sort((a, b) => a.id - b.id);
    } else {
      filtered.sort((a, b) => b.id - a.id);
    }

    totalRecords = filtered.length;
    totalPages = Math.max(10, Math.ceil(totalRecords / limit));
    const pagedItems = filtered.slice((currentPage - 1) * limit, currentPage * limit);
    renderTableData(pagedItems.length > 0 ? pagedItems : filtered.slice(0, limit));
    renderPagination(totalPages, currentPage);
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
  const currentEl = document.getElementById('currentPageDisplay');
  const totalEl = document.getElementById('totalPagesDisplay');
  if (currentEl) currentEl.textContent = current;
  if (totalEl) totalEl.textContent = total;

  const container = document.getElementById('pagination');
  if (!container) return;

  if (total <= 0) {
    container.innerHTML = '';
    return;
  }

  if (total === 1) {
    container.innerHTML = `
      <button class="page-btn" disabled title="Trang trước">&lt;</button>
      <button class="page-btn active">1</button>
      <button class="page-btn" disabled title="Trang sau">&gt;</button>
    `;
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

  // Khi thay đổi loại cảm biến / tiêu chí lọc
  sensorTypeFilter.addEventListener('change', () => {
    if (sensorTypeFilter.value === 'Thời Gian') {
      searchInput.placeholder = 'Nhập thời gian (VD: 14:37 hoặc 2026-08-16)';
    } else if (sensorTypeFilter.value) {
      searchInput.placeholder = `Nhập giá trị ${sensorTypeFilter.value.toLowerCase()}...`;
    } else {
      searchInput.placeholder = 'Nhập giá trị';
    }
    currentPage = 1;
    loadSensorHistory();
  });

  // Khi thay đổi thứ tự sắp xếp
  sortOrder.addEventListener('change', () => {
    currentPage = 1;
    loadSensorHistory();
  });

  const pageSizeSelect = document.getElementById('pageSizeSelect');
  if (pageSizeSelect) {
    pageSizeSelect.addEventListener('change', (e) => {
      limit = parseInt(e.target.value, 10) || 10;
      currentPage = 1;
      loadSensorHistory();
    });
  }

  // Tải dữ liệu ban đầu
  loadSensorHistory();
});

