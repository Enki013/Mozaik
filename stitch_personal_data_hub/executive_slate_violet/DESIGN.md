---
name: Executive Slate & Violet
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#eaedff'
  surface-container-high: '#e2e7ff'
  surface-container-highest: '#dae2fd'
  on-surface: '#131b2e'
  on-surface-variant: '#464554'
  inverse-surface: '#283044'
  inverse-on-surface: '#eef0ff'
  outline: '#767586'
  outline-variant: '#c7c4d7'
  surface-tint: '#494bd6'
  primary: '#4648d4'
  on-primary: '#ffffff'
  primary-container: '#6063ee'
  on-primary-container: '#fffbff'
  inverse-primary: '#c0c1ff'
  secondary: '#006591'
  on-secondary: '#ffffff'
  secondary-container: '#39b8fd'
  on-secondary-container: '#004666'
  tertiary: '#006c49'
  on-tertiary: '#ffffff'
  tertiary-container: '#00885d'
  on-tertiary-container: '#000703'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e1e0ff'
  primary-fixed-dim: '#c0c1ff'
  on-primary-fixed: '#07006c'
  on-primary-fixed-variant: '#2f2ebe'
  secondary-fixed: '#c9e6ff'
  secondary-fixed-dim: '#89ceff'
  on-secondary-fixed: '#001e2f'
  on-secondary-fixed-variant: '#004c6e'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fd'
typography:
  display-lg:
    fontFamily: Manrope
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Manrope
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Manrope
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: 0em
  title-lg:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0em
  title-md:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
    letterSpacing: 0.005em
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.01em
  body-sm:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-lg:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Hanken Grotesk
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.03em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-tablet: 1.5rem
  gutter-desktop: 2rem
  margin: 1rem
  margin-tablet: 1.5rem
  margin-desktop: 3rem
  space-xxs: 0.125rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
  space-2xl: 3rem
---

## Brand & Style

The design system establishes a high-trust, executive-grade personal data aggregator interface tailored for Flutter and rooted in Material 3 principles. It targets discerning professionals, quantified-self enthusiasts, and knowledge workers who demand clarity over clutter when aggregating sensitive life metrics—ranging from health and nutrition to media, finance, and cognitive habits.

The aesthetic fuses **Corporate Minimalism** with an **Airy Executive Dashboard** feel:
- **Calm Authority**: Deep slate neutrals paired with radiant, focused indigo and luminous violet accents (#6366F1) create an environment that feels analytical, precise, and respectful of cognitive bandwidth.
- **Tonal Layering over Skeuomorphism**: Surfaces rely on clean brightness, tonal elevation, and razor-sharp contrast steps rather than heavy dropshadows or overt ornamentation.
- **Architectural Clarity**: Bold, balanced geometric typography anchors analytical dashboards, while hyper-legible body copy ensures micro-data points, timestamps, and metrics are scanned effortlessly.

## Colors

The color architecture is built around a luminous violet primary, deep slate foundational neutrals, and dedicated semantic channels designed to distinguish plugin streams at a glance without visual discord.

### Key Palettes
- **Primary (`#6366F1`)**: Luminous violet anchor used for primary brand touchpoints, active navigation indicators, key floating action triggers, and active segmented controls.
- **Secondary (`#0EA5E9`)**: Electric cyan used for secondary analytical highlights, media aggregation streams, and ambient contrast against deep slate text.
- **Tertiary (`#10B981`)**: Vitality green dedicated to positive trends, physical wellness metrics, and completed telemetry loops.
- **Neutral (`#0F172A`)**: Deep executive slate used for dominant text hierarchies and foundational structure.

### Surface System
- **Surface Canvas**: `#F8FAFC` (Slate 50) — Crisp, cool, spacious baseline canvas.
- **Surface Container Low**: `#FFFFFF` — Pure white card surfaces providing elevated optical clarity.
- **Surface Container High**: `#F1F5F9` (Slate 100) — Inset grouped modules, search fields, and secondary structural divisions.
- **Surface Outline / Border**: `#E2E8F0` (Slate 200) — Delicate 1px structural boundaries.

### Semantic Domain Plugin Accents
- **Nutrition & Health**: `#10B981` (Emerald)
- **Reading & Knowledge**: `#F59E0B` (Amber)
- **Entertainment & Media**: `#06B6D4` (Cyan)
- **System & Automation**: `#6366F1` (Violet)

## Typography

The type scale combines **Manrope** for architectural, authoritative headings with **Hanken Grotesk** for clean, legible data presentation.

### Usage Rules
- **Numerical Telemetry**: Large aggregator counts and primary metrics use `headline-lg` or `headline-md` set in Manrope semi-bold or bold, maintaining uniform tabular figures.
- **Labels & Semantic Tags**: Chip tags, plugin identifiers, and status pill texts leverage `label-md` and `label-sm` in Hanken Grotesk with a subtle positive letter-spacing (`0.02em` to `0.03em`) to retain legibility at small scale.
- **Editorial Summary & Aggregation Feed**: In-stream body snippets and log entries use `body-md` with `lineHeight: 20px`, keeping card heights compact and scannable.

## Layout & Spacing

The layout philosophy follows a responsive mobile-first grid calibrated for fluid Flutter column layouts.

### Screen Adaptations
- **Mobile (<600dp)**: Single fluid column. Margins are locked to `space-md` (16dp). Internal card grouping padding uses `space-md` (16dp). List items use `space-sm` (8dp) vertical separation.
- **Tablet (600dp - 1024dp)**: 2-column dashboard layout with a fixed 72dp navigation rail. Margins expand to `margin-tablet` (24dp) with `gutter-tablet` (24dp).
- **Desktop / Foldable (>1024dp)**: Multi-pane master-detail arrangement. Left overview aggregate rail, central timeline feed, right detail pane with `margin-desktop` (48dp).

### Spacing Cadence
Component internals respect an 8dp baseline grid (with a 4dp half-step for micro labels and chips). Gaps between distinct feed cards are kept strictly at `space-md` (16dp) to foster deliberate separation without disorienting content flow.

## Elevation & Depth

This design system eschews muddy, high-spread shadows in favor of **Material 3 Tonal Elevation** combined with ambient low-opacity color-tinted borders.

### Surface Hierarchy
1. **Level 0 (Canvas Base)**: `#F8FAFC` flat surface. No elevation, no border.
2. **Level 1 (Card & Modular Units)**: `#FFFFFF` surface accompanied by a 1px border of `#E2E8F0` and an ultra-diffused ambient shadow (`box-shadow: 0 1px 3px 0 rgba(15, 23, 42, 0.04), 0 1px 2px -1px rgba(15, 23, 42, 0.02)`).
3. **Level 2 (Active States, Filter Modals, Inset Drawers)**: `#FFFFFF` surface with an elevated shadow tinted slightly with slate (`box-shadow: 0 4px 6px -1px rgba(15, 23, 42, 0.06), 0 2px 4px -2px rgba(15, 23, 42, 0.04)`).
4. **Level 3 (Floating Actions & Pickers)**: `#FFFFFF` or `#6366F1` tinted elevation (`box-shadow: 0 10px 15px -3px rgba(99, 102, 241, 0.12), 0 4px 6px -4px rgba(99, 102, 241, 0.08)`).

## Shapes

The shape system leverages Material 3's modern roundedness scale (`roundedness: 2`), applying soft corner architecture that maintains an executive feel without becoming juvenile.

### Radius Distribution
- **Cards & Aggregator Modules**: `rounded-lg` (16dp) – standard enclosure for all data feed tiles and overview summaries.
- **Dialogs & Sheet Insets**: `rounded-xl` (24dp) – modal sheets, aggregated bottom insights, and full-screen drawer headers.
- **Controls & Buttons**: `rounded-sm` to `rounded-md` (8dp to 12dp) – consistent touch targets for segmented buttons and inputs.
- **Pills & Chips**: Fully pill-rounded (9999dp) – semantic plugin indicators, category filter chips, and circular metric trackers.

## Components

### Buttons
- **Primary Filled Button**: Height 48dp, background `#6366F1`, text `#FFFFFF` (`label-lg`), corner radius 12dp. On hover/press, lighten to `#4F46E5`.
- **Tonal Button**: Height 48dp, background `#EEF2FF`, text `#4F46E5`, 12dp corner radius.
- **Segmented Buttons**: Encapsulated within a `#F1F5F9` track. Active segment uses `#FFFFFF` with Level 1 shadow, `#0F172A` text, and 8dp radius.

### FilterChips & Badges
- **Unselected FilterChip**: Background `#FFFFFF`, 1px border `#E2E8F0`, text `#475569` (`label-md`), height 32dp, radius 9999dp.
- **Selected FilterChip**: Background `#EEF2FF`, 1px border `#6366F1`, text `#4338CA`, with leading checkmark icon (16dp).
- **Semantic Plugin Badges**: 24dp height pill badges featuring soft 10% opacity fills of domain colors (e.g., `#ECFDF5` for Nutrition, `#FEF3C7` for Reading) paired with bold solid labels.

### Cards & Grouped Containers
- **Filled & Outlined Feed Card**: Background `#FFFFFF`, border 1px solid `#E2E8F0`, radius 16dp, padding 16dp. Header displays plugin tag, trailing timestamp in `body-sm` (`#64748B`), bold title in `title-md`, and content body.

### ListTiles
- Clean Material 3 style: 56dp minimum height, leading 40dp rounded-md (8dp radius) tonal icon avatar, title in `title-md` (`#0F172A`), subtitle in `body-sm` (`#64748B`), and subtle chevron-right indicator.

### Progress Indicators
- **LinearProgressIndicator**: Track height 6dp, background `#E2E8F0`, progress bar `#6366F1` with 9999dp rounded ends.
- **CircularProgressIndicator**: Stroke width 4dp, clean track `#F1F5F9`, active arc `#6366F1` with center numerical readout in Manrope bold.

### NavigationBar
- Material 3 NavigationBar: Height 80dp, surface `#FFFFFF`, top border 1px solid `#E2E8F0`. Active pill indicator uses `#EEF2FF` with `#6366F1` icon color and label in `label-sm` (`#4338CA`).