---
name: TurnAway
colors:
  surface: '#f9f9ff'
  surface-dim: '#d4dae9'
  surface-bright: '#f9f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f0f3ff'
  surface-container: '#e8eefd'
  surface-container-high: '#e3e8f7'
  surface-container-highest: '#dde2f1'
  on-surface: '#161c26'
  on-surface-variant: '#45464e'
  inverse-surface: '#2b313c'
  inverse-on-surface: '#ecf1ff'
  outline: '#76777f'
  outline-variant: '#c6c6cf'
  surface-tint: '#525d80'
  primary: '#081534'
  on-primary: '#ffffff'
  primary-container: '#1e2a4a'
  on-primary-container: '#8691b7'
  inverse-primary: '#bac5ee'
  secondary: '#126967'
  on-secondary: '#ffffff'
  secondary-container: '#a2edea'
  on-secondary-container: '#1a6e6c'
  tertiary: '#001a20'
  on-tertiary: '#ffffff'
  tertiary-container: '#00303a'
  on-tertiary-container: '#6a9aa8'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dae2ff'
  primary-fixed-dim: '#bac5ee'
  on-primary-fixed: '#0d1a39'
  on-primary-fixed-variant: '#3a4667'
  secondary-fixed: '#a5f0ed'
  secondary-fixed-dim: '#89d3d1'
  on-secondary-fixed: '#00201f'
  on-secondary-fixed-variant: '#00504e'
  tertiary-fixed: '#b9eafa'
  tertiary-fixed-dim: '#9dcede'
  on-tertiary-fixed: '#001f27'
  on-tertiary-fixed-variant: '#184d5a'
  background: '#f9f9ff'
  on-background: '#161c26'
  surface-variant: '#dde2f1'
typography:
  display-lg:
    fontFamily: Manrope
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.03em
  display-lg-mobile:
    fontFamily: Manrope
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.025em
  headline-lg:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Manrope
    fontSize: 26px
    fontWeight: '600'
    lineHeight: 34px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Manrope
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.005em
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 26px
    letterSpacing: -0.01em
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
    letterSpacing: 0em
  body-sm:
    fontFamily: Hanken Grotesk
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
    letterSpacing: 0.005em
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
    letterSpacing: 0.04em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-desktop: 1.5rem
  margin: 1.25rem
  margin-tablet: 2rem
  margin-desktop: 3rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style

This design system establishes an executive-grade, restorative digital environment calibrated specifically for intentional focus and cognitive clarity. Blending modern Material 3 tokenized structure with iOS Human Interface Guideline craftsmanship, it pairs midnight slate-indigo foundations with calming aquatic accents to create an interface that feels quiet, unhurried, and deeply grounding.

The visual direction rejects aggressive red-alert gamification and doom-inducing metrics. Instead, it frames digital boundaries through clarity and restraint: generous white space, soft tinted containers, tactile pill-shaped controls, and micro-interactions that feel measured and effortless. The experience conveys quiet confidence, deliberate attention, and physical tranquility.

## Colors

The palette is engineered around deliberate visual rest, anchored by deep midnight indigo (`#1E2A4A`) as the commanding primary tone for key actions, high-emphasis text, and active session boundaries. Secondary calming teal (`#2B7A78`) and muted aquatic slate (`#50808E`) serve as intentional state indicators—signaling restorative intervals, successful wind-downs, and mindful limits without inducing urgency or cognitive fatigue.

Surfaces rely on subtle cool-tinted tiers rather than stark, pure whites. Base backgrounds rest on `#F6F8FB`, with cards elevated through `#FFFFFF` and layered containers using faint indigo wash overlays (`rgba(30, 42, 74, 0.04)`). Neutral tokens provide balanced hierarchy, prioritizing effortless legibility during long focus sessions.

## Typography

The typographic hierarchy pairs the structured geometric elegance of Manrope for headers and numeric metrics with the clean, highly legible neutral strokes of Hanken Grotesk for body and interface controls. 

Large numbers—such as remaining screen quota or focus intervals—utilize Manrope Display tokens with tight negative tracking to impart an authoritative, calm presence. Body copy remains airy with comfortable line-height ratios that maintain reading flow without visual clutter. Labels and meta-indicators apply slight uppercase kerning where needed to retain legibility across micro-dashboards and widget surfaces.

## Layout & Spacing

Layouts follow an 8-point base spatial rhythm built upon fluid container arrangements. The grid system adapts flexibly:
- **Mobile (<640px):** Single-column layout with 20px outer canvas margins and 16px column gutters. Dense card groups employ `space-md` gaps.
- **Tablet (640px–1024px):** 6-column fluid structure with 32px canvas margins and 20px gutters, transitioning metric summaries into dual-column cards.
- **Desktop (>1024px):** 12-column layout maxed at 1160px container width with 48px margins, centering dashboard elements into focused, distraction-free cards.

Internal element padding prioritizes breathing room. Cards and parent containers utilize `space-lg` to `space-xl` internally, maintaining an open, unhurried posture throughout the UI.

## Elevation & Depth

Visual hierarchy eschews dramatic high-contrast drops, relying instead on ambient tonal surfaces paired with soft, indigo-tinted shadow blurs and ultra-subtle border rings.

- **Level 0 (Canvas):** Ground plane finished with `#F6F8FB`.
- **Level 1 (Default Cards & Surfaces):** `#FFFFFF` paired with an ultra-faint border ring (`1px solid rgba(30, 42, 74, 0.06)`) and a whisper shadow: `0 4px 16px -2px rgba(30, 42, 74, 0.04)`.
- **Level 2 (Active/Floating Controls):** `#FFFFFF` with `0 8px 24px -4px rgba(30, 42, 74, 0.08)` and subtle border highlights for popovers, dropdowns, and active tactile cards.
- **Level 3 (Modals & Sheet Overlays):** `#FFFFFF` elevated with `0 16px 40px -8px rgba(30, 42, 74, 0.14)` and a tinted indigo scrim (`rgba(22, 31, 54, 0.40)`) with `backdrop-filter: blur(8px)`.

## Shapes

The shape system embraces a rounded, pill-forward posture (`roundedness: 3`). Fully rounded capsules are standard for interactive chips, segmented controls, primary buttons, and floating navigation bars.

Standard surface cards and dashboard modules leverage expansive rounded corners (`rounded-3xl` / 1.75rem to 2rem corner radiuses). This generous curve structure evokes organic, friendly tactile hardware, softening screen time statistics and making session configurations feel approachable rather than restrictive.

## Components

### Buttons
- **Primary:** Full pill capsule (`rounded-full`), background `#1E2A4A`, text `#FFFFFF`, with `14px` vertical and `24px` horizontal padding. On press, transitions to `#161F36` with a micro-scale of `0.98`.
- **Secondary / Rest:** Pill capsule, background `rgba(43, 122, 120, 0.12)`, text `#2B7A78`, border `1px solid rgba(43, 122, 120, 0.20)`.
- **Ghost:** Transparent background with `#1E2A4A` text, gaining `rgba(30, 42, 74, 0.05)` fill on hover.

### Chips & Segmented Controls
- Segmented tab tracks sit within an enclosed pill container filled with `rgba(30, 42, 74, 0.05)` and `4px` internal padding. Active segments slide smoothly via a pure `#FFFFFF` pill with a micro ambient shadow (`0 2px 8px rgba(30, 42, 74, 0.06)`) and bold text.
- Filter chips feature fully rounded pill borders (`1px solid rgba(30, 42, 74, 0.12)`), filling with deep indigo and white text when toggled active.

### Cards
- Primary information blocks feature a `28px` corner radius, `#FFFFFF` background, `1px solid rgba(30, 42, 74, 0.06)` outline ring, and `space-lg` internal padding. 
- Goal-reached and mindful-breathing states tint card backgrounds with faint teal washes (`#F0F7F6`).

### Input Fields & Tactile Sliders
- Text inputs use `rounded-2xl` shapes, a neutral fill of `#F9FAFC`, and an inset border of `1.5px solid rgba(30, 42, 74, 0.10)`. Focus transitions border color to `#1E2A4A` with a subtle focus glow ring (`3px rgba(30, 42, 74, 0.12)`).
- Time duration sliders use continuous thick track bars (`8px` height, rounded pill ends) in muted teal or slate, anchored by a tactile circular thumb (`28px` diameter) with an elevated center and smooth gesture-snapping feedback.

### Lists, Checkboxes & Radios
- Settings rows feature inset-grouped card layouts with divider insets matching iOS group styles.
- Toggles utilize smooth Material 3 / iOS hybrid switches: an oversized pill track (`52px × 32px`) with a floating circular knob (`26px`) that shifts smoothly between slate neutral and deep indigo. Checkboxes apply `rounded-md` outlines, filling with `#2B7A78` when checked.