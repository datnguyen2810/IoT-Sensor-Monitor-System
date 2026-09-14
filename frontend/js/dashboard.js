/**
 * dashboard.js - IoT Sensor Monitor System
 * Logic trang Giám sát thông số cảm biến & Điều khiển thiết bị
 */

// Đảm bảo người dùng đã đăng nhập
if (typeof Auth !== 'undefined') {
  Auth.requireAuth();
}

let sensorChart = null;
let lastChartTimestamp = null;
let pollingTimer = null;

// Tên hiển thị thân thiện cho thiết bị
const DEVICE_NAMES = {
  led: 'Đèn LED',
  fan: 'Quạt',
  ac: 'Điều hoà'
};

/**
 * Khởi tạo biểu đồ Chart.js với 3 đường cảm biến
 * @param {Array<{time: string, temperature: number, humidity: number, light: number}>} chartData 
 */
function initChart(chartData = []) {
  const ctx = document.getElementById('sensorChart');
  if (!ctx) return;

  const labels = chartData.map(item => item.time || '');
  const tempData = chartData.map(item => item.temperature || 0);
  const humidityData = chartData.map(item => item.humidity || 0);
  const lightData = chartData.map(item => item.light || 0);

  // Lưu lại mốc thời gian điểm cuối cùng
  if (chartData.length > 0) {
    lastChartTimestamp = chartData[chartData.length - 1].time;
  }

  sensorChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: labels,
      datasets: [
        {
          label: 'Nhiệt độ (°C)',
          data: tempData,
          borderColor: '#4318FF',
          backgroundColor: 'rgba(67, 24, 255, 0.05)',
          borderWidth: 2.5,
          tension: 0.4,
          pointRadius: 2.5,
          pointHoverRadius: 6,
          pointBackgroundColor: '#4318FF',
          fill: false
        },
        {
          label: 'Độ ẩm (%)',
          data: humidityData,
          borderColor: '#EE5D50',
          backgroundColor: 'rgba(238, 93, 80, 0.05)',
          borderWidth: 2.5,
          tension: 0.4,
          pointRadius: 2.5,
          pointHoverRadius: 6,
          pointBackgroundColor: '#EE5D50',
          fill: false
        },
        {
          label: 'Ánh sáng (Lux)',
          data: lightData,
          borderColor: '#FFB547',
          backgroundColor: 'rgba(255, 181, 71, 0.05)',
          borderWidth: 2.5,
          tension: 0.4,
          pointRadius: 2.5,
          pointHoverRadius: 6,
          pointBackgroundColor: '#FFB547',
          fill: false
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: {
          display: false // Sử dụng legend tùy chỉnh bên ngoài HTML
        },
        tooltip: {
          backgroundColor: '#1B254B',
          titleFont: { family: 'Plus Jakarta Sans', size: 12, weight: '700' },
          bodyFont: { family: 'Plus Jakarta Sans', size: 12, weight: '600' },
          padding: 10,
          cornerRadius: 10,
          displayColors: true
        }
      },
      scales: {
        x: {
          grid: {
            display: false,
            drawBorder: false
          },
          ticks: {
            color: '#A3AED0',
            font: { family: 'Plus Jakarta Sans', size: 11, weight: '600' },
            maxRotation: 0,
            autoSkip: true,
            maxTicksLimit: 8
          }
        },
        y: {
          beginAtZero: true,
          grid: {
            color: '#E0E5F2',
            borderDash: [5, 5],
            drawBorder: false
          },
          ticks: {
            color: '#A3AED0',
            font: { family: 'Plus Jakarta Sans', size: 11, weight: '600' },
            stepSize: 100
          }
        }
      }
    }
  });
}

/**
 * Tải trạng thái ban đầu của 3 thiết bị
 */
async function loadDeviceStatuses() {
  try {
    const devices = await apiRequest('/api/devices/status');
    if (Array.isArray(devices)) {
      devices.forEach(item => {
        const switchElem = document.querySelector(`input[data-device="${item.device}"]`);
        if (switchElem) {
          switchElem.checked = (item.status === 'ON');
        }
      });
    }
  } catch (error) {
    console.warn('Không thể tải trạng thái thiết bị ban đầu:', error.message);
  }
}

/**
 * Tải 15 bản ghi dữ liệu cảm biến ban đầu cho biểu đồ
 */
async function loadInitialChartData() {
  try {
    const chartData = await apiRequest('/api/sensors/chart');
    if (Array.isArray(chartData) && chartData.length > 0) {
      initChart(chartData);
      
      // Đồng thời cập nhật luôn 3 Card số liệu từ điểm gần nhất
      const latestPoint = chartData[chartData.length - 1];
      updateSensorCards(latestPoint.temperature, latestPoint.light, latestPoint.humidity);
    } else {
      // Dữ liệu mẫu ban đầu nếu backend trả về rỗng
      initChart([]);
    }
  } catch (error) {
    console.warn('Backend offline, sử dụng dữ liệu mẫu cho biểu đồ:', error.message);
    // Dữ liệu mẫu khớp với hình thiết kế khi backend chưa bật
    const mockPoints = [
      { time: "17:15:00", temperature: 35.0, humidity: 55, light: 510 },
      { time: "17:16:00", temperature: 35.2, humidity: 54, light: 515 },
      { time: "17:17:00", temperature: 35.4, humidity: 55, light: 520 },
      { time: "17:18:00", temperature: 35.5, humidity: 56, light: 518 },
      { time: "17:19:00", temperature: 35.6, humidity: 55, light: 512 },
      { time: "17:20:00", temperature: 35.5, humidity: 55, light: 505 },
      { time: "17:21:00", temperature: 35.4, humidity: 56, light: 498 },
      { time: "17:22:00", temperature: 35.3, humidity: 57, light: 490 },
      { time: "17:23:00", temperature: 35.4, humidity: 56, light: 495 },
      { time: "17:24:00", temperature: 35.5, humidity: 55, light: 508 },
      { time: "17:25:00", temperature: 35.6, humidity: 55, light: 520 },
      { time: "17:26:00", temperature: 35.7, humidity: 54, light: 528 },
      { time: "17:27:00", temperature: 35.6, humidity: 55, light: 534 }
    ];
    initChart(mockPoints);
    updateSensorCards(35.6, 534, 55);
  }
}

/**
 * Cập nhật số liệu hiển thị trên 3 Card cảm biến
 */
function updateSensorCards(temp, light, humidity) {
  const tempEl = document.getElementById('tempValue');
  const lightEl = document.getElementById('lightValue');
  const humidityEl = document.getElementById('humidityValue');

  if (tempEl && temp !== undefined && temp !== null) {
    tempEl.textContent = Number(temp).toFixed(1);
  }
  if (lightEl && light !== undefined && light !== null) {
    lightEl.textContent = Math.round(light);
  }
  if (humidityEl && humidity !== undefined && humidity !== null) {
    humidityEl.textContent = Math.round(humidity);
  }
}

/**
 * Polling định kỳ mỗi 2 giây gọi GET /api/sensors/latest
 */
async function pollLatestSensorData() {
  try {
    const data = await apiRequest('/api/sensors/latest');
    if (!data) return;

    // 1. Cập nhật ngay giá trị vào 3 Card
    updateSensorCards(data.temperature, data.light, data.humidity);

    // 2. Tối ưu trượt biểu đồ: kiểm tra timestamp khác biệt
    const recordTime = data.timestamp || '';
    if (recordTime && recordTime !== lastChartTimestamp && sensorChart) {
      lastChartTimestamp = recordTime;

      // Định dạng mốc giờ hiển thị trên trục hoành (lấy HH:mm:ss nếu có)
      const displayLabel = recordTime.includes(' ') ? recordTime.split(' ')[1] : recordTime;

      // Push điểm mới vào cuối
      sensorChart.data.labels.push(displayLabel);
      sensorChart.data.datasets[0].data.push(Number(data.temperature) || 0);
      sensorChart.data.datasets[1].data.push(Number(data.humidity) || 0);
      sensorChart.data.datasets[2].data.push(Number(data.light) || 0);

      // Nếu biểu đồ vượt quá 15 điểm, loại bỏ điểm đầu tiên
      if (sensorChart.data.labels.length > 15) {
        sensorChart.data.labels.shift();
        sensorChart.data.datasets.forEach(ds => ds.data.shift());
      }

      // Cập nhật không có animation giật để tạo hiệu ứng trượt mượt mà
      sensorChart.update('none');
    }
  } catch (error) {
    // Polling ngầm bỏ qua lỗi network để tránh gián đoạn
    console.debug('Polling latest data:', error.message);
  }
}

/**
 * Gắn sự kiện cho các công tắc điều khiển thiết bị
 */
function setupDeviceControls() {
  const switches = document.querySelectorAll('.switch-control input[type="checkbox"]');

  switches.forEach(switchInput => {
    switchInput.addEventListener('change', async function () {
      const device = this.getAttribute('data-device');
      const action = this.checked ? 'ON' : 'OFF';
      const previousChecked = !this.checked;
      const deviceLabel = DEVICE_NAMES[device] || device;
      const parentLabel = this.closest('.switch-control');

      // Tạm khóa công tắc và bật hiệu ứng loading
      this.disabled = true;
      if (parentLabel) parentLabel.classList.add('loading');

      try {
        const response = await apiRequest('/api/devices/control', {
          method: 'POST',
          body: JSON.stringify({ device, action })
        });

        // Kiểm tra phản hồi thành công (200 OK)
        if (response && (response.status === 'success' || response.device_status === action)) {
          this.checked = (action === 'ON');
          showToast(`Đã chuyển trạng thái ${deviceLabel} sang: ${action}`, 'success');
        } else {
          throw new Error(response.message || 'Phản hồi không thành công');
        }
      } catch (error) {
        // Rollback hoàn trả vị trí công tắc về trạng thái trước đó
        this.checked = previousChecked;
        showToast(`Lỗi điều khiển ${deviceLabel}: ${error.message || 'Vui lòng thử lại'}`, 'error');
      } finally {
        // Mở khóa lại công tắc
        this.disabled = false;
        if (parentLabel) parentLabel.classList.remove('loading');
      }
    });
  });
}

/**
 * Khởi động toàn bộ trang Dashboard khi DOM sẵn sàng
 */
document.addEventListener('DOMContentLoaded', async () => {
  // 1. Cài đặt tương tác công tắc
  setupDeviceControls();

  // 2. Khởi tạo dữ liệu ban đầu
  await Promise.allSettled([
    loadDeviceStatuses(),
    loadInitialChartData()
  ]);

  // 3. Kích hoạt chu kỳ Polling mỗi 2s
  pollLatestSensorData(); // Gọi ngay lần đầu
  pollingTimer = setInterval(pollLatestSensorData, 2000);
});

// Dọn dẹp timer polling khi rời khỏi trang
window.addEventListener('beforeunload', () => {
  if (pollingTimer) {
    clearInterval(pollingTimer);
  }
});

