# Closette — Iconography & Design Brief

A single source of truth for the **custom icon set** that replaces every emoji in
the app, plus enough project context that a designer (or an image/vector tool)
can produce a cohesive, on-brand family in one pass.

---

## 1. What Closette is

**Closette** is an AI-powered personal **wardrobe & beauty inventory assistant**.
You photograph what you own; AI recognizes it and files it away; then Closette
helps you get dressed, track beauty products, and shop smarter.

Four core journeys:

| Flow | Name | What it does |
|------|------|--------------|
| **A** | Add to wardrobe | Photo → AI analysis → confirm → saved to your closet |
| **B** | Beauty inventory | Add products, track ingredients |
| **C** | Get Ready | Describe an occasion → a complete outfit from pieces you own |
| **D** | Should I Buy This? | Compare a candidate purchase against your wardrobe |

**Platform:** React Native (Expo), TypeScript. **Backend:** Spring Boot + a
FastAPI AI service. All UI values come from semantic design tokens in
`mobile/src/theme/tokens.ts`.

---

## 2. Brand & visual direction

> **Dusty-pink, feminine but not childish.** Pinterest + clean beauty brand +
> modern fashion app. Not Barbie, not a teenage diary.

**Palette** (from `tokens.ts`):

| Role | Hex | Use in icons |
|------|-----|-------------|
| Primary (dusty pink) | `#E7A2B6` | Active/selected icon fill or stroke |
| Primary dark | `#D27C95` | Pressed / emphasis strokes |
| Soft rose | `#F5C6D4` | Secondary fills, backgrounds |
| Light rose | `#FCE4EC` | Icon "chip" backgrounds |
| Mauve | `#A0687A` | Neutral-but-warm line icons |
| Charcoal | `#2C2429` | Default line icon stroke |
| Stone | `#79666E` | Inactive / muted icons |
| Success | `#5B8C6E` | Verified / met states |
| Warning | `#B98338` | Nudges (verify email) |
| Danger | `#C25C6B` | Errors, unmet rules |

---

## 3. Icon design language

One consistent recipe for **every** icon below:

- **Style:** line-first (outlined), rounded joins and caps — soft, friendly, feminine.
- **Stroke weight:** ~1.75px on a 24px grid (≈ 7.3% of the box). Keep it uniform.
- **Corner radius:** generous; no sharp 90° corners. Match the app's pill/rounded language.
- **Grid & padding:** design on **24×24**, keep a **2px** safe margin (live area 20×20). Ship **also at 48×48** for retina hero uses.
- **Format:** **SVG** (single color via `currentColor` so tokens drive the color), plus optional 2-tone variant using primary + soft-rose for "hero" cards.
- **Two weights per icon:**
  - **Outline** (default / inactive) — stroke only.
  - **Filled/soft** (active / selected / hero) — filled with `softRose`, primary stroke.
- **Optical consistency:** all icons should feel the same visual size — normalize by area, not by bounding box (a circle looks bigger than a square at equal box size).
- **No text inside icons.** No drop shadows. No gradients (keeps them theme-swappable).

**Accessibility:** every icon must have a text label or `accessibilityLabel`
beside it — icons never carry meaning by color alone (a rule the app already
follows, e.g. selected chips also change font weight).

---

## 4. The icon set

Grouped by purpose. **Name** is the proposed component/file name
(`Icon<Name>` → `icons/<name>.svg`). "Now" shows the emoji it replaces.

### 4.1 Navigation — bottom tab bar (5)

The 5 anchors of the app. These need an **outline** (inactive) and **filled**
(active) weight; active tint = `primaryDark`.

| Name | Now | Meaning / where | Design note |
|------|-----|-----------------|-------------|
| `nav-home` | 🏠 | Home tab — the dashboard | Simple house; rounded roof. |
| `nav-wardrobe` | 🎀 | Wardrobe tab — your clothes | A **hanger** reads clearer than a bow for "clothes"; keep a bow accent to stay on-brand. |
| `nav-getready` | ✨ | Get Ready tab — outfit builder | A **dress/outfit + sparkle**, or a wand. Must differ from the generic "magic" sparkle (see `ai-magic`). |
| `nav-beauty` | 💄 | Beauty tab — products | Lipstick bullet, or a cosmetics drop. |
| `nav-profile` | 👤 | Profile tab — account | Soft bust/silhouette. |

### 4.2 Hero actions — Home cards (4)

Larger, can use the **2-tone soft** variant inside the pink cards.

| Name | Now | Meaning | Note |
|------|-----|---------|------|
| `action-getready` | ✨ | "Get Ready — what should I wear?" | Same concept as `nav-getready`, hero weight. |
| `action-wardrobe` | 🎀 | "My Stuff — view your collection" | Same concept as `nav-wardrobe`. |
| `action-shop` | 🛍️ | "Should I Buy This? — check before you shop" | Shopping bag; a small check/heart inside ties it to "smart buying". |
| `action-beauty` | 💄 | "Beauty — your collection" | Same as `nav-beauty`. |

> Reuse is intentional: `nav-*` and `action-*` for the same concept can share one
> drawing at two sizes/weights. That keeps the set small.

### 4.3 Capture & AI flow — add to wardrobe (Flow A)

| Name | Now | Meaning / where | Note |
|------|-----|-----------------|------|
| `camera` | 📷 📸 | "Take a photo" (AddItem) | Rounded camera; consistent shutter. |
| `gallery` | 🖼️ | "Choose from library" (AddItem) | Framed image / stacked photos. |
| `ai-magic` | ✨ | AI analysis / "smart" (AddItem, ConfirmItem, Get Ready, primary Button icon, EmptyState) | The **sparkle** = anything AI-powered. The most-used glyph — make it iconic. |
| `garment` | 🧥 | Wardrobe item **placeholder** (ItemTile with no image) | Neutral clothing silhouette; low-contrast, it's a placeholder. |

### 4.4 Feedback — Get Ready (Flow C)

The reaction row on a generated look. Feeds the recommendation engine.

| Name | Now | Meaning | Note |
|------|-----|---------|------|
| `love` | ❤ | "Love this look" (strong positive) | Filled heart, primary/danger tone. |
| `dislike` | 👎 | "Not for me" | Thumb-down, or a soft broken-heart to stay elegant. |
| `save` | 🔖 | "Save look" (bookmark) | Bookmark ribbon. Shared with Profile "Saved looks". |
| `favorite` | ♥ ♡ | Favorite toggle on item tiles | Outline = off, filled = on. Distinct from `love` (smaller, tile corner). |

### 4.5 Profile menu (5)

| Name | Now | Meaning | Note |
|------|-----|---------|------|
| `palette` | 🎨 | Style preferences | Artist palette or color swatches. |
| `save` | 🔖 | Saved looks & history | Reuse `save`. |
| `privacy` | 🔒 | Privacy — "your data stays yours" | Rounded padlock. |
| `info` | ℹ️ | About Closette | Info circle. |
| `avatar` | 👤 | Profile avatar (identity card) | Reuse `nav-profile`, filled. |

### 4.6 System & status glyphs

Small, functional, appear across many screens. Keep them extra-legible at 16px.

| Name | Now | Meaning / where | Note |
|------|-----|-----------------|------|
| `warning` | ⚠ | Inline errors, field errors (TextField), alerts | Triangle-bang, `danger` tone. |
| `check` | ✓ | Met rule (PasswordChecklist), "verified" | `success` tone; also the ✓ in the verified badge. |
| `cross` | ✗ ✕ | Unmet rule (PasswordChecklist) | `muted`/`danger` tone. |
| `success` | ✅ | Confirmation notice (VerifyEmail "code sent") | Check-in-circle, `success` fill. |
| `envelope` | ✉️ | Verify-email banner | Rounded envelope; pairs with `warning` tone banner. |
| `chevron` | › | "Tap to go deeper" (rows, banners) | Simple right chevron. |
| `dot` | • | Neutral status ("email not verified" dot), bullets | Tiny filled circle. |

---

## 5. Summary — unique drawings to produce

Deduping shared concepts, the set is **~22 unique icons**:

```
Navigation/Hero : home · wardrobe(hanger+bow) · getready(outfit+sparkle) · beauty(lipstick) · profile
AI & capture    : ai-magic(sparkle) · camera · gallery · garment(placeholder)
Feedback        : love · dislike · save(bookmark) · favorite(heart)
Profile         : palette · privacy(lock) · info
System          : warning · check · cross · success · envelope · chevron · dot
```

Each ships as: **outline** + **filled** weight, **24px** and **48px**, SVG using
`currentColor`.

---

## 6. How they'll plug into the app

Recommended integration so swapping emoji → icon is one line per usage:

1. Add [`react-native-svg`](https://github.com/software-mansion/react-native-svg)
   (Expo-supported).
2. Drop each SVG into `mobile/src/components/icons/` as a component that takes
   `size` and `color` (defaulting `color` to `currentColor`/`colors.textPrimary`).
3. Add a single `<Icon name="..." />` dispatcher + an `IconName` union type, so
   screens reference icons by semantic name (`<Icon name="ai-magic" />`), mirroring
   how colors come from tokens.
4. Replace call sites:
   - **Tab bar** — swap the emoji map in `navigation/AppTabs.tsx` for `<Icon>` (outline inactive / filled active).
   - **Home & hero cards** — `ActionCard` takes an `icon` name instead of `emoji`.
   - **Buttons** — `Button`'s `icon?: string` becomes an icon name.
   - **Feedback, Profile rows, status glyphs** — direct `<Icon>` swaps.

Because everything is token-driven, an icon colored with `currentColor` inherits
the right dusty-pink automatically — no per-icon color hardcoding.

### Current emoji locations (for the find-and-replace pass)

- `navigation/AppTabs.tsx` — 5 tab icons
- `screens/HomeScreen.tsx` — 4 hero cards + `ai-magic`
- `screens/wardrobe/AddItemScreen.tsx` — camera, gallery, ai-magic, warning
- `screens/wardrobe/ConfirmItemScreen.tsx` — favorite, ai-magic
- `screens/wardrobe/WardrobeListScreen.tsx` — wardrobe, warning
- `screens/getready/GetReadyScreen.tsx` — ai-magic, love, dislike, save, warning
- `screens/beauty/BeautyScreen.tsx` — beauty, warning
- `screens/shop/ShopAssistantScreen.tsx` — shop, warning
- `screens/profile/ProfileScreen.tsx` — palette, save, privacy, info, check/dot
- `screens/auth/*` — ai-magic (EmptyState/Button), warning, success (VerifyEmail)
- `components/ui/` — `Button` (ai-magic), `ItemTile` (favorite, garment),
  `EmptyState` (ai-magic), `PasswordChecklist` (check/cross), `TextField`
  (warning), `VerifyBanner` (envelope, chevron)

---

## 7. Deliverables checklist (for the icon designer)

- [ ] 22 icons, **outline + filled**, on a 24px grid (2px safe margin).
- [ ] Exported as **SVG** using a single `currentColor` path where possible.
- [ ] Also **48px** exports for hero/onboarding use.
- [ ] Stroke ≈ 1.75px @24, rounded caps/joins, no gradients/shadows.
- [ ] Optical size normalized across the set.
- [ ] A one-page **contact sheet** showing all icons on `warmWhite` (`#FDF6F9`)
      and again in active `primaryDark` (`#D27C95`).
- [ ] Names match §4 exactly so they map 1:1 to `IconName`.

---

*Everything here derives from the live app and `mobile/src/theme/tokens.ts`.
When the palette changes in tokens, icons using `currentColor` follow for free.*
