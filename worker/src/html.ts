export function renderDashboardHtml(): string {
  return `<!doctype html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Personal Data Hub - Cloudflare D1</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #f8fafc;
      --card-bg: #ffffff;
      --card-border: #e2e8f0;
      --text-main: #0f172a;
      --text-muted: #64748b;
      --primary: #4f46e5;
      --primary-hover: #4338ca;
      --primary-light: #e0e7ff;
      --success: #10b981;
      --success-light: #ecfdf5;
      --warning: #f59e0b;
      --warning-light: #fef3c7;
      --radius: 12px;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background-color: var(--bg);
      color: var(--text-main);
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 14px;
      line-height: 1.5;
    }
    .container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 24px 20px 80px;
    }
    /* Header */
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      padding-bottom: 20px;
      border-bottom: 1px solid var(--card-border);
      flex-wrap: wrap;
      gap: 16px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .brand-icon {
      width: 42px;
      height: 42px;
      border-radius: 10px;
      background: #0f172a;
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
    }
    .brand-title {
      font-size: 20px;
      font-weight: 700;
      letter-spacing: -0.02em;
    }
    .brand-subtitle {
      font-size: 12px;
      color: var(--text-muted);
    }
    .badges {
      display: flex;
      gap: 8px;
      align-items: center;
      flex-wrap: wrap;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 9999px;
      font-size: 12px;
      font-weight: 500;
      border: 1px solid var(--card-border);
      background: #ffffff;
    }
    .badge-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--success);
    }
    /* Tabs */
    .tabs {
      display: flex;
      gap: 6px;
      background: #e2e8f0;
      padding: 4px;
      border-radius: 10px;
      margin-bottom: 24px;
      overflow-x: auto;
    }
    .tab-btn {
      padding: 8px 18px;
      border: 0;
      background: transparent;
      border-radius: 7px;
      font-size: 13.5px;
      font-weight: 500;
      color: var(--text-muted);
      cursor: pointer;
      white-space: nowrap;
      transition: all 0.15s;
    }
    .tab-btn:hover {
      color: var(--text-main);
    }
    .tab-btn.active {
      background: #ffffff;
      color: var(--text-main);
      font-weight: 600;
      box-shadow: 0 1px 3px rgba(0,0,0,0.08);
    }
    .tab-content {
      display: none;
    }
    .tab-content.active {
      display: block;
    }
    /* Metrics */
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
    }
    .metric-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: var(--radius);
      padding: 18px 20px;
      box-shadow: 0 1px 2px rgba(0,0,0,0.03);
    }
    .metric-label {
      font-size: 13px;
      color: var(--text-muted);
      margin-bottom: 8px;
      font-weight: 500;
    }
    .metric-value {
      font-size: 28px;
      font-weight: 700;
      letter-spacing: -0.02em;
    }
    .metric-sub {
      font-size: 12px;
      color: var(--text-muted);
      margin-top: 4px;
    }
    /* Cards */
    .card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: var(--radius);
      padding: 20px;
      margin-bottom: 20px;
      box-shadow: 0 1px 2px rgba(0,0,0,0.03);
    }
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }
    .card-title {
      font-size: 16px;
      font-weight: 600;
    }
    /* Item List & Cards */
    .grid-cards {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 16px;
    }
    .item-card {
      background: #ffffff;
      border: 1px solid var(--card-border);
      border-radius: var(--radius);
      padding: 14px;
      display: flex;
      gap: 14px;
      transition: transform 0.1s, box-shadow 0.1s;
    }
    .item-card:hover {
      box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
    }
    .item-img {
      width: 60px;
      height: 84px;
      border-radius: 6px;
      object-fit: cover;
      background: #f1f5f9;
      flex-shrink: 0;
    }
    .item-body {
      flex: 1;
      min-width: 0;
    }
    .item-title {
      font-size: 14px;
      font-weight: 600;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .item-subtitle {
      font-size: 12.5px;
      color: var(--text-muted);
      margin-top: 2px;
    }
    .item-meta {
      font-size: 12px;
      color: var(--text-muted);
      margin-top: 6px;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    /* Timeline */
    .timeline-group {
      margin-bottom: 20px;
    }
    .timeline-header {
      font-size: 13px;
      font-weight: 600;
      color: var(--text-muted);
      margin-bottom: 10px;
      display: flex;
      justify-content: space-between;
      border-bottom: 1px solid var(--card-border);
      padding-bottom: 4px;
    }
    .timeline-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 14px;
      background: #ffffff;
      border: 1px solid var(--card-border);
      border-radius: 8px;
      margin-bottom: 8px;
    }
    .timeline-icon {
      font-size: 16px;
      width: 28px;
      height: 28px;
      border-radius: 6px;
      background: var(--bg);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .timeline-content {
      flex: 1;
      min-width: 0;
    }
    .timeline-title {
      font-weight: 500;
      font-size: 13.5px;
    }
    .timeline-sub {
      font-size: 12px;
      color: var(--text-muted);
    }
    /* Buttons */
    .btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      border-radius: 8px;
      font-size: 13.5px;
      font-weight: 500;
      border: 1px solid transparent;
      cursor: pointer;
      text-decoration: none;
      transition: all 0.15s;
    }
    .btn-primary {
      background: var(--primary);
      color: #ffffff;
    }
    .btn-primary:hover {
      background: var(--primary-hover);
    }
    .btn-outline {
      background: #ffffff;
      border-color: var(--card-border);
      color: var(--text-main);
    }
    .btn-outline:hover {
      background: #f1f5f9;
    }
    /* Table */
    .table-wrapper {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13.5px;
    }
    th, td {
      padding: 10px 12px;
      text-align: left;
      border-bottom: 1px solid var(--card-border);
    }
    th {
      font-weight: 600;
      color: var(--text-muted);
      background: #f8fafc;
    }
    /* Input */
    .input {
      padding: 8px 12px;
      border: 1px solid var(--card-border);
      border-radius: 8px;
      font-size: 13.5px;
      outline: none;
    }
    .input:focus {
      border-color: var(--primary);
    }
  </style>
</head>
<body>
  <div class="container">
    <!-- Header -->
    <header class="header">
      <div class="brand">
        <div class="brand-icon">⚡</div>
        <div>
          <div class="brand-title">Personal Data Hub</div>
          <div class="brand-subtitle">Cloudflare Workers + D1 Edge Altyapısı</div>
        </div>
      </div>
      <div class="badges">
        <div class="badge"><span class="badge-dot"></span> Cloudflare D1 Aktif</div>
        <div class="badge" id="b-books">📚 0 Kitap</div>
        <div class="badge" id="b-films">🎬 0 Film</div>
        <div class="badge" id="b-foods">🥗 0 Besin</div>
      </div>
    </header>

    <!-- Tabs Navigation -->
    <nav class="tabs">
      <button class="tab-btn active" onclick="switchTab('tab-overview')">📊 Genel Bakış</button>
      <button class="tab-btn" onclick="switchTab('tab-nutrition')">🥗 Beslenme</button>
      <button class="tab-btn" onclick="switchTab('tab-books')">📚 Kitaplar</button>
      <button class="tab-btn" onclick="switchTab('tab-movies')">🎬 Filmler</button>
      <button class="tab-btn" onclick="switchTab('tab-settings')">⚙️ Ayarlar & Yedekleme</button>
    </nav>

    <!-- Tab 1: Overview -->
    <div id="tab-overview" class="tab-content active">
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-label">Bugünkü Kalori</div>
          <div class="metric-value" id="m-today-cal">0 kcal</div>
          <div class="metric-sub" id="m-target-cal">Hedef: 2200 kcal</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Toplam Kitap</div>
          <div class="metric-value" id="m-books-count">0</div>
          <div class="metric-sub">Goodreads RSS</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">İzlenen Filmler</div>
          <div class="metric-value" id="m-films-count">0</div>
          <div class="metric-sub">Letterboxd RSS</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Besin Kayıtları</div>
          <div class="metric-value" id="m-foods-count">0</div>
          <div class="metric-sub">FatSecret Geçmişi</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Müzik Dinleme</div>
          <div class="metric-value" id="m-music-count">0</div>
          <div class="metric-sub">Web Scrobbler & Last.fm</div>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <div class="card-title">Son Etkinlikler (Activity Timeline)</div>
          <div class="badge">Son 7 Gün</div>
        </div>
        <div id="timeline-list">Yükleniyor...</div>
      </div>
    </div>

    <!-- Tab 2: Nutrition -->
    <div id="tab-nutrition" class="tab-content">
      <div class="card">
        <div class="card-header">
          <div class="card-title">Günlük Besin Özeti</div>
        </div>
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Tarih</th>
                <th>Öğün Sayısı</th>
                <th>Toplam Kalori</th>
              </tr>
            </thead>
            <tbody id="nutrition-summary-body">
              <tr><td colspan="3">Yükleniyor...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Tab 3: Books -->
    <div id="tab-books" class="tab-content">
      <div class="card">
        <div class="card-header">
          <div class="card-title">Goodreads Kitapları</div>
          <input type="text" id="book-search" class="input" placeholder="Kitap veya yazar ara..." oninput="filterBooks()">
        </div>
        <div class="grid-cards" id="books-grid">Yükleniyor...</div>
      </div>
    </div>

    <!-- Tab 4: Movies -->
    <div id="tab-movies" class="tab-content">
      <div class="card">
        <div class="card-header">
          <div class="card-title">Letterboxd Film Günlüğü</div>
        </div>
        <div class="grid-cards" id="movies-grid">Yükleniyor...</div>
      </div>
    </div>

    <!-- Tab 5: Settings & Backup -->
    <div id="tab-settings" class="tab-content">
      <div class="card">
        <div class="card-header">
          <div class="card-title">Hedefler & Ayarlar</div>
        </div>
        <div style="display: flex; gap: 12px; align-items: center; margin-bottom: 16px;">
          <label style="font-weight: 500;">Günlük Kalori Hedefi (kcal):</label>
          <input type="number" id="calorie-target-input" class="input" style="width: 120px;" value="2200">
          <button class="btn btn-primary" onclick="saveCalorieTarget()">Kaydet</button>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <div class="card-title">Senkronizasyon İşlemleri</div>
        </div>
        <div style="display: flex; gap: 12px; flex-wrap: wrap;">
          <button class="btn btn-outline" onclick="triggerSync('fatsecret')">🥗 FatSecret Sync Başlat</button>
          <button class="btn btn-outline" onclick="triggerSync('goodreads')">🔄 Goodreads Sync Başlat</button>
          <button class="btn btn-outline" onclick="triggerSync('letterboxd')">🔄 Letterboxd Sync Başlat</button>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <div class="card-title">Veri Egemenliği & D1 Yedekleme</div>
        </div>
        <p style="color: var(--text-muted); margin-bottom: 16px;">
          Cloudflare D1 üzerindeki tüm kişisel verilerinizi istediğiniz zaman bilgisayarınıza indirebilirsiniz.
        </p>
        <div style="display: flex; gap: 12px; flex-wrap: wrap;">
          <a href="/api/export/all" class="btn btn-primary" download>📥 JSON Yedek İndir</a>
          <a href="/api/export/sql" class="btn btn-outline" download>📥 SQLite (.sql) Dump İndir</a>
        </div>
      </div>
    </div>
  </div>

  <script>
    let allBooks = [];

    function switchTab(tabId) {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
      event.target.classList.add('active');
      document.getElementById(tabId).classList.add('active');
    }

    async function loadDashboard() {
      try {
        const res = await fetch('/api/dashboard');
        const data = await res.json();
        
        document.getElementById('m-today-cal').innerText = (data.today.fatsecret_calories || 0) + ' kcal';
        document.getElementById('m-target-cal').innerText = 'Hedef: ' + (data.calorie_target || 2200) + ' kcal';
        document.getElementById('calorie-target-input').value = data.calorie_target || 2200;

        document.getElementById('m-books-count').innerText = data.counts.goodreads_books;
        document.getElementById('m-films-count').innerText = data.counts.letterboxd_films;
        document.getElementById('m-foods-count').innerText = data.counts.fatsecret_food_entries;
        if (document.getElementById('m-music-count')) {
          document.getElementById('m-music-count').innerText = data.counts.music_scrobbles || 0;
        }

        document.getElementById('b-books').innerText = '📚 ' + data.counts.goodreads_books + ' Kitap';
        document.getElementById('b-films').innerText = '🎬 ' + data.counts.letterboxd_films + ' Film';
        document.getElementById('b-foods').innerText = '🥗 ' + data.counts.fatsecret_food_entries + ' Besin';
      } catch (e) {
        console.error(e);
      }
    }

    async function loadTimeline() {
      try {
        const res = await fetch('/api/activity?days=7&limit=40');
        const data = await res.json();
        const el = document.getElementById('timeline-list');
        if (!data.groups || data.groups.length === 0) {
          el.innerHTML = '<div style="color: var(--text-muted);">Henüz etkinlik kaydı bulunmuyor.</div>';
          return;
        }

        let html = '';
        for (const g of data.groups) {
          html += '<div class="timeline-group">';
          html += '<div class="timeline-header"><span>' + g.label + ' (' + g.date + ')</span><span>' + (g.summary.total_calories > 0 ? g.summary.total_calories + ' kcal' : '') + '</span></div>';
          for (const it of g.items) {
            let icon = '📌';
            if (it.type === 'meal') icon = '🥗';
            else if (it.type === 'book') icon = '📚';
            else if (it.type === 'film') icon = '🎬';
            else if (it.type === 'music') icon = '🎵';

            html += '<div class="timeline-item">';
            html += '<div class="timeline-icon">' + icon + '</div>';
            html += '<div class="timeline-content">';
            html += '<div class="timeline-title">' + escapeHtml(it.title) + '</div>';
            html += '<div class="timeline-sub">' + escapeHtml(it.subtitle || '') + (it.details?.calories ? ' • ' + it.details.calories + ' kcal' : '') + '</div>';
            html += '</div>';
            html += '</div>';
          }
          html += '</div>';
        }
        el.innerHTML = html;
      } catch (e) {
        console.error(e);
      }
    }

    async function loadNutrition() {
      try {
        const res = await fetch('/api/fatsecret/daily-summary?limit=30');
        const data = await res.json();
        const tbody = document.getElementById('nutrition-summary-body');
        if (!data.items || data.items.length === 0) {
          tbody.innerHTML = '<tr><td colspan="3">Kayıt yok</td></tr>';
          return;
        }
        tbody.innerHTML = data.items.map(i => '<tr><td><b>' + i.date + '</b></td><td>' + i.entry_count + ' öğün</td><td><span style="font-weight: 600; color: var(--primary);">' + i.calories + ' kcal</span></td></tr>').join('');
      } catch (e) {
        console.error(e);
      }
    }

    async function loadBooks() {
      try {
        const res = await fetch('/api/goodreads/books?limit=100');
        const data = await res.json();
        allBooks = data.items || [];
        renderBooks(allBooks);
      } catch (e) {
        console.error(e);
      }
    }

    function renderBooks(books) {
      const grid = document.getElementById('books-grid');
      if (books.length === 0) {
        grid.innerHTML = '<div style="color: var(--text-muted);">Kitap bulunamadı.</div>';
        return;
      }
      grid.innerHTML = books.map(b => {
        const img = b.image_url ? '<img class="item-img" src="' + escapeHtml(b.image_url) + '" loading="lazy">' : '<div class="item-img" style="display:flex;align-items:center;justify-content:center;">📖</div>';
        const rating = b.my_rating ? '⭐ ' + b.my_rating + '/5' : (b.average_rating ? '★ ' + b.average_rating : '');
        return '<div class="item-card">' + img + '<div class="item-body">' +
          '<div class="item-title" title="' + escapeHtml(b.title) + '">' + escapeHtml(b.title) + '</div>' +
          '<div class="item-subtitle">' + escapeHtml(b.author || '') + '</div>' +
          '<div class="item-meta"><span>' + rating + '</span><span>•</span><span>' + escapeHtml(b.exclusive_shelf || 'okundu') + '</span></div>' +
          '</div></div>';
      }).join('');
    }

    function filterBooks() {
      const q = document.getElementById('book-search').value.toLowerCase();
      const filtered = allBooks.filter(b => (b.title || '').toLowerCase().includes(q) || (b.author || '').toLowerCase().includes(q));
      renderBooks(filtered);
    }

    async function loadMovies() {
      try {
        const res = await fetch('/api/letterboxd/films?limit=100');
        const data = await res.json();
        const grid = document.getElementById('movies-grid');
        if (!data.items || data.items.length === 0) {
          grid.innerHTML = '<div style="color: var(--text-muted);">Film bulunamadı.</div>';
          return;
        }
        grid.innerHTML = data.items.map(m => {
          const img = m.image_url ? '<img class="item-img" src="' + escapeHtml(m.image_url) + '" loading="lazy">' : '<div class="item-img" style="display:flex;align-items:center;justify-content:center;">🎬</div>';
          const rating = m.rating ? '⭐ ' + m.rating + '/5' : '';
          return '<div class="item-card">' + img + '<div class="item-body">' +
            '<div class="item-title" title="' + escapeHtml(m.name) + '">' + escapeHtml(m.name) + '</div>' +
            '<div class="item-subtitle">' + (m.year || '') + (m.rewatch === 'Yes' ? ' • 🔁 Tekrar İzleme' : '') + '</div>' +
            '<div class="item-meta"><span>' + rating + '</span><span>•</span><span>' + (m.watched_date || '') + '</span></div>' +
            '</div></div>';
        }).join('');
      } catch (e) {
        console.error(e);
      }
    }

    async function saveCalorieTarget() {
      const val = document.getElementById('calorie-target-input').value;
      try {
        const res = await fetch('/settings/calorie-target?target=' + val, { method: 'POST' });
        if (res.ok) {
          alert('Hedef başarıyla güncellendi!');
          loadDashboard();
        }
      } catch (e) {
        alert('Hata: ' + e.message);
      }
    }

    async function triggerSync(source) {
      if (!confirm(source + ' senkronizasyonu başlatılsın mı?')) return;
      try {
        const res = await fetch('/api/sync/' + source, { method: 'POST' });
        const data = await res.json();
        alert('Sonuç: ' + (data.message || (data.inserted + ' eklendi, ' + data.updated + ' güncellendi')));
        loadDashboard();
        if (source === 'fatsecret') { loadNutrition(); loadTimeline(); }
        if (source === 'goodreads') loadBooks();
        if (source === 'letterboxd') loadMovies();
      } catch (e) {
        alert('Hata: ' + e.message);
      }
    }

    function escapeHtml(str) {
      if (!str) return '';
      return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // Initialize
    loadDashboard();
    loadTimeline();
    loadNutrition();
    loadBooks();
    loadMovies();
  </script>
</body>
</html>`;
}
