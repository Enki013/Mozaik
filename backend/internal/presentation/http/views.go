package http

import (
	"fmt"
	"html"
	"strings"

	"personal-records/internal/application"
	"personal-records/internal/config"
	"personal-records/internal/infrastructure/sqlite"
)

type pageData struct {
	DBName             string
	Counts             map[string]int
	Dashboard          application.Dashboard
	Overview           application.OverviewStats
	Activity           application.ActivityResponse
	ActivityDays       int
	ActivityFromDate   string
	ActivityToDate     string
	Books              []sqlite.GoodreadsBook
	Shelves            []string
	SelectedShelf      string
	Search             string
	GoodreadsSettings  sqlite.SyncSettings
	LetterboxdSettings sqlite.SyncSettings
	LetterboxdFilms    []sqlite.LetterboxdFilm
	FoodSummary        []sqlite.DailySummary
	FoodEntries        []sqlite.FoodEntry
	Notifications      []sqlite.Notification
	FatSecretConnected bool
	OAuthReady         bool
	CalorieTarget      int
	RecentSyncRuns     []sqlite.SyncRun
}

func renderHome(data pageData) string {
	var b strings.Builder
	b.WriteString(`<!doctype html>
<html lang="tr" class="h-full">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Personal Data Hub</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
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

      /* Aliases for seamless component compatibility */
      --bg: #ffffff;
      --card-bg: #ffffff;
      --card-border: #e4e4e7;
      --text-main: #09090b;
      --text-muted: #71717a;
      --text-subtle: #a1a1aa;
      --primary-hover: #27272a;
      --secondary-border: #d4d4d8;

      /* Semantic Badges */
      --badge-green-bg: #f0fdf4;
      --badge-green-text: #15803d;
      --badge-green-border: #bbf7d0;
      --badge-amber-bg: #fffbeb;
      --badge-amber-text: #b45309;
      --badge-amber-border: #fde68a;
      --badge-blue-bg: #eff6ff;
      --badge-blue-text: #1d4ed8;
      --badge-blue-border: #bfdbfe;
      --badge-purple-bg: #faf5ff;
      --badge-purple-text: #7e22ce;
      --badge-purple-border: #e9d5ff;
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
      --text-subtle: #71717a;
      --primary-hover: #e4e4e7;
      --secondary-border: #3f3f46;

      --badge-green-bg: #052e16;
      --badge-green-text: #4ade80;
      --badge-green-border: #14532d;
      --badge-amber-bg: #451a03;
      --badge-amber-text: #fbbf24;
      --badge-amber-border: #78350f;
      --badge-blue-bg: #172554;
      --badge-blue-text: #60a5fa;
      --badge-blue-border: #1e3a8a;
      --badge-purple-bg: #3b0764;
      --badge-purple-text: #c084fc;
      --badge-purple-border: #581c87;
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background-color: hsl(var(--background));
      color: hsl(var(--foreground));
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 14px;
      line-height: 1.5;
      -webkit-font-smoothing: antialiased;
      -moz-osx-font-smoothing: grayscale;
      transition: background-color 0.2s ease, color 0.2s ease;
    }

    .app-container {
      max-width: 1280px;
      margin: 0 auto;
      padding: 28px 24px 80px;
    }

    /* Header */
    .app-header {
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
      gap: 12px;
    }
    .brand-logo {
      width: 38px;
      height: 38px;
      border-radius: var(--radius);
      background: hsl(var(--primary));
      color: hsl(var(--primary-foreground));
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    }
    .brand-title {
      font-size: 19px;
      font-weight: 700;
      color: hsl(var(--foreground));
      letter-spacing: -0.025em;
    }
    .brand-subtitle {
      font-size: 12.5px;
      color: hsl(var(--muted-foreground));
    }
    .header-badges {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }

    /* Theme toggle button */
    .theme-toggle-btn {
      cursor: pointer;
      font-size: 12px;
      font-weight: 500;
    }
    .dark .sun-icon { display: inline-flex; align-items: center; gap: 4px; }
    .dark .moon-icon { display: none; }
    :not(.dark) .sun-icon { display: none; }
    :not(.dark) .moon-icon { display: inline-flex; align-items: center; gap: 4px; }

    /* Security Warning Banner */
    .security-banner {
      background: #fffbeb;
      border: 1px solid #fde68a;
      color: #92400e;
      border-radius: var(--radius);
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

    /* Tabs (shadcn/ui Tabs Style) */
    .tabs-wrapper {
      margin-bottom: 24px;
      overflow-x: auto;
      padding-bottom: 4px;
    }
    .tabs-list {
      display: inline-flex;
      background: hsl(var(--muted));
      padding: 3px;
      border-radius: var(--radius);
      border: 1px solid hsl(var(--border));
      gap: 2px;
      align-items: center;
    }
    .tab-trigger {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 6px 14px;
      font-size: 13px;
      font-weight: 500;
      color: hsl(var(--muted-foreground));
      border-radius: calc(var(--radius) - 2px);
      border: 0;
      background: transparent;
      cursor: pointer;
      white-space: nowrap;
      transition: all 0.15s ease;
      text-decoration: none;
    }
    .tab-trigger:hover {
      color: hsl(var(--foreground));
    }
    .tab-trigger.active {
      background: hsl(var(--card));
      color: hsl(var(--foreground));
      font-weight: 600;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08), 0 1px 2px rgba(0, 0, 0, 0.04);
    }

    /* Tab Panes */
    .tab-pane {
      display: none;
    }
    .tab-pane.active {
      display: block;
      animation: fadeIn 0.15s ease-in-out;
    }
    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(2px); }
      to { opacity: 1; transform: translateY(0); }
    }

    /* Cards */
    .card {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: calc(var(--radius) + 2px);
      box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.03);
      overflow: hidden;
      margin-bottom: 24px;
    }
    .card-header {
      padding: 18px 22px 14px;
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      border-bottom: 1px solid hsl(var(--border) / 0.7);
    }
    .card-title {
      font-size: 16px;
      font-weight: 600;
      color: hsl(var(--foreground));
      letter-spacing: -0.015em;
    }
    .card-desc {
      font-size: 13px;
      color: hsl(var(--muted-foreground));
      margin-top: 2px;
    }
    .card-content {
      padding: 20px 22px;
    }
    .card-header + .card-content {
      padding-top: 16px;
    }

    /* Metric Cards (shadcn/ui Dashboard Stat Cards) */
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
    }
    .metric-card {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: var(--radius);
      padding: 18px 20px;
      box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03);
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }
    .metric-card:hover {
      border-color: hsl(var(--ring) / 0.25);
      box-shadow: 0 3px 6px -1px rgba(0, 0, 0, 0.05);
    }
    .metric-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
    }
    .metric-label {
      font-size: 12px;
      font-weight: 500;
      color: hsl(var(--muted-foreground));
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .metric-value {
      font-size: 26px;
      font-weight: 700;
      color: hsl(var(--foreground));
      letter-spacing: -0.03em;
      line-height: 1.15;
    }
    .metric-note {
      font-size: 12px;
      color: hsl(var(--muted-foreground));
      margin-top: 6px;
    }

    /* Layouts */
    .grid-2col {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 24px;
      align-items: start;
    }
    .grid-3col {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 20px;
    }
    .stack-col {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    /* Badges (shadcn/ui Badge Variants) */
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 2.5px 9px;
      border-radius: 9999px;
      font-size: 11.5px;
      font-weight: 500;
      line-height: 1.3;
      white-space: nowrap;
      border: 1px solid transparent;
      transition: all 0.15s ease;
    }
    .badge-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }
    .badge-slate {
      background: hsl(var(--secondary));
      color: hsl(var(--secondary-foreground));
      border-color: hsl(var(--border));
    }
    .badge-slate .badge-dot { background: hsl(var(--muted-foreground)); }
    .badge-green {
      background: var(--badge-green-bg);
      color: var(--badge-green-text);
      border-color: var(--badge-green-border);
    }
    .badge-green .badge-dot { background: #16a34a; }
    .badge-amber {
      background: var(--badge-amber-bg);
      color: var(--badge-amber-text);
      border-color: var(--badge-amber-border);
    }
    .badge-amber .badge-dot { background: #d97706; }
    .badge-blue {
      background: var(--badge-blue-bg);
      color: var(--badge-blue-text);
      border-color: var(--badge-blue-border);
    }
    .badge-blue .badge-dot { background: #2563eb; }
    .badge-purple {
      background: var(--badge-purple-bg);
      color: var(--badge-purple-text);
      border-color: var(--badge-purple-border);
    }
    .badge-purple .badge-dot { background: #7c3aed; }
    .badge-red {
      background: hsl(var(--destructive) / 0.1);
      color: hsl(var(--destructive));
      border-color: hsl(var(--destructive) / 0.2);
    }
    .badge-red .badge-dot { background: hsl(var(--destructive)); }

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
    .webhook-box code,
    .webhook-box span:first-child {
      color: hsl(var(--foreground)) !important;
      font-weight: 600;
      background: transparent !important;
      border: none !important;
      padding: 0 !important;
    }

    /* Buttons (shadcn/ui Button Variants) */
    .button, button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      border-radius: calc(var(--radius) - 2px);
      font-size: 13px;
      font-weight: 500;
      padding: 7px 14px;
      cursor: pointer;
      transition: all 0.15s ease;
      text-decoration: none;
      border: 1px solid transparent;
      min-height: 36px;
      white-space: nowrap;
      flex-shrink: 0;
      outline: none;
    }
    .button-primary, button:not(.secondary):not(.outline):not(.ghost):not(.filter-chip):not(.tab-trigger) {
      background: hsl(var(--primary));
      color: hsl(var(--primary-foreground));
      border-color: transparent;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    }
    .button-primary:hover, button:not(.secondary):not(.outline):not(.ghost):not(.filter-chip):not(.tab-trigger):hover {
      background: hsl(var(--primary) / 0.9);
    }
    .button.secondary, button.secondary {
      background: hsl(var(--card));
      color: hsl(var(--foreground));
      border: 1px solid hsl(var(--border));
      box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.02);
    }
    .button.secondary:hover, button.secondary:hover {
      background: hsl(var(--muted));
      border-color: hsl(var(--border));
    }
    .button-sm {
      padding: 4px 10px;
      font-size: 12px;
      min-height: 30px;
      border-radius: calc(var(--radius) - 3px);
    }

    /* Forms & Inputs */
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin-bottom: 14px;
    }
    .form-label {
      font-size: 13px;
      font-weight: 500;
      color: hsl(var(--foreground));
    }
    .form-helper {
      font-size: 12px;
      color: hsl(var(--muted-foreground));
    }
    input[type="text"], input[type="number"], input[type="file"], select {
      width: 100%;
      border: 1px solid hsl(var(--input));
      border-radius: calc(var(--radius) - 2px);
      padding: 7px 12px;
      font-size: 13px;
      color: hsl(var(--foreground));
      background: hsl(var(--background));
      transition: border-color 0.15s, box-shadow 0.15s;
    }
    input[type="text"]:focus, input[type="number"]:focus, select:focus {
      outline: none;
      border-color: hsl(var(--ring));
      box-shadow: 0 0 0 1px hsl(var(--ring));
    }
    .form-inline {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    /* Tables */
    .table-container {
      width: 100%;
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13.5px;
      text-align: left;
    }
    th {
      background: hsl(var(--muted) / 0.5);
      color: hsl(var(--muted-foreground));
      font-weight: 600;
      font-size: 11.5px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      padding: 10px 14px;
      border-bottom: 1px solid hsl(var(--border));
      white-space: nowrap;
    }
    td {
      padding: 12px 14px;
      border-bottom: 1px solid hsl(var(--border) / 0.6);
      color: hsl(var(--foreground));
      vertical-align: middle;
    }
    tr:hover td {
      background: hsl(var(--muted) / 0.35);
    }
    tr:last-child td {
      border-bottom: 0;
    }

    /* Stars */
    .stars {
      color: #f59e0b;
      font-size: 13.5px;
      letter-spacing: 1px;
      display: inline-flex;
      align-items: center;
      gap: 2px;
    }
    .star-empty { color: hsl(var(--muted-foreground) / 0.4); }
    .rating-num {
      color: hsl(var(--muted-foreground));
      font-size: 12px;
      font-weight: 600;
      margin-left: 4px;
    }

    /* Progress Bars */
    .progress-bar-wrap {
      height: 8px;
      background: hsl(var(--muted));
      border-radius: 9999px;
      overflow: hidden;
      display: flex;
      margin: 10px 0;
    }
    .progress-bar-segment {
      height: 100%;
      transition: width 0.3s ease;
    }
    .macro-legend {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin-top: 10px;
    }
    .macro-item {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12.5px;
      color: hsl(var(--muted-foreground));
    }
    .macro-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    /* Filter Bar System */
    .filter-bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 12px 18px;
      background: hsl(var(--muted) / 0.4);
      border-bottom: 1px solid hsl(var(--border));
    }
    .filter-controls {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 10px;
      flex: 1;
    }
    .filter-input-wrapper {
      position: relative;
      min-width: 220px;
      max-width: 320px;
      flex: 1;
    }
    .filter-input-wrapper svg {
      position: absolute;
      left: 10px;
      top: 50%;
      transform: translateY(-50%);
      color: hsl(var(--muted-foreground));
      pointer-events: none;
    }
    .filter-input {
      width: 100%;
      padding: 7px 10px 7px 32px !important;
      font-size: 13px !important;
      background: hsl(var(--background)) !important;
      border: 1px solid hsl(var(--input)) !important;
      border-radius: calc(var(--radius) - 2px) !important;
      outline: none;
      transition: border-color 0.15s, box-shadow 0.15s;
    }
    .filter-input:focus {
      border-color: hsl(var(--ring)) !important;
      box-shadow: 0 0 0 1px hsl(var(--ring)) !important;
    }
    .filter-chips {
      display: inline-flex;
      flex-wrap: wrap;
      gap: 6px;
      align-items: center;
    }
    .filter-chip {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 4px 11px;
      border-radius: calc(var(--radius) - 2px);
      font-size: 12px;
      font-weight: 500;
      background: hsl(var(--muted) / 0.7);
      color: hsl(var(--muted-foreground));
      border: 1px solid hsl(var(--border));
      cursor: pointer;
      user-select: none;
      transition: all 0.15s ease;
      min-height: 28px;
    }
    .filter-chip:hover {
      background: hsl(var(--muted));
      color: hsl(var(--foreground));
    }
    .filter-chip.active {
      background: hsl(var(--primary)) !important;
      color: hsl(var(--primary-foreground)) !important;
      border-color: hsl(var(--primary)) !important;
      font-weight: 600 !important;
    }
    .filter-count-badge {
      font-size: 12px;
      color: hsl(var(--muted-foreground));
      font-weight: 500;
      white-space: nowrap;
    }
    .filter-clear-btn {
      background: transparent;
      border: none;
      color: hsl(var(--destructive));
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      padding: 3px 6px;
      text-decoration: underline;
      display: none;
      min-height: auto;
    }
    .filter-clear-btn:hover {
      opacity: 0.8;
    }

    /* Media Thumbnails */
    .media-thumb {
      width: 32px;
      height: 46px;
      border-radius: 4px;
      object-fit: cover;
      flex-shrink: 0;
      background: hsl(var(--muted));
      border: 1px solid hsl(var(--border));
      box-shadow: 0 1px 2px rgba(0,0,0,0.05);
    }
    .media-thumb-placeholder {
      width: 32px;
      height: 46px;
      border-radius: 4px;
      flex-shrink: 0;
      background: hsl(var(--muted));
      border: 1px solid hsl(var(--border));
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      color: hsl(var(--muted-foreground));
    }
    .media-cell {
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 220px;
    }
    .media-cell-info {
      min-width: 0;
    }

    /* Calorie Budget Card */
    .budget-card {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: var(--radius);
      padding: 18px 22px;
      margin-bottom: 20px;
    }
    .budget-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
      flex-wrap: wrap;
      gap: 10px;
    }
    .budget-stats {
      display: flex;
      gap: 20px;
      align-items: baseline;
      flex-wrap: wrap;
    }

    /* Table Sorting */
    th.sortable {
      cursor: pointer;
      user-select: none;
      transition: background 0.15s ease, color 0.15s ease;
      white-space: nowrap;
    }
    th.sortable:hover {
      background: hsl(var(--muted));
      color: hsl(var(--foreground));
    }
    .sort-icon {
      display: inline-block;
      margin-left: 5px;
      font-size: 11px;
      color: hsl(var(--muted-foreground));
      vertical-align: middle;
    }
    th.sort-desc .sort-icon,
    th.sort-asc .sort-icon {
      color: hsl(var(--foreground));
      font-weight: 700;
    }

    /* Date Picker Bar */
    .date-picker-bar {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 22px;
      background: hsl(var(--muted) / 0.4);
      border-bottom: 1px solid hsl(var(--border));
      flex-wrap: wrap;
    }
    .date-input {
      padding: 5px 10px;
      font-size: 12.5px;
      border: 1px solid hsl(var(--input));
      border-radius: calc(var(--radius) - 2px);
      background: hsl(var(--background));
      color: hsl(var(--foreground));
      outline: none;
      font-family: inherit;
    }
    .date-input:focus {
      border-color: hsl(var(--ring));
      box-shadow: 0 0 0 1px hsl(var(--ring));
    }

    /* Timeline styling */
    .timeline-day {
      margin-bottom: 24px;
    }
    .timeline-day-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 14px;
      background: hsl(var(--muted) / 0.5);
      border-radius: var(--radius);
      margin-bottom: 10px;
      border: 1px solid hsl(var(--border));
    }
    .timeline-day-title {
      font-size: 14px;
      font-weight: 600;
      color: hsl(var(--foreground));
    }
    .timeline-day-summary {
      font-size: 12.5px;
      color: hsl(var(--muted-foreground));
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .timeline-items {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .timeline-item {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: var(--radius);
      padding: 12px 16px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 14px;
      transition: border-color 0.15s, box-shadow 0.15s;
    }
    .timeline-item:hover {
      border-color: hsl(var(--ring) / 0.3);
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.03);
    }
    .timeline-item-main {
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 0;
    }
    .timeline-item-icon {
      width: 34px;
      height: 34px;
      border-radius: calc(var(--radius) - 2px);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .timeline-item-info {
      min-width: 0;
    }
    .timeline-item-title {
      font-size: 14px;
      font-weight: 600;
      color: hsl(var(--foreground));
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .timeline-item-sub {
      font-size: 12.5px;
      color: hsl(var(--muted-foreground));
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .timeline-item-right {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-shrink: 0;
    }

    .connection-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 14px 0;
      border-bottom: 1px solid hsl(var(--border) / 0.6);
      gap: 16px;
    }
    .connection-row:last-child {
      border-bottom: 0;
      padding-bottom: 0;
    }
    .connection-info {
      flex: 1;
      min-width: 0;
    }
    .connection-info h4 {
      font-size: 14px;
      font-weight: 600;
      color: hsl(var(--foreground));
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .connection-status-text {
      font-size: 12px;
      color: hsl(var(--muted-foreground));
      margin-top: 2px;
      line-height: 1.4;
      overflow-wrap: break-word;
      word-break: break-word;
    }
    .connection-row form {
      flex-shrink: 0;
      display: flex;
      align-items: center;
    }
    .connection-row .button, .connection-row button {
      white-space: nowrap;
      flex-shrink: 0;
      min-width: 82px;
    }

    /* Empty State */
    .empty-state {
      padding: 40px 20px;
      text-align: center;
      color: hsl(var(--muted-foreground));
    }
    .empty-icon {
      font-size: 32px;
      margin-bottom: 10px;
    }

    /* Notification Feed */
    .notif-feed-item {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: var(--radius);
      padding: 14px 16px;
      margin-bottom: 12px;
      transition: all 0.15s ease;
    }
    .notif-feed-item:hover {
      border-color: hsl(var(--ring) / 0.3);
      box-shadow: 0 2px 6px rgba(0,0,0,0.04);
    }
    .notif-feed-item-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 6px;
    }
    .notif-feed-item-title {
      font-size: 14px;
      font-weight: 600;
      color: hsl(var(--foreground));
    }
    .notif-feed-item-body {
      font-size: 13px;
      color: hsl(var(--muted-foreground));
      line-height: 1.45;
      margin-top: 4px;
    }
    .notif-feed-item-sub {
      font-size: 12px;
      color: hsl(var(--muted-foreground));
      margin-top: 4px;
      font-style: italic;
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

    /* Modal / Dialog */
    .modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.6);
      backdrop-filter: blur(4px);
      z-index: 999;
      display: none;
      align-items: center;
      justify-content: center;
      padding: 16px;
    }
    .modal-overlay.show {
      display: flex;
    }
    .modal-card {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: calc(var(--radius) + 4px);
      max-width: 480px;
      width: 100%;
      padding: 24px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.2);
    }
    .modal-body {
      color: hsl(var(--muted-foreground));
      font-size: 13px;
      margin: 12px 0 20px;
    }

    /* Responsive */
    @media (max-width: 1024px) {
      .metrics-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .grid-2col { grid-template-columns: 1fr; }
      .grid-3col { grid-template-columns: 1fr; }
    }
    @media (max-width: 640px) {
      .app-container { padding: 18px 14px 60px; }
      .metrics-grid { grid-template-columns: 1fr; }
      .timeline-item { flex-direction: column; align-items: flex-start; gap: 8px; }
      .timeline-item-right { width: 100%; justify-content: space-between; }
    }
  </style>
</head>
<body>
  <div class="app-container">
    <!-- Header -->
    <header class="app-header">
      <div class="brand">
        <div class="brand-logo">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1-2.5-2.5Z"/>
            <path d="M6 6h10"/>
            <path d="M6 10h10"/>
          </svg>
        </div>
        <div>
          <h1 class="brand-title">Personal Data Hub</h1>
          <div class="brand-subtitle">FatSecret · Goodreads · Letterboxd · SQLite</div>
        </div>
      </div>
      <div class="header-badges">
        <span class="badge badge-slate" title="Aktif Veritabanı">
          <span class="badge-dot"></span>
          SQLite: ` + esc(data.DBName) + `
        </span>
        <span class="badge badge-slate" title="Kayıtlı Bildirim Sayısı">
          <span class="badge-dot"></span>
          🔔 ` + number(len(data.Notifications)) + ` Bildirim
        </span>
        ` + statusBadge("FatSecret", data.FatSecretConnected && data.OAuthReady) + `
        ` + statusBadge("Goodreads", data.GoodreadsSettings.FeedURL != "") + `
        ` + statusBadge("Letterboxd", data.LetterboxdSettings.FeedURL != "") + `
        <button type="button" class="button secondary button-sm theme-toggle-btn" onclick="toggleTheme()" title="Temayı Değiştir" aria-label="Temayı Değiştir">
          <span class="sun-icon">☀️ Aydınlık</span>
          <span class="moon-icon">🌙 Karanlık</span>
        </button>
        ` + func() string {
          if config.Env("API_KEY") != "" {
            return `<form action="/api/auth/logout" method="POST" style="display:inline-block; margin:0;"><button type="submit" class="button secondary button-sm" title="Oturumu Kapat" style="cursor:pointer;">🔒 Çıkış</button></form>`
          }
          return ""
        }() + `
      </div>
    </header>

    ` + func() string {
      if config.Env("API_KEY") == "" {
        return `<div class="security-banner">
          <span style="font-size:16px;">⚠️</span>
          <div>
            <b>Güvenlik Uyarısı:</b> API_KEY ortam değişkeni henüz tanımlanmadı. Sunucunuz şu an herkese açıktır.
            <span style="opacity:0.85; margin-left: 4px;"><code>.env</code> dosyasına <code>API_KEY</code> tanımlayarak korumayı etkinleştirebilirsiniz.</span>
          </div>
        </div>`
      }
      return ""
    }() + `

    <!-- Tabs Navigation -->
    <nav class="tabs-wrapper" aria-label="Sekmeler">
      <div class="tabs-list" role="tablist">
        <button class="tab-trigger active" data-tab="overview" role="tab" aria-selected="true">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="7" height="9" x="3" y="3" rx="1"/><rect width="7" height="5" x="14" y="3" rx="1"/><rect width="7" height="9" x="14" y="12" rx="1"/><rect width="7" height="5" x="3" y="16" rx="1"/></svg>
          Genel Bakış
        </button>
        <button class="tab-trigger" data-tab="timeline" role="tab" aria-selected="false">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v20"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>
          Zaman Çizelgesi
        </button>
        <button class="tab-trigger" data-tab="fatsecret" role="tab" aria-selected="false">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a8 8 0 0 0-8 8v12l3-3 2.5 2.5L12 19l2.5 2.5L17 19l3 3V10a8 8 0 0 0-8-8z"/></svg>
          Beslenme (FatSecret)
        </button>
        <button class="tab-trigger" data-tab="goodreads" role="tab" aria-selected="false">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1-2.5-2.5Z"/></svg>
          Kitaplar (Goodreads)
        </button>
        <button class="tab-trigger" data-tab="letterboxd" role="tab" aria-selected="false">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="18" height="18" x="3" y="3" rx="2"/><path d="m9 8 6 4-6 4Z"/></svg>
          Filmler (Letterboxd)
        </button>
        <button class="tab-trigger" data-tab="notifications" role="tab" aria-selected="false">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>
          Bildirimler
        </button>
        <button class="tab-trigger" data-tab="settings" role="tab" aria-selected="false">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg>
          Ayarlar & İçe Aktarma
        </button>
      </div>
    </nav>

    <!-- Tab 1: Overview -->
    <div id="pane-overview" class="tab-pane active" role="tabpanel">
      ` + renderOverviewTab(data) + `
    </div>

    <!-- Tab 2: Timeline -->
    <div id="pane-timeline" class="tab-pane" role="tabpanel">
      ` + renderTimelineTab(data) + `
    </div>

    <!-- Tab 3: FatSecret -->
    <div id="pane-fatsecret" class="tab-pane" role="tabpanel">
      ` + renderFatSecretTab(data) + `
    </div>

    <!-- Tab 4: Goodreads -->
    <div id="pane-goodreads" class="tab-pane" role="tabpanel">
      ` + renderGoodreadsTab(data) + `
    </div>

    <!-- Tab 5: Letterboxd -->
    <div id="pane-letterboxd" class="tab-pane" role="tabpanel">
      ` + renderLetterboxdTab(data) + `
    </div>

    <!-- Tab 6: Notifications -->
    <div id="pane-notifications" class="tab-pane" role="tabpanel">
      ` + renderNotificationsTab(data) + `
    </div>

    <!-- Tab 7: Settings -->
    <div id="pane-settings" class="tab-pane" role="tabpanel">
      ` + renderSettingsTab(data) + `
    </div>
  </div>

  ` + renderSyncModal() + `
</body>
</html>`)
	return b.String()
}

func renderCalorieBudgetCard(stats sqlite.FatSecretStats, target int) string {
	if target <= 0 {
		target = 2200
	}
	todayCal := int(stats.TodayCalories)
	pct := 0
	if target > 0 {
		pct = (todayCal * 100) / target
	}
	progressWidth := pct
	if progressWidth > 100 {
		progressWidth = 100
	}

	remaining := target - todayCal
	remText := ""
	barColor := "#10b981"
	if remaining >= 0 {
		remText = fmt.Sprintf("%d kcal kaldı", remaining)
	} else {
		remText = fmt.Sprintf("%d kcal aşıldı", -remaining)
		barColor = "#ef4444"
	}
	if pct >= 90 && pct <= 100 {
		barColor = "#f59e0b"
	}

	return fmt.Sprintf(`
<div class="budget-card">
  <div class="budget-header">
    <div>
      <span style="font-weight:600; font-size:15px; color:#0f172a;">🎯 Günlük Kalori Hedefi</span>
      <span style="margin-left:8px; font-size:12.5px; color:#64748b;">Bugünkü tüketim ve kalan kalori takibi</span>
    </div>
    <div>
      <button class="button button-sm secondary" onclick="openCalorieModal(%d)" style="font-size:12px; padding:3px 10px;">
        ⚙️ Hedef: %d kcal
      </button>
    </div>
  </div>
  <div style="height:10px; margin-bottom:12px; background:#e2e8f0; border-radius:999px; overflow:hidden;">
    <div style="height:100%%; width:%d%%; background:%s; transition:width 0.4s ease; border-radius:999px;"></div>
  </div>
  <div class="budget-stats">
    <div>
      <span style="font-size:12px; color:#64748b;">Alınan:</span>
      <strong style="font-size:15px; margin-left:4px; color:#0f172a;">%d kcal</strong>
    </div>
    <div>
      <span style="font-size:12px; color:#64748b;">Hedef:</span>
      <strong style="font-size:15px; margin-left:4px; color:#0f172a;">%d kcal</strong>
    </div>
    <div>
      <span style="font-size:12px; color:#64748b;">İlerleme:</span>
      <strong style="font-size:15px; margin-left:4px; color:#0f172a;">%%%d</strong>
    </div>
    <div>
      <span style="font-size:12px; color:#64748b;">Durum:</span>
      <span style="font-size:13px; font-weight:600; margin-left:4px; color:%s;">%s</span>
    </div>
  </div>
</div>`,
		target, target,
		progressWidth, barColor,
		todayCal, target, pct,
		barColor, remText,
	)
}

func renderOverviewTab(data pageData) string {
	var b strings.Builder
	todayDate := fmt.Sprint(data.Dashboard.Today["date"])

	// Top 5 Metric Cards
	b.WriteString(`<div class="metrics-grid">`)
	b.WriteString(metricCard("Bugün Alınan Kalori", ratingZero(data.Overview.FatSecret.TodayCalories)+" kcal", fmt.Sprintf("%d öğün kaydı · %s", data.Overview.FatSecret.TodayEntries, todayDate), "FatSecret"))
	b.WriteString(metricCard("Kütüphane Durumu", number(data.Overview.Goodreads.Total)+" kitap", fmt.Sprintf("%d okundu · %d okunacak", data.Overview.Goodreads.Read, data.Overview.Goodreads.ToRead), "Goodreads"))
	b.WriteString(metricCard("İzlenen Filmler", number(data.Overview.Letterboxd.Total)+" film", fmt.Sprintf("%d puanlı · ort. %s", data.Overview.Letterboxd.Rated, rating(data.Overview.Letterboxd.AverageRating)), "Letterboxd"))
	b.WriteString(metricCard("Toplam Yemek Günlüğü", number(data.Overview.FatSecret.Entries)+" kayıt", fmt.Sprintf("%d gün kayıtlı · ort. %s kcal/gün", data.Overview.FatSecret.LoggedDays, rating(data.Overview.FatSecret.AverageCalories)), "Beslenme"))
	b.WriteString(metricCard("Gelen Bildirimler", number(len(data.Notifications))+" bildirim", "Android Bildirim İletimi", "Relay"))
	b.WriteString(`</div>`)

	// 2 Column Grid
	b.WriteString(`<div class="grid-2col">`)

	// Left: Recent Activity preview
	b.WriteString(`<div class="stack-col">`)
	b.WriteString(renderRecentActivityPreview(data.Activity))
	b.WriteString(`</div>`)

	// Right: Service Sync Controls
	b.WriteString(`<div class="stack-col">`)
	b.WriteString(renderSyncControlsCard(data))
	b.WriteString(`</div>`)

	b.WriteString(`</div>`)
	return b.String()
}

func metricCard(label, value, note, badgeText string) string {
	return fmt.Sprintf(`
<div class="metric-card">
  <div class="metric-header">
    <span class="metric-label">%s</span>
    <span class="badge badge-slate">%s</span>
  </div>
  <div class="metric-value">%s</div>
  <div class="metric-note">%s</div>
</div>`, esc(label), esc(badgeText), esc(value), esc(note))
}

func renderMacroBreakdownCard(stats sqlite.FatSecretStats) string {
	carbKcal := stats.TotalCarbohydrate * 4
	protKcal := stats.TotalProtein * 4
	fatKcal := stats.TotalFat * 9
	totalKcal := carbKcal + protKcal + fatKcal

	var carbPct, protPct, fatPct float64
	if totalKcal > 0 {
		carbPct = (carbKcal / totalKcal) * 100
		protPct = (protKcal / totalKcal) * 100
		fatPct = (fatKcal / totalKcal) * 100
	}

	return fmt.Sprintf(`
<div class="card">
  <div class="card-header">
    <div>
      <h2 class="card-title">Makro Besin Dağılımı</h2>
      <p class="card-desc">Toplam kaydedilen kalorilerin makro kaynaklara göre yüzdesel dağılımı</p>
    </div>
    <span class="badge badge-blue">Tüm Zamanlar</span>
  </div>
  <div class="card-content">
    <div class="progress-bar-wrap">
      <div class="progress-bar-segment" style="width: %.1f%%; background: #3b82f6;" title="Karbonhidrat: %.1f%%"></div>
      <div class="progress-bar-segment" style="width: %.1f%%; background: #10b981;" title="Protein: %.1f%%"></div>
      <div class="progress-bar-segment" style="width: %.1f%%; background: #f59e0b;" title="Yağ: %.1f%%"></div>
    </div>
    <div class="macro-legend">
      <div class="macro-item">
        <span class="macro-dot" style="background: #3b82f6;"></span>
        <span>Karbonhidrat: <strong>%s g</strong> (%%%.0f)</span>
      </div>
      <div class="macro-item">
        <span class="macro-dot" style="background: #10b981;"></span>
        <span>Protein: <strong>%s g</strong> (%%%.0f)</span>
      </div>
      <div class="macro-item">
        <span class="macro-dot" style="background: #f59e0b;"></span>
        <span>Yağ: <strong>%s g</strong> (%%%.0f)</span>
      </div>
    </div>
  </div>
</div>`, carbPct, carbPct, protPct, protPct, fatPct, fatPct, rating(stats.TotalCarbohydrate), carbPct, rating(stats.TotalProtein), protPct, rating(stats.TotalFat), fatPct)
}

func renderRecentActivityPreview(activity application.ActivityResponse) string {
	var b strings.Builder
	b.WriteString(`
<div class="card">
  <div class="card-header">
    <div>
      <h2 class="card-title">Son Aktiviteler</h2>
      <p class="card-desc">Öğün, kitap ve film akışından son kayıtlar</p>
    </div>
    <button class="button button-sm secondary" onclick="switchTab('timeline')">Tümünü Gör →</button>
  </div>
  <div class="card-content">`)

	if len(activity.Items) == 0 {
		b.WriteString(`<div class="empty-state"><div class="empty-icon">📂</div>Henüz bir aktivite kaydı yok.</div>`)
	} else {
		b.WriteString(`<div class="timeline-items">`)
		limit := 6
		if len(activity.Items) < limit {
			limit = len(activity.Items)
		}
		for i := 0; i < limit; i++ {
			b.WriteString(renderTimelineItemRow(activity.Items[i]))
		}
		b.WriteString(`</div>`)
	}
	b.WriteString(`</div></div>`)
	return b.String()
}

func renderTimelineItemRow(item application.ActivityItem) string {
	iconBg := "#f1f5f9"
	iconSvg := ""
	rightPills := ""

	switch item.Source {
	case "fatsecret":
		iconBg = "#ecfdf5"
		iconSvg = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#059669" stroke-width="2"><path d="M12 2a8 8 0 0 0-8 8v12l3-3 2.5 2.5L12 19l2.5 2.5L17 19l3 3V10a8 8 0 0 0-8-8z"/></svg>`
		cal := item.Details["calories"]
		meal := fmt.Sprint(item.Details["meal"])
		rightPills = formatMealBadge(meal)
		if cal != nil && fmt.Sprint(cal) != "0" {
			rightPills += fmt.Sprintf(` <span class="badge badge-slate">%v kcal</span>`, cal)
		}
	case "goodreads":
		iconBg = "#eff6ff"
		iconSvg = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2563eb" stroke-width="2"><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1-2.5-2.5Z"/></svg>`
		shelf := fmt.Sprint(item.Details["exclusive_shelf"])
		rightPills = fmt.Sprintf(`<span class="badge badge-blue">%s</span>`, esc(shelf))
		if r, ok := item.Details["my_rating"].(float64); ok && r > 0 {
			rightPills += fmt.Sprintf(` <span class="badge badge-amber">★ %.0f</span>`, r)
		}
	case "letterboxd":
		iconBg = "#faf5ff"
		iconSvg = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#7c3aed" stroke-width="2"><rect width="18" height="18" x="3" y="3" rx="2"/><path d="m9 8 6 4-6 4Z"/></svg>`
		if r, ok := item.Details["rating"].(float64); ok && r > 0 {
			rightPills = fmt.Sprintf(`<span class="badge badge-purple">★ %.1f</span>`, r)
		}
		if item.Rewatch {
			rightPills += ` <span class="badge badge-purple" style="font-size:11px;">🔄 Tekrar</span>`
		}
	case "notification":
		iconBg = "#fff7ed"
		iconSvg = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#ea580c" stroke-width="2"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>`
		appName := fmt.Sprint(item.Details["app_name"])
		if appName == "" || appName == "<nil>" {
			appName = item.Subtitle
		}
		rightPills = fmt.Sprintf(`<span class="badge badge-amber">%s</span>`, esc(appName))
		if isOngoing, ok := item.Details["is_ongoing"].(bool); ok && isOngoing {
			rightPills += ` <span class="badge badge-amber" style="font-size:11px;">⚡ Devam Eden</span>`
		}
	}

	thumbHtml := ""
	if item.ImageURL != "" {
		thumbHtml = fmt.Sprintf(`<img src="%s" class="media-thumb" style="width:28px; height:40px; margin-right:10px; border-radius:3px; object-fit:cover;" alt="" loading="lazy">`, esc(item.ImageURL))
	}

	searchContent := strings.ToLower(fmt.Sprintf("%s %s %s %s", item.Title, item.Subtitle, item.Date, item.Source))

	return fmt.Sprintf(`
<div class="timeline-item" data-source="%s" data-search="%s">
  <div class="timeline-item-main" style="display:flex; align-items:center;">
    <div class="timeline-item-icon" style="background: %s;">%s</div>
    %s
    <div class="timeline-item-info">
      <div class="timeline-item-title">%s</div>
      <div class="timeline-item-sub">%s · %s</div>
    </div>
  </div>
  <div class="timeline-item-right">%s</div>
</div>`, esc(item.Source), esc(searchContent), iconBg, iconSvg, thumbHtml, esc(item.Title), esc(item.Subtitle), esc(item.Date), rightPills)
}

func renderSyncControlsCard(data pageData) string {
	return fmt.Sprintf(`
<div class="card">
  <div class="card-header">
    <div>
      <h2 class="card-title">Entegrasyonlar & Senkronizasyon</h2>
      <p class="card-desc">Servis bağlantı durumları ve anlık tetikleme</p>
    </div>
  </div>
  <div class="card-content">
    <div class="connection-row">
      <div class="connection-info">
        <h4>FatSecret %s</h4>
        <div class="connection-status-text">%s</div>
      </div>
      <form method="post" action="/fatsecret/sync">
        <input type="hidden" name="days" value="30">
        <button class="button button-sm">Sync Et</button>
      </form>
    </div>

    <div class="connection-row">
      <div class="connection-info">
        <h4>Goodreads RSS %s</h4>
        <div class="connection-status-text">%s</div>
      </div>
      <form method="post" action="/goodreads/sync">
        <button class="button button-sm">Sync Et</button>
      </form>
    </div>

    <div class="connection-row">
      <div class="connection-info">
        <h4>Letterboxd RSS %s</h4>
        <div class="connection-status-text">%s</div>
      </div>
      <form method="post" action="/letterboxd/sync">
        <button class="button button-sm">Sync Et</button>
      </form>
    </div>
  </div>
</div>`,
		statusBadge("", data.FatSecretConnected && data.OAuthReady),
		syncRunText(data.Dashboard.LastSync["fatsecret"]),
		statusBadge("", data.GoodreadsSettings.FeedURL != ""),
		syncRunText(data.Dashboard.LastSync["goodreads"]),
		statusBadge("", data.LetterboxdSettings.FeedURL != ""),
		syncRunText(data.Dashboard.LastSync["letterboxd"]),
	)
}

func renderTimelineTab(data pageData) string {
	var b strings.Builder
	totalCount := 0
	for _, g := range data.Activity.Groups {
		totalCount += len(g.Items)
	}

	days := data.ActivityDays
	if days <= 0 {
		days = 30
	}

	hasCustomDate := data.ActivityFromDate != "" || data.ActivityToDate != ""
	clearDateStyle := "display:none;"
	customBadgeHtml := ""
	if hasCustomDate {
		clearDateStyle = "display:inline-block;"
		if data.ActivityFromDate == data.ActivityToDate {
			customBadgeHtml = fmt.Sprintf(`<span class="badge badge-blue" style="margin-left:auto; font-size:12px;">📅 Seçili Gün: %s</span>`, esc(data.ActivityFromDate))
		} else {
			customBadgeHtml = fmt.Sprintf(`<span class="badge badge-blue" style="margin-left:auto; font-size:12px;">📅 Özel Aralık: %s → %s</span>`, esc(data.ActivityFromDate), esc(data.ActivityToDate))
		}
	}

	rangeBtn := func(d int, label string) string {
		cls := "button button-sm secondary"
		if !hasCustomDate && days == d {
			cls = "button button-sm"
		}
		return fmt.Sprintf(`<a class="%s" href="/?days=%d#timeline" style="padding:4px 9px; font-size:12px;">%s</a>`, cls, d, label)
	}

	apiHref := fmt.Sprintf("/api/activity?days=%d", days)
	if hasCustomDate {
		apiHref = fmt.Sprintf("/api/activity?from=%s&to=%s", esc(data.ActivityFromDate), esc(data.ActivityToDate))
	}

	b.WriteString(fmt.Sprintf(`
<div class="card" style="margin-bottom:20px;">
  <div class="card-header">
    <div>
      <h2 class="card-title">Birleşik Zaman Çizelgesi</h2>
      <p class="card-desc">Öğünler, okunan kitaplar ve izlenen filmler günlük kronolojik sırayla listelenir.</p>
    </div>
    <div style="display:flex; gap:6px; align-items:center; flex-wrap:wrap;">
      <span style="font-size:12px; color:var(--text-muted); font-weight:500; margin-right:4px;">Aralık:</span>
      %s
      %s
      %s
      %s
      %s
      <a class="button button-sm secondary" href="%s" target="_blank" style="margin-left:6px; padding:4px 9px; font-size:12px;">JSON API</a>
    </div>
  </div>
  <!-- Date Picker Bar -->
  <div class="date-picker-bar">
    <span style="font-size:13px; font-weight:600; color:#1e293b; display:flex; align-items:center; gap:6px;">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="18" height="18" x="3" y="4" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
      Tarih / Aralık Seç:
    </span>
    <div style="display:flex; align-items:center; gap:6px;">
      <label style="font-size:12px; color:#64748b; font-weight:500;">Başlangıç:</label>
      <input type="date" id="timelineFromDate" class="date-input" value="%s">
    </div>
    <div style="display:flex; align-items:center; gap:6px;">
      <label style="font-size:12px; color:#64748b; font-weight:500;">Bitiş:</label>
      <input type="date" id="timelineToDate" class="date-input" value="%s">
    </div>
    <button type="button" class="button button-sm" onclick="applyTimelineDateFilter()">Aralığı Getir</button>
    <button type="button" class="button button-sm secondary" onclick="goToSpecificDate()">O Güne Git</button>
    <button type="button" class="button button-sm secondary" onclick="filterTimelineToday()">Bugün</button>
    <button type="button" class="filter-clear-btn" id="timelineDateClearBtn" onclick="resetTimelineDateFilter()" style="%s">Tarihi Sıfırla</button>
    %s
  </div>
  <div class="filter-bar">
    <div class="filter-controls">
      <div class="filter-input-wrapper">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
        <input type="text" id="timelineSearch" class="filter-input" placeholder="Aktivite veya tarih ara..." oninput="filterTimeline()">
      </div>
      <div class="filter-chips" id="timelineSourceChips">
        <button type="button" class="filter-chip active" data-source="all" onclick="setTimelineFilter('all', this)">Tümü</button>
        <button type="button" class="filter-chip" data-source="fatsecret" onclick="setTimelineFilter('fatsecret', this)">🥗 Yemekler</button>
        <button type="button" class="filter-chip" data-source="goodreads" onclick="setTimelineFilter('goodreads', this)">📚 Kitaplar</button>
        <button type="button" class="filter-chip" data-source="letterboxd" onclick="setTimelineFilter('letterboxd', this)">🎬 Filmler</button>
        <button type="button" class="filter-chip" data-source="notification" onclick="setTimelineFilter('notification', this)">🔔 Bildirimler</button>
      </div>
      <button type="button" id="timelineClearBtn" class="filter-clear-btn" onclick="resetTimelineFilter()">Temizle</button>
    </div>
    <div class="filter-count-badge" id="timelineCount">Toplam %d aktivite</div>
  </div>
</div>`, rangeBtn(7, "7 Gün"), rangeBtn(14, "14 Gün"), rangeBtn(30, "30 Gün"), rangeBtn(90, "90 Gün"), rangeBtn(365, "1 Yıl"), apiHref, esc(data.ActivityFromDate), esc(data.ActivityToDate), clearDateStyle, customBadgeHtml, totalCount))

	if len(data.Activity.Groups) == 0 {
		b.WriteString(fmt.Sprintf(`
<div class="card">
  <div class="empty-state">
    <div class="empty-icon">📅</div>
    <h3 style="font-size:16px; margin-bottom:4px; color:#0f172a;">Aktivite Kaydı Bulunamadı</h3>
    <p>Son %d gün içinde kaydedilmiş yemek veya aktivite bulunmuyor. Senkronizasyon yaparak verilerinizi güncelleyebilirsiniz.</p>
  </div>
</div>`, days))
		return b.String()
	}

	b.WriteString(`
<div id="timelineEmptySearch" class="card" style="display:none; margin-bottom:20px;">
  <div class="empty-state">
    <div class="empty-icon">🔍</div>
    <h3 style="font-size:16px; margin-bottom:4px; color:#0f172a;">Arama Sonucu Bulunamadı</h3>
    <p>Seçtiğiniz filtrelere veya arama terimine uygun aktivite kaydı bulunmuyor.</p>
  </div>
</div>`)

	for _, group := range data.Activity.Groups {
		b.WriteString(`<div class="timeline-day">`)
		summaryText := fmt.Sprintf("%d aktivite", group.ItemCount)
		if c, ok := group.Summary["total_calories"].(float64); ok && c > 0 {
			summaryText += fmt.Sprintf(" · 🔥 %.0f kcal", c)
		}
		if p, ok := group.Summary["total_protein"].(float64); ok && p > 0 {
			summaryText += fmt.Sprintf(" · P: %.0fg", p)
		}
		if carb, ok := group.Summary["total_carbs"].(float64); ok && carb > 0 {
			summaryText += fmt.Sprintf(" · K: %.0fg", carb)
		}
		if f, ok := group.Summary["total_fat"].(float64); ok && f > 0 {
			summaryText += fmt.Sprintf(" · Y: %.0fg", f)
		}

		b.WriteString(fmt.Sprintf(`
<div class="timeline-day-header">
  <span class="timeline-day-title">%s (%s)</span>
  <span class="timeline-day-summary">%s</span>
</div>
<div class="timeline-items">`, esc(group.Label), esc(group.Date), summaryText))

		for _, item := range group.Items {
			b.WriteString(renderTimelineItemRow(item))
		}
		b.WriteString(`</div></div>`)
	}

	return b.String()
}

func renderFatSecretTab(data pageData) string {
	var b strings.Builder
	stats := data.Overview.FatSecret

	// Stat Grid
	b.WriteString(`
<div class="metrics-grid">
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Bugün</span><span class="badge badge-green">Kalori</span></div>
    <div class="metric-value">` + ratingZero(stats.TodayCalories) + ` kcal</div>
    <div class="metric-note">` + fmt.Sprintf("%d yemek kaydı", stats.TodayEntries) + `</div>
  </div>
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Toplam Kalori</span><span class="badge badge-slate">Kayıt</span></div>
    <div class="metric-value">` + rating(stats.TotalCalories) + `</div>
    <div class="metric-note">` + fmt.Sprintf("%d gün boyunca", stats.LoggedDays) + `</div>
  </div>
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Gün Ortalaması</span><span class="badge badge-amber">Ortalama</span></div>
    <div class="metric-value">` + rating(stats.AverageCalories) + ` kcal</div>
    <div class="metric-note">Günlük ortalama tüketim</div>
  </div>
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Yemek Kaydı</span><span class="badge badge-blue">Girdi</span></div>
    <div class="metric-value">` + number(stats.Entries) + `</div>
    <div class="metric-note">Son kayıt: ` + emptyDash(stats.LastLoggedDate) + `</div>
  </div>
</div>`)

	// Daily Calorie Budget card
	b.WriteString(renderCalorieBudgetCard(stats, data.CalorieTarget))

	// Macro Breakdown card
	b.WriteString(renderMacroBreakdownCard(stats))

	b.WriteString(`<div style="margin-top:20px;" class="stack-col">`)

	// Daily calorie summary
	b.WriteString(`
<div class="card">
  <div class="card-header">
    <div>
      <h2 class="card-title">Günlük Kalori Özeti (Son 14 Gün)</h2>
      <p class="card-desc">Tarih bazında kaydedilen toplam kalori ve öğün sayıları</p>
    </div>
    <div style="display:flex; gap:8px; flex-wrap:wrap; align-items:center;">
      <a class="button button-sm secondary" href="/fatsecret/export" target="_blank">JSON Dışa Aktar</a>
      <form method="post" action="/fatsecret/sync">
        <input type="hidden" name="days" value="30">
        <button class="button button-sm">Sync Et</button>
      </form>
    </div>
  </div>
  <div class="table-container">` + renderFoodSummaryTable(data.FoodSummary) + `</div>
</div>`)

	// Recent Food entries
	b.WriteString(fmt.Sprintf(`
<div class="card">
  <div class="card-header">
    <div>
      <h2 class="card-title">Son Öğün Kayıtları</h2>
      <p class="card-desc">FatSecret üzerinden eşitlenen detaylı besin kayıtları</p>
    </div>
    <div style="display:flex; gap:8px;">
      <a class="button button-sm secondary" href="/api/fatsecret/food-entries?limit=500" target="_blank">JSON API</a>
    </div>
  </div>
  <div class="filter-bar">
    <div class="filter-controls">
      <div class="filter-input-wrapper">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
        <input type="text" id="foodSearch" class="filter-input" placeholder="Yemek veya porsiyon ara (örn. Yulaf, Yumurta)..." oninput="filterFoodEntries()">
      </div>
      <div style="display:flex; align-items:center; gap:5px;">
        <span style="font-size:12px; color:#64748b; font-weight:500;">📅 Gün:</span>
        <input type="date" id="foodDateFilter" class="date-input" style="padding:3px 6px; font-size:12px; height:32px;" onchange="filterFoodEntries()">
      </div>
      <div class="filter-chips" id="foodMealChips">
        <button type="button" class="filter-chip active" data-meal="all" onclick="setFoodMealFilter('all', this)">Tüm Öğünler</button>
        <button type="button" class="filter-chip" data-meal="Breakfast" onclick="setFoodMealFilter('Breakfast', this)">🍳 Kahvaltı</button>
        <button type="button" class="filter-chip" data-meal="Lunch" onclick="setFoodMealFilter('Lunch', this)">🍲 Öğle</button>
        <button type="button" class="filter-chip" data-meal="Dinner" onclick="setFoodMealFilter('Dinner', this)">🍽️ Akşam</button>
        <button type="button" class="filter-chip" data-meal="Other" onclick="setFoodMealFilter('Other', this)">🥪 Diğer</button>
        <button type="button" class="filter-chip" id="foodProteinChip" onclick="toggleFoodProtein(this)">💪 Yüksek Protein (&gt;20g)</button>
      </div>
      <button type="button" id="foodClearBtn" class="filter-clear-btn" onclick="resetFoodFilter()">Temizle</button>
    </div>
    <div class="filter-count-badge" id="foodCount">Gösterilen: %d kayıt</div>
  </div>
  <div class="table-container">` + renderFoodEntriesTable(data.FoodEntries) + `</div>
  <div id="foodEmptySearch" class="empty-state" style="display:none; padding:32px;">
    <div class="empty-icon">🔍</div>
    Kriterlere uygun yemek kaydı bulunamadı.
  </div>
</div>`, len(data.FoodEntries)))

	b.WriteString(`</div>`)
	return b.String()
}

func renderFoodSummaryTable(rows []sqlite.DailySummary) string {
	if len(rows) == 0 {
		return `<div class="empty-state">Henüz kayıtlı FatSecret günlük kalori verisi yok.</div>`
	}
	var b strings.Builder
	b.WriteString(`<table id="foodSummaryTable"><thead><tr>
  <th class="sortable" onclick="sortTable(this, 'date', 'date', 'desc')" title="Tarihe göre sırala">Tarih <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'entries', 'num', 'desc')" title="Öğün sayısına göre sırala">Öğün Sayısı <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'calories', 'num', 'desc')" title="Kaloriye göre sırala (En yüksek kalori üste)">Toplam Kalori <span class="sort-icon">↕</span></th>
  <th>Seviye Göstergesi</th>
</tr></thead><tbody>`)
	for _, r := range rows {
		barWidth := (r.Calories / 3000.0) * 100.0
		if barWidth > 100 {
			barWidth = 100
		}
		b.WriteString(fmt.Sprintf(`
<tr class="summary-row" data-date="%s" data-entries="%d" data-calories="%.2f">
  <td><strong>%s</strong></td>
  <td><span class="badge badge-slate">%d öğün</span></td>
  <td><strong style="color:#0f172a;">%.0f kcal</strong></td>
  <td style="min-width: 140px;">
    <div class="progress-bar-wrap" style="margin:0; height:6px;">
      <div class="progress-bar-segment" style="width: %.1f%%; background: #059669;"></div>
    </div>
  </td>
</tr>`, esc(r.Date), r.EntryCount, r.Calories, esc(r.Date), r.EntryCount, r.Calories, barWidth))
	}
	b.WriteString(`</tbody></table>`)
	return b.String()
}

func renderFoodEntriesTable(rows []sqlite.FoodEntry) string {
	if len(rows) == 0 {
		return `<div class="empty-state">Henüz kaydedilmiş öğün bulunmuyor.</div>`
	}
	var b strings.Builder
	b.WriteString(`<table id="foodEntriesTable"><thead><tr>
  <th class="sortable" onclick="sortTable(this, 'date', 'date', 'desc')" title="Tarihe göre sırala">Tarih <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'meal', 'str', 'asc')" title="Öğüne göre sırala">Öğün <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'name', 'str', 'asc')" title="Yemek adına göre sırala">Besin & Porsiyon Açıklaması <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'calories', 'num', 'desc')" title="Kaloriye göre sırala (En yüksek kalori üste)">Kalori <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'carb', 'num', 'desc')" title="Karbonhidrata göre sırala">Karb <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'protein', 'num', 'desc')" title="Proteine göre sırala (En yüksek protein üste)">Protein <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'fat', 'num', 'desc')" title="Yağa göre sırala">Yağ <span class="sort-icon">↕</span></th>
</tr></thead><tbody>`)
	for _, r := range rows {
		searchContent := strings.ToLower(fmt.Sprintf("%s %s %s %s", r.FoodEntryName, r.FoodEntryDescription, r.Date, r.Meal))
		b.WriteString(fmt.Sprintf(`
<tr class="food-row" data-meal="%s" data-protein="%.2f" data-calories="%.2f" data-carb="%.2f" data-fat="%.2f" data-date="%s" data-name="%s" data-search="%s">
  <td>%s</td>
  <td>%s</td>
  <td>
    <div style="font-weight:600; color:#0f172a;">%s</div>
    <div style="font-size:12px; color:#64748b; margin-top:1px;">%s</div>
  </td>
  <td><strong>%s kcal</strong></td>
  <td><span class="badge badge-blue">%s g</span></td>
  <td><span class="badge badge-green">%s g</span></td>
  <td><span class="badge badge-amber">%s g</span></td>
</tr>`,
			esc(r.Meal),
			r.Protein,
			r.Calories,
			r.Carbohydrate,
			r.Fat,
			esc(r.Date),
			esc(r.FoodEntryName),
			esc(searchContent),
			esc(r.Date),
			formatMealBadge(r.Meal),
			esc(r.FoodEntryName),
			esc(r.FoodEntryDescription),
			rating(r.Calories),
			rating(r.Carbohydrate),
			rating(r.Protein),
			rating(r.Fat),
		))
	}
	b.WriteString(`</tbody></table>`)
	return b.String()
}

func renderGoodreadsTab(data pageData) string {
	var b strings.Builder
	stats := data.Overview.Goodreads

	// Stat Grid
	b.WriteString(`
<div class="metrics-grid">
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Toplam Kitap</span><span class="badge badge-blue">Kütüphane</span></div>
    <div class="metric-value">` + number(stats.Total) + `</div>
    <div class="metric-note">Kütüphanedeki tüm kitaplar</div>
  </div>
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Okundu</span><span class="badge badge-green">Read</span></div>
    <div class="metric-value">` + number(stats.Read) + `</div>
    <div class="metric-note">Tamamlanan kitaplar</div>
  </div>
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Okunacak</span><span class="badge badge-amber">To-Read</span></div>
    <div class="metric-value">` + number(stats.ToRead) + `</div>
    <div class="metric-note">Okuma listesindeki kitaplar</div>
  </div>
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Ortalama Puan</span><span class="badge badge-purple">Puan</span></div>
    <div class="metric-value">★ ` + rating(stats.AverageMyRating) + `</div>
    <div class="metric-note">` + fmt.Sprintf("%d puanlanan kitap", stats.Rated) + `</div>
  </div>
</div>`)

	// Filter and search card
	b.WriteString(fmt.Sprintf(`
<div class="card" style="margin-bottom:20px;">
  <div class="card-header">
    <div>
      <h2 class="card-title">Kütüphane Kitap Listesi</h2>
      <p class="card-desc">Kitap adına, yazara, rafa veya puana göre filtreleyin</p>
    </div>
    <div style="display:flex; gap:8px;">
      <a class="button button-sm secondary" href="/goodreads/export" target="_blank">JSON Dışa Aktar</a>
      <form method="post" action="/goodreads/sync">
        <button class="button button-sm">RSS Şimdi Sync Et</button>
      </form>
    </div>
  </div>
  <div class="filter-bar">
    <div class="filter-controls">
      <div class="filter-input-wrapper">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
        <input type="text" id="bookSearch" class="filter-input" placeholder="Kitap veya yazar ara..." oninput="filterBooks()">
      </div>
      <div class="filter-chips" id="bookShelfChips">
        <button type="button" class="filter-chip active" data-shelf="all" onclick="setBookShelfFilter('all', this)">Tüm Raflar</button>
        <button type="button" class="filter-chip" data-shelf="read" onclick="setBookShelfFilter('read', this)">🟢 Okundu</button>
        <button type="button" class="filter-chip" data-shelf="to-read" onclick="setBookShelfFilter('to-read', this)">🟡 Okunacak</button>
        <button type="button" class="filter-chip" data-shelf="currently-reading" onclick="setBookShelfFilter('currently-reading', this)">🔵 Okunuyor</button>
      </div>
      <div class="filter-chips" id="bookRatingChips">
        <button type="button" class="filter-chip active" data-rating="all" onclick="setBookRatingFilter('all', this)">Tüm Puanlar</button>
        <button type="button" class="filter-chip" data-rating="5" onclick="setBookRatingFilter('5', this)">★ 5</button>
        <button type="button" class="filter-chip" data-rating="4" onclick="setBookRatingFilter('4', this)">★ 4+</button>
      </div>
      <button type="button" id="bookClearBtn" class="filter-clear-btn" onclick="resetBookFilter()">Temizle</button>
    </div>
    <div class="filter-count-badge" id="bookCount">Gösterilen: %d kitap</div>
  </div>
  <div class="table-container">` + renderBooksTable(data.Books) + `</div>
  <div id="bookEmptySearch" class="empty-state" style="display:none; padding:32px;">
    <div class="empty-icon">🔍</div>
    Kriterlere uygun kitap bulunamadı.
  </div>
</div>`, len(data.Books)))

	return b.String()
}

func renderBooksTable(rows []sqlite.GoodreadsBook) string {
	if len(rows) == 0 {
		return `<div class="empty-state"><div class="empty-icon">📖</div>Aranan kriterlere uygun kitap bulunamadı.</div>`
	}
	var b strings.Builder
	b.WriteString(`<table id="booksTable"><thead><tr>
  <th class="sortable" onclick="sortTable(this, 'title', 'str', 'asc')" title="Kitap adına göre sırala">Kitap <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'author', 'str', 'asc')" title="Yazara göre sırala">Yazar <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'shelf', 'str', 'asc')" title="Rafa göre sırala">Raf <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'rating', 'num', 'desc')" title="Puana göre sırala (En yüksek puan üste)">Puanım <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'date', 'date', 'desc')" title="Okuma tarihine göre sırala">Okuma Tarihi <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'year', 'num', 'desc')" title="Yayın yılına göre sırala">Yıl <span class="sort-icon">↕</span></th>
</tr></thead><tbody>`)
	for _, r := range rows {
		shelfBadge := fmt.Sprintf(`<span class="badge badge-slate">%s</span>`, esc(r.ExclusiveShelf))
		if r.ExclusiveShelf == "read" {
			shelfBadge = `<span class="badge badge-green">Okundu</span>`
		} else if r.ExclusiveShelf == "to-read" {
			shelfBadge = `<span class="badge badge-amber">Okunacak</span>`
		} else if r.ExclusiveShelf == "currently-reading" {
			shelfBadge = `<span class="badge badge-blue">Okunuyor</span>`
		}

		thumbHtml := `<div class="media-thumb-placeholder">📖</div>`
		if r.ImageURL != "" {
			thumbHtml = fmt.Sprintf(`<img src="%s" class="media-thumb" alt="%s" loading="lazy" onerror="this.outerHTML='<div class=\'media-thumb-placeholder\'>📖</div>'">`, esc(r.ImageURL), esc(r.Title))
		}

		searchContent := strings.ToLower(fmt.Sprintf("%s %s %s", r.Title, r.Author, r.DateRead))
		b.WriteString(fmt.Sprintf(`
<tr class="book-row" data-shelf="%s" data-rating="%d" data-title="%s" data-author="%s" data-date="%s" data-year="%d" data-search="%s">
  <td>
    <div class="media-cell">
      %s
      <div class="media-cell-info">
        <strong style="color:#0f172a;">%s</strong>
      </div>
    </div>
  </td>
  <td>%s</td>
  <td>%s</td>
  <td>%s</td>
  <td>%s</td>
  <td>%s</td>
</tr>`,
			esc(r.ExclusiveShelf),
			r.MyRating,
			esc(r.Title),
			esc(r.Author),
			esc(r.DateRead),
			r.YearPublished,
			esc(searchContent),
			thumbHtml,
			esc(r.Title),
			esc(r.Author),
			shelfBadge,
			renderStars(float64(r.MyRating)),
			emptyDash(esc(r.DateRead)),
			emptyDash(number(r.YearPublished)),
		))
	}
	b.WriteString(`</tbody></table>`)
	return b.String()
}

func renderLetterboxdTab(data pageData) string {
	var b strings.Builder
	stats := data.Overview.Letterboxd

	yearRange := ""
	if stats.OldestYear > 0 || stats.NewestYear > 0 {
		yearRange = fmt.Sprintf("%s-%s", number(stats.OldestYear), number(stats.NewestYear))
	}

	// Stat Grid
	b.WriteString(`
<div class="metrics-grid">
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Toplam Film</span><span class="badge badge-purple">Sinema</span></div>
    <div class="metric-value">` + number(stats.Total) + `</div>
    <div class="metric-note">Kayıtlı tüm filmler</div>
  </div>
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Puanlı Film</span><span class="badge badge-amber">Değerlendirme</span></div>
    <div class="metric-value">` + number(stats.Rated) + `</div>
    <div class="metric-note">Puanlanan film sayısı</div>
  </div>
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Ortalama Puan</span><span class="badge badge-green">Ortalama</span></div>
    <div class="metric-value">★ ` + rating(stats.AverageRating) + `</div>
    <div class="metric-note">5 üzerinden ortalama puan</div>
  </div>
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Film Yılları</span><span class="badge badge-slate">Dönem</span></div>
    <div class="metric-value">` + emptyDash(yearRange) + `</div>
    <div class="metric-note">En eski ve en yeni film yılları</div>
  </div>
</div>`)

	// Film Table card
	// Film Table card
	b.WriteString(fmt.Sprintf(`
<div class="card">
  <div class="card-header">
    <div>
      <h2 class="card-title">İzlenen Filmler</h2>
      <p class="card-desc">Letterboxd üzerinden eşitlenen film kayıtları</p>
    </div>
    <div style="display:flex; gap:8px;">
      <a class="button button-sm secondary" href="/letterboxd/export" target="_blank">JSON Dışa Aktar</a>
      <a class="button button-sm secondary" href="/api/letterboxd/films?limit=250" target="_blank">JSON API</a>
      <form method="post" action="/letterboxd/sync">
        <button class="button button-sm">RSS Şimdi Sync Et</button>
      </form>
    </div>
  </div>
  <div class="filter-bar">
    <div class="filter-controls">
      <div class="filter-input-wrapper">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
        <input type="text" id="filmSearch" class="filter-input" placeholder="Film adı veya yıl ara..." oninput="filterFilms()">
      </div>
      <div class="filter-chips" id="filmRatingChips">
        <button type="button" class="filter-chip active" data-rating="all" onclick="setFilmRatingFilter('all', this)">Tüm Puanlar</button>
        <button type="button" class="filter-chip" data-rating="5" onclick="setFilmRatingFilter('5', this)">★ 5 Yıldız</button>
        <button type="button" class="filter-chip" data-rating="4" onclick="setFilmRatingFilter('4', this)">★ 4+ Yıldız</button>
        <button type="button" class="filter-chip" data-rating="3" onclick="setFilmRatingFilter('3', this)">★ 3+ Yıldız</button>
        <button type="button" class="filter-chip" data-rating="unrated" onclick="setFilmRatingFilter('unrated', this)">Puansız</button>
        <button type="button" class="filter-chip" id="filmRewatchChip" onclick="toggleFilmRewatch(this)">🔄 Tekrar İzlenenler</button>
      </div>
      <button type="button" id="filmClearBtn" class="filter-clear-btn" onclick="resetFilmFilter()">Temizle</button>
    </div>
    <div class="filter-count-badge" id="filmCount">Gösterilen: %d film</div>
  </div>
  <div class="table-container">` + renderFilmsTable(data.LetterboxdFilms) + `</div>
  <div id="filmEmptySearch" class="empty-state" style="display:none; padding:32px;">
    <div class="empty-icon">🔍</div>
    Kriterlere uygun film bulunamadı.
  </div>
</div>`, len(data.LetterboxdFilms)))

	return b.String()
}

func renderFilmsTable(rows []sqlite.LetterboxdFilm) string {
	if len(rows) == 0 {
		return `
<div class="empty-state">
  <div class="empty-icon">🎬</div>
  <h3 style="font-size:16px; margin-bottom:4px; color:#0f172a;">Henüz Film Kaydı Yok</h3>
  <p>Letterboxd profilinizin RSS feed bağlantısını <a href="#settings" onclick="switchTab('settings')" style="color:#2563eb; font-weight:600;">Ayarlar sekmesinden</a> girerek veya CSV içe aktararak filmlerinizi ekleyebilirsiniz.</p>
</div>`
	}
	var b strings.Builder
	b.WriteString(`<table id="filmsTable"><thead><tr>
  <th class="sortable" onclick="sortTable(this, 'name', 'str', 'asc')" title="Film adına göre sırala">Film <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'year', 'num', 'desc')" title="Yıla göre sırala">Yıl <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'rating', 'num', 'desc')" title="Puana göre sırala (En yüksek puan üste)">Puanım <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'date', 'date', 'desc')" title="İzleme tarihine göre sırala">İzleme Tarihi <span class="sort-icon">↕</span></th>
  <th>Kaynak</th>
</tr></thead><tbody>`)
	for _, r := range rows {
		isRewatch := strings.EqualFold(r.Rewatch, "yes") || strings.EqualFold(r.Rewatch, "true") || r.Rewatch == "1"
		rewatchAttr := "false"
		rewatchBadge := ""
		if isRewatch {
			rewatchAttr = "true"
			rewatchBadge = `<span class="badge badge-purple" style="margin-left:6px; font-size:11px;">🔄 Tekrar</span>`
		}

		thumbHtml := `<div class="media-thumb-placeholder">🎬</div>`
		if r.ImageURL != "" {
			thumbHtml = fmt.Sprintf(`<img src="%s" class="media-thumb" alt="%s" loading="lazy" onerror="this.outerHTML='<div class=\'media-thumb-placeholder\'>🎬</div>'">`, esc(r.ImageURL), esc(r.Name))
		}

		reviewSnippet := ""
		if r.Review != "" {
			cleanReview := strings.TrimSpace(r.Review)
			if len(cleanReview) > 80 {
				cleanReview = cleanReview[:80] + "..."
			}
			reviewSnippet = fmt.Sprintf(`<div style="font-size:12px; color:#64748b; margin-top:3px; font-style:italic;">"%s"</div>`, esc(cleanReview))
		}

		searchContent := strings.ToLower(fmt.Sprintf("%s %d %s", r.Name, r.Year, r.WatchedDate))
		b.WriteString(fmt.Sprintf(`
<tr class="film-row" data-rating="%.1f" data-name="%s" data-year="%d" data-date="%s" data-rewatch="%s" data-search="%s">
  <td>
    <div class="media-cell">
      %s
      <div class="media-cell-info">
        <div style="display:flex; align-items:center;">
          <strong style="color:#0f172a;">%s</strong>
          %s
        </div>
        %s
      </div>
    </div>
  </td>
  <td><span class="badge badge-slate">%d</span></td>
  <td>%s</td>
  <td>%s</td>
  <td><span class="badge badge-purple">%s</span></td>
</tr>`,
			r.Rating,
			esc(r.Name),
			r.Year,
			esc(r.WatchedDate),
			rewatchAttr,
			esc(searchContent),
			thumbHtml,
			esc(r.Name),
			rewatchBadge,
			reviewSnippet,
			r.Year,
			renderStars(r.Rating),
			emptyDash(esc(r.WatchedDate)),
			esc(r.Source),
		))
	}
	b.WriteString(`</tbody></table>`)
	return b.String()
}

func renderSyncRunsTable(runs []sqlite.SyncRun) string {
	if len(runs) == 0 {
		return `<div class="empty-state" style="padding:24px;"><div class="empty-icon">📋</div>Henüz senkronizasyon log kaydı bulunmuyor.</div>`
	}
	var b strings.Builder
	b.WriteString(`<table id="syncRunsTable"><thead><tr>
  <th class="sortable" onclick="sortTable(this, 'source', 'str', 'asc')" title="Kaynağa göre sırala">Kaynak <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'status', 'str', 'asc')" title="Duruma göre sırala">Durum <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'inserted', 'num', 'desc')" title="Eklenen kayıt sayısına göre sırala">Eklenen <span class="sort-icon">↕</span></th>
  <th class="sortable" onclick="sortTable(this, 'updated', 'num', 'desc')" title="Güncellenen kayıt sayısına göre sırala">Güncellenen <span class="sort-icon">↕</span></th>
  <th>Mesaj / Not</th>
  <th class="sortable" onclick="sortTable(this, 'date', 'date', 'desc')" title="Tarihe göre sırala">Tarih <span class="sort-icon">↕</span></th>
</tr></thead><tbody>`)
	for _, r := range runs {
		statusBadge := `<span class="badge badge-green">Başarılı</span>`
		if r.Status == "failed" {
			statusBadge = `<span class="badge badge-red">Hata</span>`
		}
		serviceBadge := fmt.Sprintf(`<span class="badge badge-slate">%s</span>`, esc(r.Source))
		if strings.HasPrefix(r.Source, "FatSecret") {
			serviceBadge = fmt.Sprintf(`<span class="badge badge-green">%s</span>`, esc(r.Source))
		} else if strings.HasPrefix(r.Source, "Goodreads") {
			serviceBadge = fmt.Sprintf(`<span class="badge badge-blue">%s</span>`, esc(r.Source))
		} else if strings.HasPrefix(r.Source, "Letterboxd") {
			serviceBadge = fmt.Sprintf(`<span class="badge badge-purple">%s</span>`, esc(r.Source))
		}

		msgText := "-"
		if r.Message != "" {
			color := "#475569"
			if r.Status == "failed" {
				color = "#ef4444"
			}
			msgText = fmt.Sprintf(`<span style="color:%s; font-size:12px;">%s</span>`, color, esc(r.Message))
		}

		b.WriteString(fmt.Sprintf(`
<tr class="sync-row" data-source="%s" data-status="%s" data-inserted="%d" data-updated="%d" data-date="%s">
  <td>%s</td>
  <td>%s</td>
  <td><strong style="color:#0f172a;">%d</strong></td>
  <td><strong style="color:#0f172a;">%d</strong></td>
  <td>%s</td>
  <td><span style="font-size:12px; color:#64748b;">%s</span></td>
</tr>`,
			esc(r.Source),
			esc(r.Status),
			r.Inserted,
			r.Updated,
			esc(r.RanAt),
			serviceBadge,
			statusBadge,
			r.Inserted,
			r.Updated,
			msgText,
			esc(r.RanAt),
		))
	}
	b.WriteString(`</tbody></table>`)
	return b.String()
}

func renderNotificationsTab(data pageData) string {
	var b strings.Builder

	// Calculate counts
	appCounts := make(map[string]int)
	ongoingCount := 0
	for _, n := range data.Notifications {
		appName := n.AppName
		if appName == "" {
			appName = n.PackageName
		}
		if appName != "" {
			appCounts[appName]++
		}
		if n.IsOngoing {
			ongoingCount++
		}
	}

	// 1. Metric Cards
	b.WriteString(`
<div class="metrics-grid">
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Toplam Bildirim</span><span class="badge badge-purple">Arşiv</span></div>
    <div class="metric-value">` + number(len(data.Notifications)) + `</div>
    <div class="metric-note">Veritabanında kayıtlı tüm bildirimler</div>
  </div>
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Farklı Uygulama</span><span class="badge badge-blue">Kaynaklar</span></div>
    <div class="metric-value">` + number(len(appCounts)) + `</div>
    <div class="metric-note">Bildirim gönderen uygulama adedi</div>
  </div>
  <div class="metric-card">
    <div class="metric-header"><span class="metric-label">Devam Eden / Aktif</span><span class="badge badge-amber">Durum</span></div>
    <div class="metric-value">` + number(ongoingCount) + `</div>
    <div class="metric-note">Arka plan / kalıcı sistem bildirimleri</div>
  </div>
</div>`)

	// 2. Webhook Relay Integration Card
	b.WriteString(`
<div class="card">
  <div class="card-header">
    <div>
      <h2 class="card-title">🔔 Android Bildirim İletimi (Notification Relay)</h2>
      <p class="card-desc">Android cihazınızdaki bildirimleri HTTP Webhook üzerinden yerel SQLite veritabanına aktarın.</p>
    </div>
    <div style="display:flex; gap:8px;">
      <a class="button button-sm secondary" href="/api/notifications?limit=250" target="_blank">JSON API</a>
      <button type="button" class="button button-sm" onclick="copyNotifWebhookUrl()">📋 Webhook URL Kopyala</button>
    </div>
  </div>
  <div class="webhook-box" style="margin-bottom:12px;">
    <span id="notifWebhookEndpoint" style="font-family:ui-monospace,SFMono-Regular,Menlo,monospace; font-size:13px; color:hsl(var(--foreground)) !important; font-weight:600;">/api/webhooks/notification</span>
    <span id="notifWebhookFeedback" style="font-size:12px; color:#10b981; font-weight:600; margin-left:12px; display:none;">✓ URL Panoya Kopyalandı!</span>
  </div>
  <div style="font-size:12.5px; color:var(--text-muted); line-height:1.6;">
    <strong>Kullanım Rehberi:</strong> Android cihazınıza <code>Notification Relay</code> veya <code>HTTP Shortcuts</code> uygulamasını kurun.
    Uç nokta olarak yukarıdaki URL'i (veya yerel IP adresinizi, örn: <code>http://192.168.1.X:8080/api/webhooks/notification</code>) tanımlayın ve <code>POST</code> metoduyla JSON iletin.
    <br>
    <strong>Desteklenen JSON parametreleri:</strong> <code>packageName</code>, <code>appName</code>, <code>title</code>, <code>text</code>, <code>subText</code>, <code>bigText</code>, <code>postTime</code>, <code>ongoing</code>.
  </div>
</div>`)

	// 3. Search and Feed Card
	type appEntry struct {
		Name  string
		Count int
	}
	var sortedApps []appEntry
	for name, count := range appCounts {
		sortedApps = append(sortedApps, appEntry{Name: name, Count: count})
	}
	for i := 0; i < len(sortedApps)-1; i++ {
		for j := i + 1; j < len(sortedApps); j++ {
			if sortedApps[j].Count > sortedApps[i].Count {
				sortedApps[i], sortedApps[j] = sortedApps[j], sortedApps[i]
			}
		}
	}

	var appOptions strings.Builder
	appOptions.WriteString(fmt.Sprintf(`<option value="all">Tüm Uygulamalar (%d)</option>`, len(data.Notifications)))
	for _, app := range sortedApps {
		appOptions.WriteString(fmt.Sprintf(`<option value="%s">%s (%d)</option>`, esc(app.Name), esc(app.Name), app.Count))
	}

	b.WriteString(fmt.Sprintf(`
<div class="card">
  <div class="card-header">
    <div>
      <h2 class="card-title">Gelen Bildirim Akışı</h2>
      <p class="card-desc">Cihazınızdan iletilen anlık bildirim geçmişi</p>
    </div>
  </div>
  <div class="filter-bar">
    <div class="filter-controls" style="flex-wrap:wrap; gap:10px;">
      <div class="filter-input-wrapper" style="min-width:240px; flex:1;">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
        <input type="text" id="notifSearch" class="filter-input" placeholder="Başlık, metin veya paket adı ara..." oninput="filterNotifications()">
      </div>
      <select id="notifAppFilter" class="filter-input" style="width:auto; min-width:180px;" onchange="filterNotifications()">
        %s
      </select>
      <label style="display:flex; align-items:center; gap:6px; font-size:13px; font-weight:500; cursor:pointer; white-space:nowrap;">
        <input type="checkbox" id="notifOngoingOnly" onchange="filterNotifications()"> Sadece Devam Eden
      </label>
      <button type="button" class="filter-clear-btn" onclick="resetNotifFilter()">Temizle</button>
    </div>
    <div class="filter-count-badge" id="notifCountDisplay">Toplam %d bildirim</div>
  </div>
  <div id="notifFeedContainer">`, appOptions.String(), len(data.Notifications)))

	if len(data.Notifications) == 0 {
		b.WriteString(`
    <div class="empty-state">
      <div class="empty-icon">🔔</div>
      <h3 style="font-size:16px; margin-bottom:4px; color:var(--text-main);">Henüz Bildirim Kaydı Yok</h3>
      <p>Android cihazınızdan Notification Relay ile bildirim gönderdiğinizde burada listelenecektir.</p>
    </div>`)
	} else {
		for _, n := range data.Notifications {
			appName := n.AppName
			if appName == "" {
				appName = n.PackageName
			}
			if appName == "" {
				appName = "Bilinmeyen Uygulama"
			}

			ongoingBadge := ""
			if n.IsOngoing {
				ongoingBadge = `<span class="badge badge-amber" style="font-size:11px;"><span class="badge-dot"></span>Devam Eden</span>`
			}

			text := n.Text
			if text == "" {
				text = n.BigText
			}

			subHtml := ""
			if n.SubText != "" && n.SubText != text {
				subHtml = fmt.Sprintf(`<div class="notif-feed-item-sub">%s</div>`, esc(n.SubText))
			}

			searchContent := strings.ToLower(fmt.Sprintf("%s %s %s %s %s", n.Title, text, n.SubText, n.PackageName, appName))
			timeStr := formatNotifPostTime(n.PostTime)
			if timeStr == "" {
				timeStr = formatNotifPostTime(n.CreatedAt)
			}

			titleHtml := ""
			if n.Title != "" {
				titleHtml = fmt.Sprintf(`<div class="notif-feed-item-title">%s</div>`, esc(n.Title))
			}

			bodyHtml := ""
			if text != "" {
				bodyHtml = fmt.Sprintf(`<div class="notif-feed-item-body">%s</div>`, esc(text))
			}

			b.WriteString(fmt.Sprintf(`
    <div class="notif-feed-item" data-app="%s" data-ongoing="%t" data-search="%s">
      <div class="notif-feed-item-header">
        <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
          <span class="badge badge-purple"><span class="badge-dot"></span>%s</span>
          %s
          <span class="notif-pkg">%s</span>
        </div>
        <span class="notif-time">🕒 %s</span>
      </div>
      %s
      %s
      %s
    </div>`,
				esc(appName),
				n.IsOngoing,
				esc(searchContent),
				esc(appName),
				ongoingBadge,
				esc(n.PackageName),
				esc(timeStr),
				titleHtml,
				bodyHtml,
				subHtml,
			))
		}
		b.WriteString(`
    <div id="notifEmptySearch" style="display:none; padding:32px 16px; text-align:center; color:var(--text-muted);">
      Arama kriterlerine uygun bildirim bulunamadı.
    </div>`)
	}

	b.WriteString(`
  </div>
</div>`)

	return b.String()
}

func formatNotifPostTime(ts string) string {
	ts = strings.TrimSpace(ts)
	if len(ts) >= 16 {
		return strings.Replace(ts[:16], "T", " ", 1)
	}
	return ts
}

func renderSettingsTab(data pageData) string {
	var b strings.Builder
	b.WriteString(fmt.Sprintf(`
<div class="grid-3col">
  <!-- FatSecret Settings -->
  <div class="card">
    <div class="card-header">
      <div>
        <h2 class="card-title">FatSecret</h2>
        <p class="card-desc">OAuth 1.0 hesabı ve otomatik eşitleme</p>
      </div>
      %s
    </div>
    <div class="card-content">
      <div style="font-size:13px; color:#475569; margin-bottom:14px; line-height:1.45;">
        FatSecret beslenme günlüğünüzdeki veriler her saat arka planda ve anlık butonlarla yerel SQLite veritabanınıza çekilir.
      </div>
      <a class="button secondary" style="width:100%%;" href="/fatsecret/connect">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
        FatSecret Hesabını Yeniden Bağla
      </a>
      <a class="button secondary" href="/fatsecret/export" style="width:100%%; margin-top:8px;">FatSecret JSON İndir</a>

      <div style="border-top:1px solid #f1f5f9; margin-top:16px; padding-top:14px;">
        <div style="font-weight:600; font-size:13px; color:#0f172a; margin-bottom:4px;">Senkronizasyon Aralığı</div>
        <div style="font-size:12px; color:#64748b; margin-bottom:10px; line-height:1.4;">
          💡 <strong>Akıllı Eşitleme:</strong> Veritabanında kayıtlı geçmiş günler otomatik olarak atlanır, FatSecret kotanız korunur.
        </div>
        <div style="display:flex; flex-wrap:wrap; gap:8px;">
          <form method="post" action="/fatsecret/sync">
            <input type="hidden" name="days" value="1">
            <button class="button button-sm secondary">Bugün</button>
          </form>
          <form method="post" action="/fatsecret/sync">
            <input type="hidden" name="days" value="30">
            <button class="button button-sm secondary">Son 30 Gün</button>
          </form>
          <form method="post" action="/fatsecret/sync">
            <input type="hidden" name="days" value="365">
            <button class="button button-sm secondary">Son 1 Yıl</button>
          </form>
          <form method="post" action="/fatsecret/sync">
            <input type="hidden" name="days" value="1095">
            <button class="button button-sm" style="background:#059669; border-color:#059669;" title="Geçmişteki tüm kayıtları SQLite'a kopyalar">⚡ Tüm Zamanlar</button>
          </form>
        </div>
      </div>
    </div>
  </div>

  <!-- Goodreads Settings -->
  <div class="card">
    <div class="card-header">
      <div>
        <h2 class="card-title">Goodreads</h2>
        <p class="card-desc">RSS besleme ve CSV içe aktarımı</p>
      </div>
      %s
    </div>
    <div class="card-content">
      <form method="post" action="/goodreads/settings" class="stack-col" style="gap:10px; margin-bottom:16px;">
        <div class="form-group" style="margin:0;">
          <label class="form-label">RSS Feed URL</label>
          <input type="text" name="feed_url" value="%s" placeholder="https://www.goodreads.com/review/list_rss/...">
        </div>
        <div style="display:flex; gap:10px; align-items:center;">
          <div class="form-group" style="margin:0; flex:1;">
            <label class="form-label">Aralık (dk)</label>
            <input type="number" name="interval_minutes" min="15" value="%d">
          </div>
          <label style="display:flex; align-items:center; gap:6px; font-size:13px; margin-top:20px; cursor:pointer;">
            <input type="checkbox" name="enabled" %s> Otomatik
          </label>
        </div>
        <button class="button">Ayar Kaydet</button>
      </form>

      <div style="border-top:1px solid #f1f5f9; padding-top:14px;">
        <label class="form-label" style="margin-bottom:6px; display:block;">CSV İçe Aktar</label>
        <form method="post" action="/goodreads/import" enctype="multipart/form-data" style="display:flex; flex-direction:column; gap:8px;">
          <input type="file" name="file" accept=".csv,text/csv" required>
          <button class="button secondary">Goodreads CSV Yükle</button>
        </form>
        <a class="button secondary" href="/goodreads/export" style="width:100%%; margin-top:8px;">Goodreads JSON İndir</a>
      </div>
    </div>
  </div>

  <!-- Letterboxd Settings -->
  <div class="card">
    <div class="card-header">
      <div>
        <h2 class="card-title">Letterboxd</h2>
        <p class="card-desc">RSS besleme ve CSV içe aktarımı</p>
      </div>
      %s
    </div>
    <div class="card-content">
      <form method="post" action="/letterboxd/settings" class="stack-col" style="gap:10px; margin-bottom:16px;">
        <div class="form-group" style="margin:0;">
          <label class="form-label">RSS Feed URL</label>
          <input type="text" name="feed_url" value="%s" placeholder="https://letterboxd.com/kullanici/rss/">
        </div>
        <div style="display:flex; gap:10px; align-items:center;">
          <div class="form-group" style="margin:0; flex:1;">
            <label class="form-label">Aralık (dk)</label>
            <input type="number" name="interval_minutes" min="15" value="%d">
          </div>
          <label style="display:flex; align-items:center; gap:6px; font-size:13px; margin-top:20px; cursor:pointer;">
            <input type="checkbox" name="enabled" %s> Otomatik
          </label>
        </div>
        <button class="button">Ayar Kaydet</button>
      </form>

      <div style="border-top:1px solid #f1f5f9; padding-top:14px;">
        <label class="form-label" style="margin-bottom:6px; display:block;">CSV İçe Aktar</label>
        <form method="post" action="/letterboxd/import" enctype="multipart/form-data" style="display:flex; flex-direction:column; gap:8px;">
          <input type="file" name="file" accept=".csv,text/csv" required>
          <button class="button secondary">Letterboxd CSV Yükle</button>
        </form>
        <a class="button secondary" href="/letterboxd/export" style="width:100%%; margin-top:8px;">Letterboxd JSON İndir</a>
      </div>
    </div>
  </div>
</div>`,
		statusBadge("", data.FatSecretConnected && data.OAuthReady),
		statusBadge("", data.GoodreadsSettings.FeedURL != ""),
		esc(data.GoodreadsSettings.FeedURL),
		data.GoodreadsSettings.IntervalMinutes,
		checked(data.GoodreadsSettings.Enabled),
		statusBadge("", data.LetterboxdSettings.FeedURL != ""),
		esc(data.LetterboxdSettings.FeedURL),
		data.LetterboxdSettings.IntervalMinutes,
		checked(data.LetterboxdSettings.Enabled),
	))

	// Backup & Export Hub Card
	b.WriteString(`
<div class="card" style="margin-top:24px;">
  <div class="card-header">
    <div>
      <h2 class="card-title">📦 Veri Egemenliği & Yedekleme Merkezi</h2>
      <p class="card-desc">Tüm kişisel verilerinizi tek tıkla dışa aktarın, JSON veya SQLite veritabanı yedeğinizi indirin.</p>
    </div>
  </div>
  <div class="card-content">
    <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(260px, 1fr)); gap:16px;">
      <div style="border:1px solid #e2e8f0; border-radius:8px; padding:16px; display:flex; flex-direction:column; justify-content:space-between;">
        <div>
          <div style="font-weight:600; font-size:14px; color:#0f172a; margin-bottom:4px;">🌐 Birleşik JSON Yedeği</div>
          <div style="font-size:12.5px; color:#64748b; margin-bottom:12px;">FatSecret, Goodreads ve Letterboxd verilerinin tümünü tek bir yapılandırılmış JSON dosyası olarak indirir.</div>
        </div>
        <a class="button" href="/api/export/all" target="_blank" style="width:100%; justify-content:center; text-align:center;">Tüm Verileri İndir (JSON)</a>
      </div>
      <div style="border:1px solid #e2e8f0; border-radius:8px; padding:16px; display:flex; flex-direction:column; justify-content:space-between;">
        <div>
          <div style="font-weight:600; font-size:14px; color:#0f172a; margin-bottom:4px;">💾 Ham SQLite Veritabanı</div>
          <div style="font-size:12.5px; color:#64748b; margin-bottom:12px;">Sunucudaki güncel <code>personal_records.db</code> dosyasını doğrudan indirerek tam veri bağımsızlığı sağlar.</div>
        </div>
        <a class="button secondary" href="/api/export/db" target="_blank" style="width:100%; justify-content:center; text-align:center;">SQLite Dosyasını İndir (.db)</a>
      </div>
    </div>
  </div>
</div>`)

	// Sync Runs Log Card
	b.WriteString(fmt.Sprintf(`
<div class="card" style="margin-top:24px;">
  <div class="card-header">
    <div>
      <h2 class="card-title">📜 Son Senkronizasyon Çalışma Logları</h2>
      <p class="card-desc">Tüm servislerin gerçekleştirdiği son eşitleme işlemleri, başarı durumları ve kayıt sayıları</p>
    </div>
  </div>
  <div class="table-container">%s</div>
</div>`, renderSyncRunsTable(data.RecentSyncRuns)))

	return b.String()
}

func renderStars(r float64) string {
	if r <= 0 {
		return `<span class="badge badge-slate">-</span>`
	}
	full := int(r)
	half := (r - float64(full)) >= 0.5
	var b strings.Builder
	b.WriteString(`<span class="stars" title="` + fmt.Sprintf("%.1f", r) + `">`)
	for i := 0; i < full && i < 5; i++ {
		b.WriteString(`★`)
	}
	if half && full < 5 {
		b.WriteString(`½`)
		full++
	}
	for i := full; i < 5; i++ {
		b.WriteString(`<span class="star-empty">☆</span>`)
	}
	b.WriteString(fmt.Sprintf(` <span class="rating-num">%.1f</span></span>`, r))
	return b.String()
}

func formatMealBadge(meal string) string {
	m := strings.ToLower(strings.TrimSpace(meal))
	switch m {
	case "breakfast", "kahvalti", "kahvaltı":
		return `<span class="badge badge-blue"><span class="badge-dot"></span>Kahvaltı</span>`
	case "lunch", "ogle", "öğle":
		return `<span class="badge badge-amber"><span class="badge-dot"></span>Öğle</span>`
	case "dinner", "aksam", "akşam":
		return `<span class="badge badge-purple"><span class="badge-dot"></span>Akşam</span>`
	default:
		if meal == "" {
			meal = "Diğer"
		}
		return fmt.Sprintf(`<span class="badge badge-slate"><span class="badge-dot"></span>%s</span>`, esc(meal))
	}
}

func statusBadge(prefix string, ok bool) string {
	label := "Bağlı"
	class := "badge badge-green"
	if !ok {
		label = "Eksik"
		class = "badge badge-amber"
	}
	if prefix != "" {
		label = prefix + ": " + label
	}
	return fmt.Sprintf(`<span class="%s"><span class="badge-dot"></span>%s</span>`, class, esc(label))
}

func syncRunText(value any) string {
	run, ok := value.(*sqlite.SyncRun)
	if !ok || run == nil {
		return "Henüz sync kaydı yok."
	}
	detail := fmt.Sprintf("%s · %s · %d yeni, %d güncel", run.RanAt, run.Status, run.Inserted, run.Updated)
	if run.Message != "" {
		msg := run.Message
		if len(msg) > 100 {
			msg = msg[:100] + "..."
		}
		detail += " · " + msg
	}
	return esc(detail)
}

func renderShelfOptions(shelves []string, selected string) string {
	var b strings.Builder
	b.WriteString(`<option value="">Tüm raflar</option>`)
	for _, shelf := range shelves {
		attr := ""
		if shelf == selected {
			attr = " selected"
		}
		b.WriteString(fmt.Sprintf(`<option value="%s"%s>%s</option>`, esc(shelf), attr, esc(shelf)))
	}
	return b.String()
}

func esc(value string) string {
	return html.EscapeString(value)
}

func checked(value bool) string {
	if value {
		return "checked"
	}
	return ""
}

func number(value int) string {
	if value == 0 {
		return "0"
	}
	return fmt.Sprint(value)
}

func emptyDash(value string) string {
	if strings.TrimSpace(value) == "" {
		return "-"
	}
	return value
}

func rating(value float64) string {
	if value == 0 {
		return "0"
	}
	return ratingZero(value)
}

func ratingZero(value float64) string {
	if value == float64(int(value)) {
		return fmt.Sprintf("%.0f", value)
	}
	return fmt.Sprintf("%.1f", value)
}

func renderSyncModal() string {
	return `
<!-- Modal Component -->
<style>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.65);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 16px;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.2s ease-in-out, visibility 0.2s ease-in-out;
}
.modal-backdrop.show {
  opacity: 1;
  visibility: visible;
}
.modal-card {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.2), 0 10px 10px -5px rgba(0, 0, 0, 0.08);
  border: 1px solid #e2e8f0;
  width: 100%;
  max-width: 440px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transform: scale(0.96);
  transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}
.modal-backdrop.show .modal-card {
  transform: scale(1);
}
.modal-header {
  display: flex;
  align-items: center;
  gap: 14px;
}
.modal-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.modal-icon.loading {
  background: #eff6ff;
  color: #2563eb;
}
.modal-icon.success {
  background: #ecfdf5;
  color: #059669;
}
.modal-icon.error {
  background: #fef2f2;
  color: #dc2626;
}
.modal-icon svg {
  display: block;
}
.modal-spinner {
  animation: modal-spin 0.8s linear infinite;
}
@keyframes modal-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.modal-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}
.modal-subtitle {
  font-size: 13px;
  color: #64748b;
  margin: 3px 0 0;
}
.modal-body {
  font-size: 13.5px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
  color: #334155;
  line-height: 1.45;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.modal-body.error-box {
  background: #fef2f2;
  border-color: #fecaca;
  color: #991b1b;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

<div id="syncModal" class="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="modalTitle">
  <div class="modal-card">
    <div class="modal-header">
      <div id="modalIcon" class="modal-icon loading">
        <svg class="modal-spinner" viewBox="0 0 24 24" width="24" height="24">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="32" stroke-linecap="round"/>
        </svg>
      </div>
      <div>
        <h3 id="modalTitle" class="modal-title">Senkronize Ediliyor...</h3>
        <p id="modalSubtitle" class="modal-subtitle">Lütfen bekleyin, veriler çekiliyor.</p>
      </div>
    </div>
    <div id="modalBody" class="modal-body" style="display:none;"></div>
    <div class="modal-actions">
      <button id="modalCloseBtn" type="button" class="button" style="display:none;">Tamam (Sayfayı Yenile)</button>
    </div>
  </div>
</div>

<!-- Calorie Target Edit Modal -->
<div id="calorieModal" class="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="calorieModalTitle">
  <div class="modal-card">
    <div class="modal-header">
      <div class="modal-icon" style="background:#eff6ff; color:#2563eb;">
        <span style="font-size:22px;">🎯</span>
      </div>
      <div>
        <h3 id="calorieModalTitle" class="modal-title">Günlük Kalori Hedefi</h3>
        <p class="modal-subtitle">Tüketmek istediğiniz günlük hedef kalori miktarını belirleyin.</p>
      </div>
    </div>
    <form id="calorieTargetForm" method="post" action="/settings/calorie-target">
      <div style="margin: 12px 0 16px;">
        <label class="form-label" style="margin-bottom:6px; display:block; font-weight:500;">Hedef Kalori (kcal)</label>
        <input type="number" id="calorieTargetInput" name="target" min="500" max="10000" step="50" style="width:100%; box-sizing:border-box; padding:10px 14px; font-size:16px; font-weight:600; border:1px solid #cbd5e1; border-radius:8px; outline:none;" required>
      </div>
      <div class="modal-actions">
        <button type="button" class="button secondary" onclick="closeCalorieModal()">İptal</button>
        <button type="submit" class="button">Kaydet</button>
      </div>
    </form>
  </div>
</div>

<script>
function openCalorieModal(target) {
  var modal = document.getElementById('calorieModal');
  var input = document.getElementById('calorieTargetInput');
  if (input) input.value = target || 2200;
  if (modal) modal.classList.add('show');
}
function closeCalorieModal() {
  var modal = document.getElementById('calorieModal');
  if (modal) modal.classList.remove('show');
}

// Tabs Navigation
function switchTab(tabId) {
  document.querySelectorAll('.tab-trigger').forEach(function(btn) {
    if (btn.getAttribute('data-tab') === tabId) {
      btn.classList.add('active');
      btn.setAttribute('aria-selected', 'true');
    } else {
      btn.classList.remove('active');
      btn.setAttribute('aria-selected', 'false');
    }
  });

  document.querySelectorAll('.tab-pane').forEach(function(pane) {
    if (pane.id === 'pane-' + tabId) {
      pane.classList.add('active');
    } else {
      pane.classList.remove('active');
    }
  });

  if (history.replaceState) {
    history.replaceState(null, null, '#' + tabId);
  } else {
    location.hash = '#' + tabId;
  }
}

// --- Table Column Sorting ---
function sortTable(th, attrName, dataType, defaultDir) {
  var table = th.closest('table');
  if (!table) return;
  var tbody = table.querySelector('tbody');
  if (!tbody) return;

  var currentDir = th.getAttribute('data-sort-dir');
  var newDir = 'desc';
  if (!currentDir) {
    newDir = defaultDir || 'desc';
  } else if (currentDir === 'desc') {
    newDir = 'asc';
  } else {
    newDir = 'desc';
  }

  // Clear other header sort indicators in this table
  table.querySelectorAll('th.sortable').forEach(function(otherTh) {
    otherTh.removeAttribute('data-sort-dir');
    otherTh.classList.remove('sort-asc', 'sort-desc');
    var icon = otherTh.querySelector('.sort-icon');
    if (icon) icon.textContent = '↕';
  });

  // Set active sort indicator
  th.setAttribute('data-sort-dir', newDir);
  th.classList.add('sort-' + newDir);
  var icon = th.querySelector('.sort-icon');
  if (icon) icon.textContent = (newDir === 'desc') ? '↓' : '↑';

  // Get rows as array
  var rows = Array.prototype.slice.call(tbody.querySelectorAll('tr'));
  rows.sort(function(a, b) {
    var valA = a.getAttribute('data-' + attrName);
    var valB = b.getAttribute('data-' + attrName);
    if (valA === null) valA = a.textContent.trim();
    if (valB === null) valB = b.textContent.trim();

    var diff = 0;
    if (dataType === 'num') {
      var numA = parseFloat(valA) || 0;
      var numB = parseFloat(valB) || 0;
      diff = numA - numB;
    } else if (dataType === 'date') {
      diff = (valA > valB) ? 1 : (valA < valB ? -1 : 0);
    } else {
      diff = ('' + valA).localeCompare('' + valB, 'tr', { sensitivity: 'base' });
    }
    return (newDir === 'desc') ? -diff : diff;
  });

  // Re-append sorted rows
  rows.forEach(function(row) {
    tbody.appendChild(row);
  });
}

// --- Timeline Date Picker Navigation ---
function applyTimelineDateFilter() {
  var fromInput = document.getElementById('timelineFromDate');
  var toInput = document.getElementById('timelineToDate');
  var from = fromInput ? fromInput.value.trim() : '';
  var to = toInput ? toInput.value.trim() : '';
  if (!from && !to) {
    alert('Lütfen bir başlangıç veya bitiş tarihi seçin.');
    return;
  }
  if (from && !to) to = from;
  if (!from && to) from = to;

  window.location.href = '/?from=' + encodeURIComponent(from) + '&to=' + encodeURIComponent(to) + '#timeline';
}

function goToSpecificDate() {
  var fromInput = document.getElementById('timelineFromDate');
  var toInput = document.getElementById('timelineToDate');
  var d = (fromInput && fromInput.value.trim()) || (toInput && toInput.value.trim());
  if (!d) {
    alert('Lütfen gitmek istediğiniz tarihi başlangıç veya bitiş kutusundan seçin.');
    return;
  }
  window.location.href = '/?date=' + encodeURIComponent(d) + '#timeline';
}

function filterTimelineToday() {
  var today = new Date().toISOString().slice(0, 10);
  window.location.href = '/?date=' + encodeURIComponent(today) + '#timeline';
}

function resetTimelineDateFilter() {
  window.location.href = '/?days=30#timeline';
}

// --- Timeline Filter ---
var currentTimelineSource = 'all';
function setTimelineFilter(source, btn) {
  currentTimelineSource = source;
  document.querySelectorAll('#timelineSourceChips .filter-chip').forEach(function(el) {
    el.classList.remove('active');
  });
  if (btn) btn.classList.add('active');
  filterTimeline();
}

function resetTimelineFilter() {
  var input = document.getElementById('timelineSearch');
  if (input) input.value = '';
  setTimelineFilter('all', document.querySelector('#timelineSourceChips [data-source="all"]'));
}

function filterTimeline() {
  var q = ((document.getElementById('timelineSearch') && document.getElementById('timelineSearch').value) || '').trim().toLowerCase();
  var items = document.querySelectorAll('#pane-timeline .timeline-item');
  var visibleCount = 0;
  var hasActiveFilter = (currentTimelineSource !== 'all' || q.length > 0);
  var clearBtn = document.getElementById('timelineClearBtn');
  if (clearBtn) clearBtn.style.display = hasActiveFilter ? 'inline-block' : 'none';

  items.forEach(function(item) {
    var source = item.getAttribute('data-source');
    var search = item.getAttribute('data-search') || '';
    var matchSource = (currentTimelineSource === 'all' || source === currentTimelineSource);
    var matchSearch = (!q || search.indexOf(q) !== -1);
    if (matchSource && matchSearch) {
      item.style.display = '';
      visibleCount++;
    } else {
      item.style.display = 'none';
    }
  });

  document.querySelectorAll('#pane-timeline .timeline-day').forEach(function(day) {
    var visibleInDay = day.querySelectorAll('.timeline-item:not([style*="display: none"])').length;
    day.style.display = (visibleInDay > 0) ? '' : 'none';
  });

  var countEl = document.getElementById('timelineCount');
  if (countEl) countEl.textContent = 'Gösterilen: ' + visibleCount + ' / ' + items.length + ' aktivite';
  var emptyEl = document.getElementById('timelineEmptySearch');
  if (emptyEl) emptyEl.style.display = (visibleCount === 0 && items.length > 0) ? 'block' : 'none';
}

// --- FatSecret Food Entries Filter ---
var currentFoodMeal = 'all';
var currentFoodHighProtein = false;

function setFoodMealFilter(meal, btn) {
  currentFoodMeal = meal;
  document.querySelectorAll('#foodMealChips .filter-chip:not(#foodProteinChip)').forEach(function(el) {
    el.classList.remove('active');
  });
  if (btn) btn.classList.add('active');
  filterFoodEntries();
}

function toggleFoodProtein(btn) {
  currentFoodHighProtein = !currentFoodHighProtein;
  if (btn) {
    if (currentFoodHighProtein) btn.classList.add('active');
    else btn.classList.remove('active');
  }
  filterFoodEntries();
}

function resetFoodFilter() {
  var input = document.getElementById('foodSearch');
  if (input) input.value = '';
  var dateInput = document.getElementById('foodDateFilter');
  if (dateInput) dateInput.value = '';
  currentFoodHighProtein = false;
  var protChip = document.getElementById('foodProteinChip');
  if (protChip) protChip.classList.remove('active');
  setFoodMealFilter('all', document.querySelector('#foodMealChips [data-meal="all"]'));
}

function filterFoodEntries() {
  var q = ((document.getElementById('foodSearch') && document.getElementById('foodSearch').value) || '').trim().toLowerCase();
  var dateVal = ((document.getElementById('foodDateFilter') && document.getElementById('foodDateFilter').value) || '').trim();
  var rows = document.querySelectorAll('.food-row');
  var visibleCount = 0;
  var hasActiveFilter = (currentFoodMeal !== 'all' || currentFoodHighProtein || q.length > 0 || dateVal.length > 0);
  var clearBtn = document.getElementById('foodClearBtn');
  if (clearBtn) clearBtn.style.display = hasActiveFilter ? 'inline-block' : 'none';

  rows.forEach(function(row) {
    var meal = row.getAttribute('data-meal') || '';
    var protein = parseFloat(row.getAttribute('data-protein') || '0');
    var date = row.getAttribute('data-date') || '';
    var search = row.getAttribute('data-search') || '';

    var matchMeal = (currentFoodMeal === 'all' || meal.toLowerCase() === currentFoodMeal.toLowerCase());
    var matchProtein = (!currentFoodHighProtein || protein >= 20.0);
    var matchDate = (!dateVal || date === dateVal);
    var matchSearch = (!q || search.indexOf(q) !== -1);

    if (matchMeal && matchProtein && matchDate && matchSearch) {
      row.style.display = '';
      visibleCount++;
    } else {
      row.style.display = 'none';
    }
  });

  var countEl = document.getElementById('foodCount');
  if (countEl) countEl.textContent = 'Gösterilen: ' + visibleCount + ' / ' + rows.length + ' kayıt';
  var emptyEl = document.getElementById('foodEmptySearch');
  if (emptyEl) emptyEl.style.display = (visibleCount === 0 && rows.length > 0) ? 'block' : 'none';
}

// --- Goodreads Books Filter ---
var currentBookShelf = 'all';
var currentBookRating = 'all';

function setBookShelfFilter(shelf, btn) {
  currentBookShelf = shelf;
  document.querySelectorAll('#bookShelfChips .filter-chip').forEach(function(el) {
    el.classList.remove('active');
  });
  if (btn) btn.classList.add('active');
  filterBooks();
}

function setBookRatingFilter(rating, btn) {
  currentBookRating = rating;
  document.querySelectorAll('#bookRatingChips .filter-chip').forEach(function(el) {
    el.classList.remove('active');
  });
  if (btn) btn.classList.add('active');
  filterBooks();
}

function resetBookFilter() {
  var input = document.getElementById('bookSearch');
  if (input) input.value = '';
  currentBookRating = 'all';
  document.querySelectorAll('#bookRatingChips .filter-chip').forEach(function(el) {
    el.classList.remove('active');
  });
  var allRatingChip = document.querySelector('#bookRatingChips [data-rating="all"]');
  if (allRatingChip) allRatingChip.classList.add('active');

  setBookShelfFilter('all', document.querySelector('#bookShelfChips [data-shelf="all"]'));
}

function filterBooks() {
  var q = ((document.getElementById('bookSearch') && document.getElementById('bookSearch').value) || '').trim().toLowerCase();
  var rows = document.querySelectorAll('.book-row');
  var visibleCount = 0;
  var hasActiveFilter = (currentBookShelf !== 'all' || currentBookRating !== 'all' || q.length > 0);
  var clearBtn = document.getElementById('bookClearBtn');
  if (clearBtn) clearBtn.style.display = hasActiveFilter ? 'inline-block' : 'none';

  rows.forEach(function(row) {
    var shelf = row.getAttribute('data-shelf') || '';
    var r = parseInt(row.getAttribute('data-rating') || '0', 10);
    var search = row.getAttribute('data-search') || '';

    var matchShelf = (currentBookShelf === 'all' || shelf.toLowerCase() === currentBookShelf.toLowerCase());
    var matchRating = true;
    if (currentBookRating === '5') matchRating = (r === 5);
    else if (currentBookRating === '4') matchRating = (r >= 4);

    var matchSearch = (!q || search.indexOf(q) !== -1);

    if (matchShelf && matchRating && matchSearch) {
      row.style.display = '';
      visibleCount++;
    } else {
      row.style.display = 'none';
    }
  });

  var countEl = document.getElementById('bookCount');
  if (countEl) countEl.textContent = 'Gösterilen: ' + visibleCount + ' / ' + rows.length + ' kitap';
  var emptyEl = document.getElementById('bookEmptySearch');
  if (emptyEl) emptyEl.style.display = (visibleCount === 0 && rows.length > 0) ? 'block' : 'none';
}

// --- Letterboxd Films Filter ---
var currentFilmRating = 'all';
var currentFilmRewatchOnly = false;

function setFilmRatingFilter(rating, btn) {
  currentFilmRating = rating;
  document.querySelectorAll('#filmRatingChips .filter-chip:not(#filmRewatchChip)').forEach(function(el) {
    el.classList.remove('active');
  });
  if (btn) btn.classList.add('active');
  filterFilms();
}

function toggleFilmRewatch(btn) {
  currentFilmRewatchOnly = !currentFilmRewatchOnly;
  if (btn) {
    if (currentFilmRewatchOnly) btn.classList.add('active');
    else btn.classList.remove('active');
  }
  filterFilms();
}

function resetFilmFilter() {
  var input = document.getElementById('filmSearch');
  if (input) input.value = '';
  currentFilmRewatchOnly = false;
  var rewatchChip = document.getElementById('filmRewatchChip');
  if (rewatchChip) rewatchChip.classList.remove('active');
  setFilmRatingFilter('all', document.querySelector('#filmRatingChips [data-rating="all"]'));
}

function filterFilms() {
  var q = ((document.getElementById('filmSearch') && document.getElementById('filmSearch').value) || '').trim().toLowerCase();
  var rows = document.querySelectorAll('.film-row');
  var visibleCount = 0;
  var hasActiveFilter = (currentFilmRating !== 'all' || currentFilmRewatchOnly || q.length > 0);
  var clearBtn = document.getElementById('filmClearBtn');
  if (clearBtn) clearBtn.style.display = hasActiveFilter ? 'inline-block' : 'none';

  rows.forEach(function(row) {
    var r = parseFloat(row.getAttribute('data-rating') || '0');
    var isRewatch = row.getAttribute('data-rewatch') === 'true';
    var search = row.getAttribute('data-search') || '';

    var matchRating = true;
    if (currentFilmRating === '5') matchRating = (r >= 4.9);
    else if (currentFilmRating === '4') matchRating = (r >= 3.9);
    else if (currentFilmRating === '3') matchRating = (r >= 2.9);
    else if (currentFilmRating === 'unrated') matchRating = (r === 0);

    var matchRewatch = (!currentFilmRewatchOnly || isRewatch);
    var matchSearch = (!q || search.indexOf(q) !== -1);

    if (matchRating && matchRewatch && matchSearch) {
      row.style.display = '';
      visibleCount++;
    } else {
      row.style.display = 'none';
    }
  });

  var countEl = document.getElementById('filmCount');
  if (countEl) countEl.textContent = 'Gösterilen: ' + visibleCount + ' / ' + rows.length + ' film';
  var emptyEl = document.getElementById('filmEmptySearch');
  if (emptyEl) emptyEl.style.display = (visibleCount === 0 && rows.length > 0) ? 'block' : 'none';
}

// --- Notifications Filter & Relay Copy ---
function copyNotifWebhookUrl() {
  var url = window.location.origin + '/api/webhooks/notification';
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(url).then(function() {
      var fb = document.getElementById('notifWebhookFeedback');
      if (fb) {
        fb.style.display = 'inline';
        setTimeout(function() { fb.style.display = 'none'; }, 2500);
      }
    }).catch(function() {
      prompt('Webhook URL kopyalayın:', url);
    });
  } else {
    prompt('Webhook URL kopyalayın:', url);
  }
}

function filterNotifications() {
  var searchInput = document.getElementById('notifSearch');
  var appSelect = document.getElementById('notifAppFilter');
  var ongoingBox = document.getElementById('notifOngoingOnly');
  var countEl = document.getElementById('notifCountDisplay');
  var emptyEl = document.getElementById('notifEmptySearch');

  var query = searchInput ? searchInput.value.toLowerCase().trim() : '';
  var selectedApp = appSelect ? appSelect.value : 'all';
  var ongoingOnly = ongoingBox ? ongoingBox.checked : false;

  var items = document.querySelectorAll('.notif-feed-item');
  var visibleCount = 0;

  items.forEach(function(item) {
    var itemApp = item.getAttribute('data-app') || '';
    var isOngoing = item.getAttribute('data-ongoing') === 'true';
    var searchData = item.getAttribute('data-search') || '';

    var matchApp = (selectedApp === 'all' || itemApp === selectedApp);
    var matchOngoing = (!ongoingOnly || isOngoing);
    var matchQuery = (!query || searchData.indexOf(query) !== -1);

    if (matchApp && matchOngoing && matchQuery) {
      item.style.display = 'block';
      visibleCount++;
    } else {
      item.style.display = 'none';
    }
  });

  if (countEl) {
    countEl.textContent = 'Gösterilen: ' + visibleCount + ' / ' + items.length + ' bildirim';
  }
  if (emptyEl) {
    emptyEl.style.display = (visibleCount === 0 && items.length > 0) ? 'block' : 'none';
  }
}

function resetNotifFilter() {
  var searchInput = document.getElementById('notifSearch');
  var appSelect = document.getElementById('notifAppFilter');
  var ongoingBox = document.getElementById('notifOngoingOnly');
  if (searchInput) searchInput.value = '';
  if (appSelect) appSelect.value = 'all';
  if (ongoingBox) ongoingBox.checked = false;
  filterNotifications();
}

function toggleTheme() {
  var isDark = document.documentElement.classList.toggle('dark');
  try {
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
  } catch (e) {}
}

document.addEventListener('DOMContentLoaded', function() {
  // Initialize webhook URL with origin
  var notifEndpointEl = document.getElementById('notifWebhookEndpoint');
  if (notifEndpointEl && window.location.origin) {
    notifEndpointEl.textContent = window.location.origin + '/api/webhooks/notification';
  }
  // Bind tab buttons
  document.querySelectorAll('.tab-trigger').forEach(function(btn) {
    btn.addEventListener('click', function(e) {
      e.preventDefault();
      var tab = btn.getAttribute('data-tab');
      switchTab(tab);
    });
  });

  // Activate tab based on URL hash
  var initialHash = window.location.hash.replace('#', '');
  if (initialHash && document.getElementById('pane-' + initialHash)) {
    switchTab(initialHash);
  }

  // Modal handlers
  var modal = document.getElementById('syncModal');
  if (!modal) return;
  var modalIcon = document.getElementById('modalIcon');
  var modalTitle = document.getElementById('modalTitle');
  var modalSubtitle = document.getElementById('modalSubtitle');
  var modalBody = document.getElementById('modalBody');
  var modalCloseBtn = document.getElementById('modalCloseBtn');
  var shouldReload = false;

  function showLoading(title, subtitle) {
    shouldReload = false;
    modalIcon.className = 'modal-icon loading';
    modalIcon.innerHTML = '<svg class="modal-spinner" viewBox="0 0 24 24" width="24" height="24"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="32" stroke-linecap="round"/></svg>';
    modalTitle.textContent = title;
    modalSubtitle.textContent = subtitle;
    modalBody.style.display = 'none';
    modalBody.className = 'modal-body';
    modalBody.textContent = '';
    modalCloseBtn.style.display = 'none';
    modal.classList.add('show');
  }

  function showSuccess(title, message) {
    shouldReload = true;
    modalIcon.className = 'modal-icon success';
    modalIcon.innerHTML = '<svg width="24" height="24" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>';
    modalTitle.textContent = title;
    modalSubtitle.textContent = 'İşlem başarıyla tamamlandı.';
    modalBody.textContent = message;
    modalBody.className = 'modal-body';
    modalBody.style.display = 'block';
    modalCloseBtn.textContent = 'Tamam (Sayfayı Yenile)';
    modalCloseBtn.className = 'button';
    modalCloseBtn.style.display = 'inline-flex';
  }

  function showError(title, errorMsg) {
    shouldReload = false;
    modalIcon.className = 'modal-icon error';
    modalIcon.innerHTML = '<svg width="24" height="24" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/></svg>';
    modalTitle.textContent = title;
    modalSubtitle.textContent = 'İşlem sırasında bir hata oluştu:';
    modalBody.textContent = errorMsg;
    modalBody.className = 'modal-body error-box';
    modalBody.style.display = 'block';
    modalCloseBtn.textContent = 'Kapat';
    modalCloseBtn.className = 'button secondary';
    modalCloseBtn.style.display = 'inline-flex';
  }

  function closeModal() {
    modal.classList.remove('show');
    if (shouldReload) {
      setTimeout(function() {
        window.location.reload();
      }, 150);
    }
  }

  modalCloseBtn.addEventListener('click', closeModal);
  modal.addEventListener('click', function(e) {
    if (e.target === modal && modalCloseBtn.style.display !== 'none') {
      closeModal();
    }
  });
  document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape' && modal.classList.contains('show') && modalCloseBtn.style.display !== 'none') {
      closeModal();
    }
  });

  // Intercept all sync and import forms
  var forms = document.querySelectorAll('form[action$="/sync"], form[action$="/import"]');
  forms.forEach(function(form) {
    form.addEventListener('submit', async function(e) {
      e.preventDefault();
      var btn = form.querySelector('button');
      var action = form.getAttribute('action');
      var isImport = action.indexOf('/import') !== -1;

      var name = 'Senkronizasyon';
      if (action.indexOf('goodreads') !== -1) name = 'Goodreads';
      else if (action.indexOf('letterboxd') !== -1) name = 'Letterboxd';
      else if (action.indexOf('fatsecret') !== -1) name = 'FatSecret';

      var loadingTitle = isImport ? (name + ' İçe Aktarılıyor') : (name + ' Senkronize Ediliyor');
      var loadingSub = isImport ? 'Dosya yükleniyor ve veriler işleniyor...' : 'Sunucudan en güncel veriler alınıyor...';
      showLoading(loadingTitle, loadingSub);
      if (btn) btn.disabled = true;

      try {
        var options = {
          method: 'POST',
          headers: {
            'Accept': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
          }
        };

        if (isImport) {
          options.body = new FormData(form);
        } else {
          var formData = new FormData(form);
          options.body = new URLSearchParams(formData);
        }

        var response = await fetch(action, options);
        var data = await response.json().catch(function() { return null; });

        if (!response.ok || (data && data.success === false)) {
          var errText = (data && data.error) ? data.error : ('Sunucu hatası: HTTP ' + response.status);
          showError(name + ' Hatası', errText);
        } else if (data && data.success) {
          showSuccess(name + ' Başarılı', data.message || 'Senkronizasyon başarıyla tamamlandı.');
        } else {
          showSuccess(name + ' Tamamlandı', 'İşlem tamamlandı.');
        }
      } catch (err) {
        showError('Bağlantı Hatası', err.message || 'İstek gönderilemedi.');
      } finally {
        if (btn) btn.disabled = false;
      }
    });
  });

  // Calorie target form handler
  var calForm = document.getElementById('calorieTargetForm');
  if (calForm) {
    calForm.addEventListener('submit', async function(e) {
      e.preventDefault();
      var input = document.getElementById('calorieTargetInput');
      var val = input ? input.value : '2200';
      try {
        var res = await fetch('/settings/calorie-target', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'Accept': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
          },
          body: new URLSearchParams({ target: val })
        });
        var data = await res.json().catch(function() { return null; });
        if (res.ok && data && data.success) {
          closeCalorieModal();
          window.location.reload();
        } else {
          alert((data && data.error) || 'Hedef güncellenemedi.');
        }
      } catch (err) {
        alert('Hata: ' + err.message);
      }
    });
  }

  var calModal = document.getElementById('calorieModal');
  if (calModal) {
    calModal.addEventListener('click', function(e) {
      if (e.target === calModal) {
        closeCalorieModal();
      }
    });
  }
  document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
      var cm = document.getElementById('calorieModal');
      if (cm && cm.classList.contains('show')) {
        closeCalorieModal();
      }
    }
  });
});
</script>`
}

func renderLoginPageHTML(errorMsg string) string {
	errHtml := ""
	if errorMsg != "" {
		errHtml = `<div class="auth-error">` + html.EscapeString(errorMsg) + `</div>`
	}

	return `<!doctype html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Giriş · Personal Records</title>
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
    :root {
      --background: 0 0% 100%;
      --foreground: 240 10% 3.9%;
      --card: 0 0% 100%;
      --card-foreground: 240 10% 3.9%;
      --primary: 240 5.9% 10%;
      --primary-foreground: 0 0% 98%;
      --secondary: 240 4.8% 95.9%;
      --secondary-foreground: 240 5.9% 10%;
      --muted: 240 4.8% 95.9%;
      --muted-foreground: 240 3.8% 46.1%;
      --destructive: 0 84.2% 60.2%;
      --border: 240 5.9% 90%;
      --input: 240 5.9% 90%;
      --ring: 240 5.9% 10%;
      --radius: 8px;
    }
    .dark {
      --background: 240 10% 3.9%;
      --foreground: 0 0% 98%;
      --card: 240 10% 4.8%;
      --card-foreground: 0 0% 98%;
      --primary: 0 0% 98%;
      --primary-foreground: 240 5.9% 10%;
      --secondary: 240 3.7% 15.9%;
      --secondary-foreground: 0 0% 98%;
      --muted: 240 3.7% 15.9%;
      --muted-foreground: 240 5% 64.9%;
      --destructive: 0 62.8% 30.6%;
      --border: 240 3.7% 15.9%;
      --input: 240 3.7% 15.9%;
      --ring: 240 4.9% 83.9%;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background-color: hsl(var(--background));
      color: hsl(var(--foreground));
      font-family: 'Inter', ui-sans-serif, system-ui, -apple-system, sans-serif;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16px;
    }
    .auth-card {
      background: hsl(var(--card));
      border: 1px solid hsl(var(--border));
      border-radius: 12px;
      padding: 32px 28px;
      width: 100%;
      max-width: 400px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.15), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
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
    .btn-submit {
      width: 100%;
      height: 40px;
      background: hsl(var(--primary));
      color: hsl(var(--primary-foreground));
      border: none;
      border-radius: 6px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      transition: opacity 0.15s ease;
    }
    .btn-submit:hover {
      opacity: 0.9;
    }
  </style>
</head>
<body>
  <div class="auth-card">
    <div class="auth-icon-wrap">
      <svg width="22" height="22" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
      </svg>
    </div>
    <h2 class="auth-title">Personal Records</h2>
    <p class="auth-desc">Bu sunucu korumalıdır. Devam etmek için lütfen API Anahtarınızı girin.</p>
    <form action="/api/auth/login" method="POST">
      <div class="auth-field">
        <label class="auth-label" for="auth-key-input">API Anahtarı / Şifre</label>
        <div class="auth-input-wrap">
          <input type="password" id="auth-key-input" name="apiKey" placeholder="••••••••••••" autofocus required />
          <button type="button" class="auth-toggle-btn" onclick="var i=document.getElementById('auth-key-input');i.type=i.type==='password'?'text':'password';">👁️</button>
        </div>
      </div>
      ` + errHtml + `
      <button type="submit" class="btn-submit">Giriş Yap</button>
    </form>
  </div>
</body>
</html>`
}
