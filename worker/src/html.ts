export function renderDashboardHtml(): string {
  return `<!doctype html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Personal Records</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
  <script>
    (function() {
      try {
        var saved = localStorage.getItem('theme');
        var prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
        if (saved === 'dark' || (!saved && prefersDark)) {
          document.documentElement.classList.add('dark');
        }
      } catch (e) {}
    })();
  </script>
  <style>
    /* shadcn/ui Zinc Design System */
    :root {
      --background: 0 0% 100%;
      --foreground: 240 10% 3.9%;
      --card: 0 0% 100%;
      --card-foreground: 240 10% 3.9%;
      --popover: 0 0% 100%;
      --popover-foreground: 240 10% 3.9%;
      --primary: 240 5.9% 10%;
      --primary-foreground: 0 0% 98%;
      --secondary: 240 4.8% 95.9%;
      --secondary-foreground: 240 5.9% 10%;
      --muted: 240 4.8% 95.9%;
      --muted-foreground: 240 3.8% 46.1%;
      --accent: 240 4.8% 95.9%;
      --accent-foreground: 240 5.9% 10%;
      --destructive: 0 84.2% 60.2%;
      --destructive-foreground: 0 0% 98%;
      --border: 240 5.9% 90%;
      --input: 240 5.9% 90%;
      --ring: 240 5.9% 10%;
      --radius: 8px;

      --bg: #ffffff;
      --card-bg: #ffffff;
      --card-border: #e4e4e7;
      --text-main: #09090b;
      --text-muted: #71717a;
      --primary-hover: #27272a;
      --primary-light: #f4f4f5;
      --success: #16a34a;
      --success-light: #f0fdf4;
      --warning: #d97706;
      --warning-light: #fffbeb;
    }

    .dark {
      --background: 240 10% 3.9%;
      --foreground: 0 0% 98%;
      --card: 240 10% 4.8%;
      --card-foreground: 0 0% 98%;
      --popover: 240 10% 4.8%;
      --popover-foreground: 0 0% 98%;
      --primary: 0 0% 98%;
      --primary-foreground: 240 5.9% 10%;
      --secondary: 240 3.7% 15.9%;
      --secondary-foreground: 0 0% 98%;
      --muted: 240 3.7% 15.9%;
      --muted-foreground: 240 5% 64.9%;
      --accent: 240 3.7% 15.9%;
      --accent-foreground: 0 0% 98%;
      --destructive: 0 62.8% 30.6%;
      --destructive-foreground: 0 0% 98%;
      --border: 240 3.7% 15.9%;
      --input: 240 3.7% 15.9%;
      --ring: 240 4.9% 83.9%;

      --bg: #09090b;
      --card-bg: #0c0c0e;
      --card-border: #27272a;
      --text-main: #fafafa;
      --text-muted: #a1a1aa;
      --primary-hover: #e4e4e7;
      --primary-light: #27272a;
      --success: #4ade80;
      --success-light: #052e16;
      --warning: #fbbf24;
      --warning-light: #451a03;
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background-color: hsl(var(--background));
      color: hsl(var(--foreground));
      font-family: 'Inter', ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      font-size: 14px;
      line-height: 1.5;
      -webkit-font-smoothing: antialiased;
      -moz-osx-font-smoothing: grayscale;
      transition: background-color 0.2s ease, color 0.2s ease;
    }
    .container {
      max-width: 1280px;
      margin: 0 auto;
      padding: 28px 24px 80px;
    }
    /* Header */
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      padding-bottom: 20px;
      border-bottom: 1px solid hsl(var(--border));
      flex-wrap: wrap;
      gap: 16px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .brand-icon {
      width: 36px;
      height: 36px;
      border-radius: var(--radius);
      background: hsl(var(--primary));
      color: hsl(var(--primary-foreground));
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      font-weight: 700;
      letter-spacing: -0.04em;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    }
    .brand-title {
      font-size: 18px;
      font-weight: 700;
      color: hsl(var(--foreground));
      letter-spacing: -0.025em;
    }
    .brand-subtitle {
      font-size: 12.5px;
      color: hsl(var(--muted-foreground));
    }
    .badges {
      display: flex;
      gap: 6px;
      align-items: center;
      flex-wrap: wrap;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 3px 9px;
      border-radius: 9999px;
      font-size: 11.5px;
      font-weight: 500;
      border: 1px solid hsl(var(--border));
      background: hsl(var(--card));
      color: hsl(var(--foreground));
      transition: all 0.15s ease;
    }
    .badge-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--success);
    }

    /* Dark Mode Icons */
    .dark .sun-icon { display: inline-flex; align-items: center; gap: 4px; }
    .dark .moon-icon { display: none; }
    :not(.dark) .sun-icon { display: none; }
    :not(.dark) .moon-icon { display: inline-flex; align-items: center; gap: 4px; }

    /* Tabs (shadcn/ui Tabs Style) */
    .tabs {
      display: inline-flex;
      background: hsl(var(--muted));
      padding: 3px;
      border-radius: var(--radius);
      border: 1px solid hsl(var(--border));
      gap: 2px;
      margin-bottom: 24px;
      overflow-x: auto;
      max-width: 100%;
    }
    .tab-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border: 0;
      background: transparent;
      border-radius: calc(var(--radius) - 2px);
      font-size: 13px;
      font-weight: 500;
      color: hsl(var(--muted-foreground));
      cursor: pointer;
      white-space: nowrap;
      transition: all 0.15s ease;
    }
    .tab-btn:hover {
      color: hsl(var(--foreground));
    }
    .tab-btn.active {
      background: hsl(var(--card));
      color: hsl(var(--foreground));
      font-weight: 600;
      box-shadow: 0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.04);
    }
    .tab-content {
      display: none;
    }
    .tab-content.active {
      display: block;
      animation: fadeIn 0.15s ease-in-out;
    }
    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(2px); }
      to { opacity: 1; transform: translateY(0); }
    }
    /* Metrics */
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 14px;
      margin-bottom: 24px;
    }
    .metric-card {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: var(--radius);
      padding: 18px 20px;
      box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03);
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }
    .metric-card:hover {
      border-color: hsl(var(--ring) / 0.25);
      box-shadow: 0 3px 6px -1px rgba(0, 0, 0, 0.05);
    }
    .metric-label {
      font-size: 12px;
      color: hsl(var(--muted-foreground));
      margin-bottom: 6px;
      font-weight: 500;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .metric-value {
      font-size: 26px;
      font-weight: 700;
      letter-spacing: -0.025em;
      color: hsl(var(--foreground));
      line-height: 1.15;
    }
    .metric-sub {
      font-size: 12px;
      color: hsl(var(--muted-foreground));
      margin-top: 5px;
    }
    /* Cards */
    .card {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: calc(var(--radius) + 2px);
      padding: 20px 22px;
      margin-bottom: 20px;
      box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.03);
    }
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 18px;
    }
    .card-title {
      font-size: 16px;
      font-weight: 600;
      color: hsl(var(--foreground));
      letter-spacing: -0.015em;
    }
    /* Item List & Cards */
    .grid-cards {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 12px;
    }
    .item-card {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: var(--radius);
      padding: 12px;
      display: flex;
      gap: 12px;
      transition: border-color 0.15s ease, background-color 0.15s ease;
    }
    .item-card:hover {
      background: hsl(var(--muted) / 0.4);
      border-color: hsl(var(--ring) / 0.3);
    }
    .item-img {
      width: 60px;
      height: 84px;
      border-radius: 6px;
      object-fit: cover;
      background: hsl(var(--muted));
      flex-shrink: 0;
    }
    .item-body {
      flex: 1;
      min-width: 0;
    }
    .item-title {
      font-size: 14px;
      font-weight: 600;
      color: hsl(var(--foreground));
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .item-subtitle {
      font-size: 12.5px;
      color: hsl(var(--muted-foreground));
      margin-top: 2px;
    }
    .item-meta {
      font-size: 12px;
      color: hsl(var(--muted-foreground));
      margin-top: 6px;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    /* Timeline */
    .timeline-group {
      margin-bottom: 16px;
    }
    .timeline-header {
      font-size: 13px;
      font-weight: 600;
      color: hsl(var(--muted-foreground));
      margin-bottom: 10px;
      display: flex;
      justify-content: space-between;
      border-bottom: 1px solid hsl(var(--border));
      padding-bottom: 8px;
    }
    .timeline-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 14px;
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: var(--radius);
      margin-bottom: 6px;
      transition: border-color 0.15s ease;
    }
    .timeline-item:hover {
      border-color: hsl(var(--ring) / 0.3);
    }
    .timeline-icon {
      font-size: 15px;
      width: 32px;
      height: 32px;
      border-radius: calc(var(--radius) - 2px);
      background: hsl(var(--muted));
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
      color: hsl(var(--foreground));
    }
    .timeline-sub {
      font-size: 12px;
      color: hsl(var(--muted-foreground));
    }
    /* Buttons */
    .btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      min-height: 36px;
      padding: 7px 14px;
      border-radius: calc(var(--radius) - 2px);
      font-size: 13px;
      font-weight: 500;
      border: 1px solid transparent;
      cursor: pointer;
      text-decoration: none;
      transition: all 0.15s ease;
    }
    .btn-primary {
      background: hsl(var(--primary));
      color: hsl(var(--primary-foreground));
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    }
    .btn-primary:hover {
      background: hsl(var(--primary) / 0.9);
    }
    .btn-outline {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      color: hsl(var(--foreground));
    }
    .btn-outline:hover {
      background: hsl(var(--muted));
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
      padding: 10px 14px;
      text-align: left;
      border-bottom: 1px solid hsl(var(--border) / 0.7);
    }
    th {
      font-weight: 600;
      font-size: 11.5px;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: hsl(var(--muted-foreground));
      background: hsl(var(--muted) / 0.5);
    }
    td {
      color: hsl(var(--foreground));
    }
    tr:hover td {
      background: hsl(var(--muted) / 0.35);
    }
    /* Input */
    .input {
      min-height: 36px;
      padding: 7px 12px;
      border: 1px solid hsl(var(--input));
      border-radius: calc(var(--radius) - 2px);
      font-size: 13px;
      background: hsl(var(--background));
      color: hsl(var(--foreground));
      outline: none;
      transition: all 0.15s ease;
    }
    .input:focus {
      border-color: hsl(var(--ring));
      box-shadow: 0 0 0 1px hsl(var(--ring));
    }
    /* Notifications */
    .notif-item {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: var(--radius);
      padding: 14px 16px;
      margin-bottom: 8px;
      transition: border-color 0.15s ease;
    }
    .notif-item:hover {
      border-color: hsl(var(--ring) / 0.3);
    }
    .notif-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 6px;
    }
    .notif-app-tag {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 2.5px 8px;
      border-radius: 9999px;
      background: hsl(var(--secondary));
      color: hsl(var(--secondary-foreground));
      border: 1px solid hsl(var(--border));
      font-size: 11px;
      font-weight: 600;
    }
    .notif-ongoing-tag {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 7px;
      border-radius: 9999px;
      background: var(--badge-amber-bg);
      color: var(--badge-amber-text);
      border: 1px solid var(--badge-amber-border);
      font-size: 11px;
      font-weight: 500;
    }
    .notif-pkg {
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 11px;
      color: hsl(var(--muted-foreground));
    }
    .notif-time {
      font-size: 12px;
      color: hsl(var(--muted-foreground));
    }
    .notif-title {
      font-size: 14px;
      font-weight: 600;
      color: hsl(var(--foreground));
      margin-top: 4px;
    }
    .notif-body {
      font-size: 13px;
      color: hsl(var(--muted-foreground));
      margin-top: 4px;
      white-space: pre-line;
      line-height: 1.45;
    }
    .notif-sub {
      font-size: 12px;
      color: hsl(var(--muted-foreground));
      margin-top: 4px;
      font-style: italic;
    }
    code {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 12px;
      background: hsl(var(--muted));
      color: hsl(var(--foreground));
      border: 1px solid hsl(var(--border));
      padding: 2px 6px;
      border-radius: 4px;
    }
    .webhook-box {
      background: hsl(var(--muted)) !important;
      border: 1px solid hsl(var(--border)) !important;
      padding: 12px 16px;
      border-radius: calc(var(--radius) - 2px);
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 13px;
      color: hsl(var(--foreground)) !important;
      display: flex;
      justify-content: space-between;
      align-items: center;
      word-break: break-all;
    }
    .webhook-box span:first-child,
    .webhook-box code {
      color: hsl(var(--foreground)) !important;
      font-weight: 600;
      background: transparent !important;
      border: none !important;
      padding: 0 !important;
    }
    /* Health Connect */
    .health-record {
      display: flex;
      gap: 12px;
      padding: 14px 0;
      border-bottom: 1px solid hsl(var(--border));
    }
    .health-record:last-child { border-bottom: 0; }
    .health-record-icon {
      width: 40px;
      height: 40px;
      border-radius: var(--radius);
      background: hsl(var(--secondary));
      color: hsl(var(--foreground));
      border: 1px solid hsl(var(--border));
      display: flex;
      align-items: center;
      justify-content: center;
      flex: 0 0 auto;
      font-size: 18px;
    }
    .health-record-body { min-width: 0; flex: 1; }
    .health-record-title { font-size: 14px; font-weight: 600; color: hsl(var(--foreground)); }
    .health-record-sub { color: hsl(var(--muted-foreground)); font-size: 12.5px; margin-top: 2px; }
    .health-record-meta { color: hsl(var(--muted-foreground)); font-size: 11.5px; margin-top: 5px; }
    .health-origin-tag {
      align-self: flex-start;
      border-radius: 9999px;
      background: hsl(var(--secondary));
      color: hsl(var(--secondary-foreground));
      border: 1px solid hsl(var(--border));
      font-size: 11px;
      font-weight: 600;
      padding: 3px 8px;
      max-width: 180px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    @media (max-width: 720px) {
      .container { padding: 20px 16px 56px; }
      .header { align-items: flex-start; margin-bottom: 20px; }
      .badges { display: none; }
      .tabs { width: 100%; margin-bottom: 20px; }
      .metrics-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
      .metric-card { padding: 14px; }
      .metric-value { font-size: 22px; }
      .card { padding: 16px; }
      .card-header { align-items: flex-start; }
      .grid-cards { grid-template-columns: 1fr; }
      .health-origin-tag { max-width: 110px; }
    }

    /* Security Layer & Auth Modal */
    .auth-overlay {
      position: fixed;
      inset: 0;
      z-index: 9999;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.75);
      backdrop-filter: blur(8px);
      padding: 16px;
      transition: opacity 0.2s ease;
    }
    .auth-overlay.hidden {
      display: none !important;
    }
    .auth-card {
      background: hsl(var(--card));
      color: hsl(var(--card-foreground));
      border: 1px solid hsl(var(--border));
      border-radius: 12px;
      padding: 32px 28px;
      width: 100%;
      max-width: 400px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 8px 10px -6px rgba(0, 0, 0, 0.2);
    }
    .auth-icon-wrap {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: hsl(var(--secondary));
      color: hsl(var(--foreground));
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 16px;
      border: 1px solid hsl(var(--border));
    }
    .auth-title {
      font-size: 18px;
      font-weight: 700;
      color: hsl(var(--foreground));
      margin-bottom: 4px;
    }
    .auth-desc {
      font-size: 13px;
      color: hsl(var(--muted-foreground));
      margin-bottom: 20px;
      line-height: 1.45;
    }
    .auth-field {
      margin-bottom: 16px;
    }
    .auth-label {
      display: block;
      font-size: 12px;
      font-weight: 600;
      color: hsl(var(--muted-foreground));
      margin-bottom: 6px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .auth-input-wrap {
      position: relative;
      display: flex;
      align-items: center;
    }
    .auth-input-wrap input {
      width: 100%;
      height: 40px;
      padding: 0 40px 0 12px;
      border-radius: 6px;
      border: 1px solid hsl(var(--input));
      background: transparent;
      color: hsl(var(--foreground));
      font-size: 14px;
      outline: none;
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }
    .auth-input-wrap input:focus {
      border-color: hsl(var(--ring));
      box-shadow: 0 0 0 2px hsl(var(--ring) / 0.2);
    }
    .auth-toggle-btn {
      position: absolute;
      right: 8px;
      background: transparent;
      border: none;
      color: hsl(var(--muted-foreground));
      cursor: pointer;
      font-size: 13px;
      padding: 4px;
      border-radius: 4px;
    }
    .auth-error {
      font-size: 12.5px;
      color: hsl(var(--destructive));
      background: hsl(var(--destructive) / 0.1);
      border: 1px solid hsl(var(--destructive) / 0.25);
      padding: 8px 12px;
      border-radius: 6px;
      margin-bottom: 14px;
    }
    .auth-error.hidden {
      display: none !important;
    }
    .security-banner {
      background: #fffbeb;
      border: 1px solid #fde68a;
      color: #92400e;
      border-radius: 8px;
      padding: 10px 14px;
      margin-bottom: 20px;
      font-size: 13px;
      display: flex;
      align-items: center;
      gap: 10px;
      line-height: 1.4;
    }
    .dark .security-banner {
      background: rgba(245, 158, 11, 0.12);
      border-color: rgba(245, 158, 11, 0.25);
      color: #fbbf24;
    }
    .security-banner.hidden {
      display: none !important;
    }
  </style>
</head>
<body>
  <div class="container">
    <!-- Header -->
    <header class="header">
      <div class="brand">
        <div class="brand-icon">PR</div>
        <div>
          <div class="brand-title">Personal Records</div>
          <div class="brand-subtitle">Kişisel kayıtlarınız, tek yerde</div>
        </div>
      </div>
      <div class="badges">
        <div class="badge"><span class="badge-dot"></span> Bağlı</div>
        <div class="badge" id="b-books">0 Kitap</div>
        <div class="badge" id="b-films">0 Film</div>
        <div class="badge" id="b-foods">0 Besin</div>
        <div class="badge" id="b-notifs">0 Bildirim</div>
        <div class="badge" id="b-health">0 Sağlık kaydı</div>
        <button type="button" class="btn btn-outline theme-toggle-btn" onclick="toggleTheme()" title="Temayı Değiştir" aria-label="Temayı Değiştir" style="min-height:30px; padding:3px 10px; font-size:12px;">
          <span class="sun-icon">☀️ Aydınlık</span>
          <span class="moon-icon">🌙 Karanlık</span>
        </button>
        <button type="button" id="auth-logout-btn" class="btn btn-outline" onclick="handleLogout()" title="Oturumu Kapat" style="min-height:30px; padding:3px 10px; font-size:12px; display:none;">
          🔒 Çıkış
        </button>
      </div>
    </header>

    <!-- Security Warning Banner (shown only when API_KEY is not configured) -->
    <div id="security-banner" class="security-banner hidden">
      <span style="font-size:16px;">⚠️</span>
      <div>
        <b>Güvenlik Uyarısı:</b> API_KEY ortam değişkeni henüz tanımlanmadı. Sunucunuz şu an herkese açıktır.
        <span style="opacity:0.85; margin-left: 4px;">Cloudflare Secrets üzerinden <code>API_KEY</code> tanımlayarak korumayı etkinleştirebilirsiniz.</span>
      </div>
    </div>

    <!-- Tabs Navigation -->
    <nav class="tabs">
      <button class="tab-btn active" onclick="switchTab('tab-overview', this)">Genel bakış</button>
      <button class="tab-btn" onclick="switchTab('tab-nutrition', this)">Beslenme</button>
      <button class="tab-btn" onclick="switchTab('tab-books', this)">Kitaplar</button>
      <button class="tab-btn" onclick="switchTab('tab-movies', this)">Filmler</button>
      <button class="tab-btn" onclick="switchTab('tab-notifications', this)">Bildirimler</button>
      <button class="tab-btn" onclick="switchTab('tab-health', this)">Sağlık</button>
      <button class="tab-btn" onclick="switchTab('tab-settings', this)">Ayarlar</button>
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
        <div class="metric-card">
          <div class="metric-label">Gelen Bildirimler</div>
          <div class="metric-value" id="m-notifs-count">0</div>
          <div class="metric-sub">Android Bildirim İletimi</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Bugünkü Adımlar</div>
          <div class="metric-value" id="m-health-steps">0</div>
          <div class="metric-sub" id="m-health-sub">Health Connect</div>
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

    <!-- Tab 5: Notifications -->
    <div id="tab-notifications" class="tab-content">
      <!-- Webhook Integration Card -->
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">🔔 Android Bildirim İletimi (Notification Relay)</div>
            <div style="font-size:12.5px; color: var(--text-muted); margin-top: 2px;">
              Android telefonunuzdan gelen anlık sistem ve uygulama bildirimlerini Cloudflare D1 veritabanına aktarır.
            </div>
          </div>
          <button class="btn btn-outline" onclick="copyWebhookUrl()">URL'yi kopyala</button>
        </div>
        <div class="webhook-box">
          <span id="webhook-url-text">/api/webhooks/notification</span>
          <span id="webhook-copy-feedback" style="font-size: 12px; color: var(--success); font-weight: 600; margin-left: 12px; display: none;">Kopyalandı!</span>
        </div>
        <div style="margin-top: 10px; font-size: 12px; color: var(--text-muted); line-height: 1.5;">
          <b>Nasıl Kullanılır:</b> <code>Notification Relay</code> veya <code>HTTP Shortcuts</code> uygulamasında hedef uç nokta olarak yukarıdaki adresi belirtip <code>POST</code> metoduyla JSON iletin.
          Desteklenen parametreler: <code>packageName</code>, <code>appName</code>, <code>title</code>, <code>text</code>, <code>subText</code>, <code>bigText</code>, <code>postTime</code>, <code>ongoing</code>.
        </div>
      </div>

      <!-- Notification Metrics -->
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-label">Toplam Bildirim</div>
          <div class="metric-value" id="m-notif-tab-total">0</div>
          <div class="metric-sub">D1'de kayıtlı bildirimler</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Farklı Uygulamalar</div>
          <div class="metric-value" id="m-notif-tab-apps">0</div>
          <div class="metric-sub">Kaynak uygulama sayısı</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Devam Eden / Aktif</div>
          <div class="metric-value" id="m-notif-tab-ongoing">0</div>
          <div class="metric-sub">Sistem arka plan bildirimleri</div>
        </div>
      </div>

      <!-- Feed Card with Search & Filters -->
      <div class="card">
        <div class="card-header" style="flex-wrap: wrap; gap: 12px;">
          <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap; flex: 1;">
            <input type="text" id="notif-search" class="input" style="min-width: 240px; flex: 1;" placeholder="Başlık, içerik veya paket adı ara..." oninput="filterNotifications()">
            <select id="notif-app-filter" class="input" style="min-width: 180px;" onchange="filterNotifications()">
              <option value="all">Tüm Uygulamalar</option>
            </select>
            <label style="display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 500; cursor: pointer; white-space: nowrap;">
              <input type="checkbox" id="notif-ongoing-filter" onchange="filterNotifications()"> Sadece Devam Eden
            </label>
          </div>
          <div style="display: flex; gap: 8px;">
            <button class="btn btn-outline" onclick="loadNotifications()">Yenile</button>
          </div>
        </div>
        <div id="notifications-list">Yükleniyor...</div>
      </div>
    </div>

    <!-- Tab 6: Health Connect -->
    <div id="tab-health" class="tab-content">
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">♥ Health Connect</div>
            <div style="font-size:12.5px; color: var(--text-muted); margin-top: 2px;">Telefonunuzdaki sağlık kayıtları, bağlı kaynaklar ve günlük özet.</div>
          </div>
          <button class="btn btn-outline" onclick="copyHealthWebhookUrl()">URL'yi kopyala</button>
        </div>
        <div class="webhook-box">
          <span id="health-webhook-url-text">/api/webhooks/health-connect</span>
          <span id="health-webhook-copy-feedback" style="font-size: 12px; color: var(--success); font-weight: 600; margin-left: 12px; display: none;">Kopyalandı!</span>
        </div>
      </div>

      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-label">Bugünkü Adımlar</div>
          <div class="metric-value" id="m-health-tab-steps">0</div>
          <div class="metric-sub" id="m-health-tab-steps-date">Kayıt bekleniyor</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Aktif Kalori</div>
          <div class="metric-value" id="m-health-tab-calories">0 kcal</div>
          <div class="metric-sub">Son kayıtlı gün</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Son Nabız</div>
          <div class="metric-value" id="m-health-tab-heart-rate">—</div>
          <div class="metric-sub">bpm</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Toplam Sağlık Kaydı</div>
          <div class="metric-value" id="m-health-tab-total">0</div>
          <div class="metric-sub">Health Connect</div>
        </div>
      </div>

      <div class="card">
        <div class="card-header" style="flex-wrap: wrap; gap: 12px;">
          <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap; flex: 1;">
            <input type="text" id="health-search" class="input" style="min-width: 240px; flex: 1;" placeholder="Kayıt veya kaynak ara..." oninput="filterHealthRecords()">
            <select id="health-category-filter" class="input" onchange="filterHealthRecords()">
              <option value="all">Tüm Kategoriler</option>
              <option value="activity">Aktivite</option>
              <option value="exercise">Antrenman</option>
              <option value="sleep">Uyku</option>
              <option value="vitals">Yaşamsal Bulgular</option>
              <option value="body">Vücut</option>
            </select>
          </div>
          <button class="btn btn-outline" onclick="loadHealthRecords()">Yenile</button>
        </div>
        <div id="health-records-list">Yükleniyor...</div>
      </div>
    </div>

    <!-- Tab 7: Settings & Backup -->
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
          <button class="btn btn-outline" onclick="triggerSync('fatsecret')">FatSecret'i eşitle</button>
          <button class="btn btn-outline" onclick="triggerSync('goodreads')">Goodreads'i eşitle</button>
          <button class="btn btn-outline" onclick="triggerSync('letterboxd')">Letterboxd'u eşitle</button>
          <button class="btn btn-outline" onclick="triggerSync('trakt')">Trakt'ı eşitle</button>
          <button class="btn btn-outline" onclick="triggerSync('simkl')">Simkl'i eşitle</button>
          <button class="btn btn-outline" onclick="triggerSync('mdblist')">MDBList'i eşitle</button>
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
    // --- Security Layer & Authenticated Requests ---
    const originalFetch = window.fetch.bind(window);
    async function authFetch(url, options) {
      options = options || {};
      options.headers = options.headers || {};
      const token = localStorage.getItem('pr_api_key');
      if (token && !options.headers['Authorization']) {
        options.headers['Authorization'] = 'Bearer ' + token;
      }
      const res = await originalFetch(url, options);
      if (res.status === 401 && !url.includes('/api/auth/')) {
        showAuthModal("Oturum süresi doldu veya API Anahtarı geçersiz.");
      }
      return res;
    }

    function togglePasswordVisibility() {
      const inp = document.getElementById('auth-key-input');
      if (inp) {
        inp.type = inp.type === 'password' ? 'text' : 'password';
      }
    }

    function showAuthModal(errorMsg) {
      const overlay = document.getElementById('auth-overlay');
      const errBox = document.getElementById('auth-error');
      if (errBox) {
        if (errorMsg) {
          errBox.innerText = errorMsg;
          errBox.classList.remove('hidden');
        } else {
          errBox.classList.add('hidden');
        }
      }
      if (overlay) overlay.classList.remove('hidden');
      setTimeout(() => {
        document.getElementById('auth-key-input')?.focus();
      }, 50);
    }

    function hideAuthModal() {
      const overlay = document.getElementById('auth-overlay');
      if (overlay) overlay.classList.add('hidden');
    }

    async function handleLoginSubmit(e) {
      e.preventDefault();
      const input = document.getElementById('auth-key-input');
      const errBox = document.getElementById('auth-error');
      const submitBtn = document.getElementById('auth-submit-btn');
      const key = (input.value || '').trim();
      if (!key) return;

      submitBtn.disabled = true;
      submitBtn.innerText = 'Doğrulanıyor...';
      errBox.classList.add('hidden');

      try {
        const res = await originalFetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ apiKey: key })
        });
        const data = await res.json();
        if (res.ok && data.ok) {
          localStorage.setItem('pr_api_key', key);
          document.cookie = "pr_auth=" + encodeURIComponent(key) + "; path=/; max-age=31536000; SameSite=Lax";
          hideAuthModal();
          document.getElementById('auth-logout-btn')?.style.setProperty('display', 'inline-flex');
          loadAllData();
        } else {
          errBox.innerText = data.error || 'Geçersiz API Anahtarı. Lütfen tekrar deneyin.';
          errBox.classList.remove('hidden');
        }
      } catch (err) {
        errBox.innerText = 'Bağlantı hatası: ' + err.message;
        errBox.classList.remove('hidden');
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerText = 'Giriş Yap';
      }
    }

    async function handleLogout() {
      if (!confirm('Oturumu kapatmak istediğinize emin misiniz?')) return;
      try {
        await originalFetch('/api/auth/logout', { method: 'POST' });
      } catch {}
      localStorage.removeItem('pr_api_key');
      document.cookie = "pr_auth=; path=/; max-age=0";
      window.location.reload();
    }

    async function checkAuthAndLoad() {
      try {
        const token = localStorage.getItem('pr_api_key') || '';
        const headers = {};
        if (token) headers['Authorization'] = 'Bearer ' + token;
        const res = await originalFetch('/api/auth/verify', { headers });
        const data = await res.json();

        if (data.required === false) {
          // Open mode (API_KEY not configured yet)
          document.getElementById('security-banner')?.classList.remove('hidden');
          document.getElementById('auth-logout-btn')?.style.setProperty('display', 'none');
          hideAuthModal();
          loadAllData();
          return;
        }

        if (data.authenticated) {
          document.getElementById('security-banner')?.classList.add('hidden');
          document.getElementById('auth-logout-btn')?.style.setProperty('display', 'inline-flex');
          hideAuthModal();
          loadAllData();
          return;
        }

        // Auth required but unauthenticated
        showAuthModal();
      } catch (e) {
        console.error("Auth verification failed:", e);
        loadAllData();
      }
    }

    function loadAllData() {
      loadDashboard();
      loadTimeline();
      loadNutrition();
      loadBooks();
      loadMovies();
      loadNotifications();
      loadHealthRecords();
    }

    let allBooks = [];
    let allNotifications = [];
    let allHealthRecords = [];

    function switchTab(tabId, el) {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
      const btn = el || (window.event ? window.event.currentTarget || window.event.target : null);
      if (btn && btn.classList) btn.classList.add('active');
      const targetPane = document.getElementById(tabId);
      if (targetPane) targetPane.classList.add('active');
      if (tabId === 'tab-notifications' && allNotifications.length === 0) {
        loadNotifications();
      }
      if (tabId === 'tab-health' && allHealthRecords.length === 0) {
        loadHealthRecords();
      }
    }

    async function loadDashboard() {
      try {
        const res = await authFetch('/api/dashboard');
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
        if (document.getElementById('m-notifs-count')) {
          document.getElementById('m-notifs-count').innerText = data.counts.notifications || 0;
        }
        if (document.getElementById('m-health-sub')) {
          document.getElementById('m-health-sub').innerText = (data.counts.health_records || 0) + ' Health Connect kaydı';
        }

        document.getElementById('b-books').innerText = data.counts.goodreads_books + ' Kitap';
        document.getElementById('b-films').innerText = data.counts.letterboxd_films + ' Film';
        document.getElementById('b-foods').innerText = data.counts.fatsecret_food_entries + ' Besin';
        if (document.getElementById('b-notifs')) {
          document.getElementById('b-notifs').innerText = (data.counts.notifications || 0) + ' Bildirim';
        }
        if (document.getElementById('b-health')) {
          document.getElementById('b-health').innerText = (data.counts.health_records || 0) + ' Sağlık kaydı';
        }
      } catch (e) {
        console.error(e);
      }
    }

    async function loadTimeline() {
      try {
        const res = await authFetch('/api/activity?days=7&limit=40');
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
            else if (it.type === 'film' || it.type === 'movie') icon = '🎬';
            else if (it.type === 'show') icon = '📺';
            else if (it.type === 'anime') icon = '🍙';
            else if (it.type === 'music') icon = '🎵';
            else if (it.type === 'notification') icon = '🔔';
            else if (it.type === 'health') icon = '♥';

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
        const res = await authFetch('/api/fatsecret/daily-summary?limit=30');
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
        const res = await authFetch('/api/goodreads/books?limit=100');
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
        const res = await authFetch('/api/letterboxd/films?limit=100');
        const data = await res.json();
        const grid = document.getElementById('movies-grid');
        if (!data.items || data.items.length === 0) {
          grid.innerHTML = '<div style="color: var(--text-muted);">Film bulunamadı.</div>';
          return;
        }
        grid.innerHTML = data.items.map(m => {
          const img = m.image_url ? '<img class="item-img" src="' + escapeHtml(m.image_url) + '" loading="lazy">' : '<div class="item-img" style="display:flex;align-items:center;justify-content:center;font-size:32px;">🎬</div>';
          const rating = m.rating ? '⭐ ' + Number(m.rating).toFixed(1) + '/5' : '';
          const srcName = m.source === 'mdblist' ? 'MDBList' : (m.source === 'trakt' ? 'Trakt' : (m.source === 'simkl' ? 'Simkl' : 'Letterboxd'));
          const srcBadge = '<span style="background:rgba(255,255,255,0.08);color:var(--text-muted);padding:1px 5px;border-radius:4px;font-size:10px;">' + srcName + '</span>';

          return '<div class="item-card">' + img + '<div class="item-body">' +
            '<div class="item-title" title="' + escapeHtml(m.name) + '">' + escapeHtml(m.name) + '</div>' +
            '<div class="item-subtitle">' + (m.year || '') + (m.rewatch === 'Yes' ? ' • 🔁 Tekrar İzleme' : '') + ' • ' + srcBadge + '</div>' +
            '<div class="item-meta"><span>' + rating + '</span><span>•</span><span>' + (m.watched_date || '') + '</span></div>' +
            '</div></div>';
        }).join('');
      } catch (e) {
        console.error(e);
      }
    }

    /* Notification Relay Integration & Feed */
    function copyWebhookUrl() {
      const url = window.location.origin + '/api/webhooks/notification';
      navigator.clipboard.writeText(url).then(() => {
        const fb = document.getElementById('webhook-copy-feedback');
        if (fb) {
          fb.style.display = 'inline';
          setTimeout(() => { fb.style.display = 'none'; }, 2000);
        }
      }).catch(() => {
        prompt('Webhook URL kopyalayın:', url);
      });
    }

    async function loadNotifications() {
      try {
        const urlText = document.getElementById('webhook-url-text');
        if (urlText && window.location.origin) {
          urlText.innerText = window.location.origin + '/api/webhooks/notification';
        }

        const res = await authFetch('/api/notifications?limit=200');
        const data = await res.json();
        allNotifications = data.items || [];
        updateNotificationMetrics(allNotifications);
        populateAppFilter(allNotifications);
        renderNotifications(allNotifications);
      } catch (e) {
        console.error(e);
        const list = document.getElementById('notifications-list');
        if (list) list.innerHTML = '<div style="color:var(--text-muted); padding:16px;">Bildirimler yüklenirken bir hata oluştu.</div>';
      }
    }

    function updateNotificationMetrics(items) {
      const total = items.length;
      const apps = new Set(items.map(i => i.app_name || i.package_name).filter(Boolean));
      const ongoing = items.filter(i => i.is_ongoing === 1 || i.is_ongoing === true).length;

      if (document.getElementById('m-notif-tab-total')) document.getElementById('m-notif-tab-total').innerText = total;
      if (document.getElementById('m-notif-tab-apps')) document.getElementById('m-notif-tab-apps').innerText = apps.size;
      if (document.getElementById('m-notif-tab-ongoing')) document.getElementById('m-notif-tab-ongoing').innerText = ongoing;
    }

    function populateAppFilter(items) {
      const select = document.getElementById('notif-app-filter');
      if (!select) return;
      const currentVal = select.value;
      const appMap = new Map();
      items.forEach(i => {
        const name = i.app_name || i.package_name;
        if (name) {
          appMap.set(name, (appMap.get(name) || 0) + 1);
        }
      });

      const sortedApps = Array.from(appMap.entries()).sort((a, b) => b[1] - a[1]);
      let html = '<option value="all">Tüm Uygulamalar (' + items.length + ')</option>';
      sortedApps.forEach(([name, count]) => {
        html += '<option value="' + escapeHtml(name) + '">' + escapeHtml(name) + ' (' + count + ')</option>';
      });
      select.innerHTML = html;
      if (currentVal && appMap.has(currentVal)) {
        select.value = currentVal;
      }
    }

    function renderNotifications(items) {
      const list = document.getElementById('notifications-list');
      if (!list) return;

      if (!items || items.length === 0) {
        list.innerHTML = '<div style="color:var(--text-muted); text-align:center; padding:36px 16px;">' +
          '<div style="font-size:32px; margin-bottom:8px;">🔔</div>' +
          '<div style="font-weight:600; font-size:14px; margin-bottom:4px; color:var(--text-main);">Henüz bildirim kaydı yok</div>' +
          '<div style="font-size:12.5px;">Android Notification Relay üzerinden gönderilen bildirimler burada listelenecektir.</div>' +
          '</div>';
        return;
      }

      list.innerHTML = items.map(n => {
        const isOngoing = n.is_ongoing === 1 || n.is_ongoing === true;
        const appName = n.app_name || n.package_name || 'Bilinmeyen Uygulama';
        const timeStr = formatNotificationTime(n.post_time || n.created_at);
        const ongoingBadge = isOngoing ? '<span class="notif-ongoing-tag">⚡ Devam Eden</span>' : '';
        const text = n.text || n.big_text || '';
        const sub = (n.sub_text && n.sub_text !== text) ? '<div class="notif-sub">' + escapeHtml(n.sub_text) + '</div>' : '';

        return '<div class="notif-item">' +
          '<div class="notif-header">' +
            '<div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">' +
              '<span class="notif-app-tag">📱 ' + escapeHtml(appName) + '</span>' +
              ongoingBadge +
              '<span class="notif-pkg">' + escapeHtml(n.package_name || '') + '</span>' +
            '</div>' +
            '<span class="notif-time">🕒 ' + escapeHtml(timeStr) + '</span>' +
          '</div>' +
          (n.title ? '<div class="notif-title">' + escapeHtml(n.title) + '</div>' : '') +
          (text ? '<div class="notif-body">' + escapeHtml(text) + '</div>' : '') +
          sub +
        '</div>';
      }).join('');
    }

    function formatNotificationTime(ts) {
      if (!ts) return '';
      try {
        const d = new Date(ts);
        if (isNaN(d.getTime())) return String(ts);
        return d.toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit', year: 'numeric' }) + ' ' +
          d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
      } catch {
        return String(ts);
      }
    }

    function filterNotifications() {
      const q = (document.getElementById('notif-search')?.value || '').toLowerCase().trim();
      const app = document.getElementById('notif-app-filter')?.value || 'all';
      const ongoingOnly = document.getElementById('notif-ongoing-filter')?.checked || false;

      const filtered = allNotifications.filter(n => {
        const appName = (n.app_name || n.package_name || '').toLowerCase();
        const title = (n.title || '').toLowerCase();
        const text = (n.text || '').toLowerCase();
        const bigText = (n.big_text || '').toLowerCase();
        const pkg = (n.package_name || '').toLowerCase();
        const isOngoing = n.is_ongoing === 1 || n.is_ongoing === true;

        if (ongoingOnly && !isOngoing) return false;
        if (app !== 'all' && (n.app_name || n.package_name) !== app) return false;

        if (!q) return true;
        return appName.includes(q) || title.includes(q) || text.includes(q) || bigText.includes(q) || pkg.includes(q);
      });

      renderNotifications(filtered);
    }

    function copyHealthWebhookUrl() {
      const url = window.location.origin + '/api/webhooks/health-connect';
      navigator.clipboard.writeText(url).then(() => {
        const feedback = document.getElementById('health-webhook-copy-feedback');
        if (feedback) {
          feedback.style.display = 'inline';
          setTimeout(() => { feedback.style.display = 'none'; }, 2000);
        }
      }).catch(() => {
        prompt('Health Connect webhook URL kopyalayın:', url);
      });
    }

    async function loadHealthRecords() {
      try {
        const urlText = document.getElementById('health-webhook-url-text');
        if (urlText && window.location.origin) {
          urlText.innerText = window.location.origin + '/api/webhooks/health-connect';
        }

        const res = await authFetch('/api/health-connect/records?limit=200');
        if (!res.ok) throw new Error('Health Connect kayıtları yüklenemedi');
        const data = await res.json();
        allHealthRecords = data.items || [];
        updateHealthMetrics(data.summary || {});
        renderHealthRecords(allHealthRecords);
      } catch (e) {
        console.error(e);
        const list = document.getElementById('health-records-list');
        if (list) list.innerHTML = '<div style="color:var(--text-muted); padding:16px;">Health Connect kayıtları yüklenirken bir hata oluştu.</div>';
      }
    }

    function updateHealthMetrics(summary) {
      const steps = Math.round(Number(summary.today_steps) || 0);
      const calories = Math.round(Number(summary.today_active_calories) || 0);
      const heartRate = Math.round(Number(summary.latest_heart_rate_bpm) || 0);
      const total = Number(summary.total_records) || 0;
      const stepsDate = summary.steps_date || '';

      const overviewSteps = document.getElementById('m-health-steps');
      if (overviewSteps) overviewSteps.innerText = steps.toLocaleString('tr-TR');
      const overviewSub = document.getElementById('m-health-sub');
      if (overviewSub && stepsDate) overviewSub.innerText = 'Health Connect · ' + formatHealthDate(stepsDate);

      document.getElementById('m-health-tab-steps').innerText = steps.toLocaleString('tr-TR');
      document.getElementById('m-health-tab-steps-date').innerText = stepsDate ? formatHealthDate(stepsDate) : 'Kayıt bekleniyor';
      document.getElementById('m-health-tab-calories').innerText = calories.toLocaleString('tr-TR') + ' kcal';
      document.getElementById('m-health-tab-heart-rate').innerText = heartRate ? heartRate + ' bpm' : '—';
      document.getElementById('m-health-tab-total').innerText = total.toLocaleString('tr-TR');
    }

    function renderHealthRecords(items) {
      const list = document.getElementById('health-records-list');
      if (!list) return;
      if (!items || items.length === 0) {
        list.innerHTML = '<div style="color:var(--text-muted); text-align:center; padding:36px 16px;">' +
          '<div style="font-size:32px; margin-bottom:8px;">♥</div>' +
          '<div style="font-weight:600; font-size:14px; margin-bottom:4px; color:var(--text-main);">Henüz sağlık kaydı yok</div>' +
          '<div style="font-size:12.5px;">Health Connect webhook ile gönderilen kayıtlar burada listelenecek.</div>' +
          '</div>';
        return;
      }

      list.innerHTML = items.map(record => {
        const origin = formatHealthOrigin(record.data_origin);
        return '<div class="health-record">' +
          '<div class="health-record-icon">' + healthRecordIcon(record.record_type) + '</div>' +
          '<div class="health-record-body">' +
            '<div class="health-record-title">' + escapeHtml(record.title || healthRecordLabel(record.record_type)) + '</div>' +
            (record.subtitle ? '<div class="health-record-sub">' + escapeHtml(record.subtitle) + '</div>' : '') +
            '<div class="health-record-meta">' + escapeHtml(formatHealthDateTime(record.end_time || record.start_time || record.date_iso)) + '</div>' +
          '</div>' +
          (origin ? '<span class="health-origin-tag" title="' + escapeHtml(origin) + '">' + escapeHtml(origin) + '</span>' : '') +
          '</div>';
      }).join('');
    }

    function filterHealthRecords() {
      const query = (document.getElementById('health-search')?.value || '').toLowerCase().trim();
      const category = document.getElementById('health-category-filter')?.value || 'all';
      const filtered = allHealthRecords.filter(record => {
        if (category !== 'all' && record.category !== category) return false;
        if (!query) return true;
        return [record.title, record.subtitle, record.record_type, record.data_origin, record.date_iso]
          .some(value => String(value || '').toLowerCase().includes(query));
      });
      renderHealthRecords(filtered);
    }

    function healthRecordIcon(type) {
      const icons = {
        steps: '♟', distance: '↗', active_calories: '⚡', total_calories: '◉', exercise: '⌁',
        sleep: '☾', heart_rate: '♥', resting_heart_rate: '♥', weight: '⚖',
        blood_pressure: '♥', oxygen_saturation: '◌', hydration: '◒', nutrition: '◍'
      };
      return icons[type] || '♥';
    }

    function healthRecordLabel(type) {
      const labels = { steps: 'Adımlar', distance: 'Mesafe', active_calories: 'Aktif Kalori', exercise: 'Antrenman', sleep: 'Uyku', heart_rate: 'Nabız', weight: 'Ağırlık' };
      return labels[type] || 'Sağlık Kaydı';
    }

    function formatHealthOrigin(origin) {
      if (!origin) return '';
      const value = String(origin).toLowerCase();
      if (value.includes('shealth') || value.includes('samsung')) return 'Samsung Health';
      if (value.includes('google') && value.includes('fitness')) return 'Google Fit';
      if (value.includes('fitbit')) return 'Fitbit';
      if (value.includes('garmin')) return 'Garmin Connect';
      if (value.includes('xiaomi') || value.includes('mihealth') || value.includes('zepp')) return 'Zepp / Mi Fitness';
      if (value.includes('healthconnect')) return 'Health Connect';
      return String(origin);
    }

    function formatHealthDate(date) {
      if (!date) return '';
      try { return new Date(date + 'T00:00:00').toLocaleDateString('tr-TR', { day: '2-digit', month: 'long', year: 'numeric' }); }
      catch { return String(date); }
    }

    function formatHealthDateTime(value) {
      if (!value) return '';
      try {
        const date = new Date(value);
        if (isNaN(date.getTime())) return String(value);
        return date.toLocaleDateString('tr-TR', { day: '2-digit', month: 'long', year: 'numeric' }) + ' · ' +
          date.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
      } catch { return String(value); }
    }

    async function saveCalorieTarget() {
      const val = document.getElementById('calorie-target-input').value;
      try {
        const res = await authFetch('/settings/calorie-target?target=' + val, { method: 'POST' });
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
        const res = await authFetch('/api/sync/' + source, { method: 'POST' });
        const data = await res.json();
        if (res.status === 429) {
          alert('⏳ ' + (data.error || 'Lütfen senkronizasyon bekleme süresinin bitmesini bekleyin.'));
          return;
        }
        alert('Sonuç: ' + (data.message || (data.inserted + ' eklendi, ' + data.updated + ' güncellendi')));
        loadDashboard();
        if (source === 'fatsecret') { loadNutrition(); loadTimeline(); }
        if (source === 'goodreads') loadBooks();
        if (source === 'letterboxd') loadMovies();
        if (source === 'trakt' || source === 'simkl') loadTimeline();
        if (source === 'mdblist') loadMovies();
      } catch (e) {
        alert('Hata: ' + e.message);
      }
    }

    function escapeHtml(str) {
      if (!str) return '';
      return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function toggleTheme() {
      var isDark = document.documentElement.classList.toggle('dark');
      try {
        localStorage.setItem('theme', isDark ? 'dark' : 'light');
      } catch (e) {}
    }

    // Initialize with Security Layer Check
    checkAuthAndLoad();
  </script>

  <!-- Auth Modal Overlay -->
  <div id="auth-overlay" class="auth-overlay hidden">
    <div class="auth-card">
      <div class="auth-icon-wrap">
        <svg width="22" height="22" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
        </svg>
      </div>
      <h2 class="auth-title">Personal Records</h2>
      <p class="auth-desc">Bu sunucu korumalıdır. Gösterge paneline erişmek için lütfen API Anahtarınızı (şifrenizi) girin.</p>
      <form id="auth-form" onsubmit="handleLoginSubmit(event)">
        <div class="auth-field">
          <label class="auth-label" for="auth-key-input">Erişim Anahtarı / Şifre</label>
          <div class="auth-input-wrap">
            <input type="password" id="auth-key-input" placeholder="••••••••••••" autocomplete="current-password" required />
            <button type="button" class="auth-toggle-btn" onclick="togglePasswordVisibility()" title="Göster / Gizle">👁️</button>
          </div>
        </div>
        <div id="auth-error" class="auth-error hidden"></div>
        <button type="submit" id="auth-submit-btn" class="btn btn-primary" style="width: 100%; height: 40px; font-weight: 600;">
          Giriş Yap
        </button>
      </form>
    </div>
  </div>
</body>
</html>`;
}
