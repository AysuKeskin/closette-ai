# Onboarding — Aesthetic Card Illustrations

Art brief for the 8 swipe-deck cards in the style onboarding (`OnboardingScreen`,
the Tinder-style "Which looks are you?" step). Each aesthetic needs one
illustration. The user swipes **right = love**, **left = nope**, and the loved
cards' style tags feed recommendations.

---

## Style (prepend to every prompt)

> *"A cute flat illustration of **[SUBJECT]**, thick rounded dark-charcoal
> (#2C2429) outline, soft shading, small white glossy highlights, centered on a
> plain white (or transparent) background, modern feminine fashion-app style,
> 1024×1024, no text."*

Match each card's **own palette** (not the app's pink) so the illustration reads
as that aesthetic. A full figure OR just the outfit (on a hanger / flat-lay) both
work — just keep all 8 in the **same** style.

---

## The 8 cards

| File name | Aesthetic | `[SUBJECT]` prompt | Palette (use these tones) |
|-----------|-----------|--------------------|---------------------------|
| `cleangirl` | Clean Girl | a woman with a slicked-back bun, minimal gold hoop earrings, wearing a neutral tank top and tailored trousers | cream · tan · grey · black |
| `oldmoney` | Old Money | an elegant outfit — a camel blazer over a white shirt with navy trousers and loafers | camel `#C19A6B` · navy `#22314E` · cream · burgundy |
| `coquette` | Coquette | a soft feminine outfit with a bow — a pastel pink dress with lace trim and a hair ribbon | dusty pink `#D9A6AF` · blush · cream · lavender |
| `street` | Street | an urban streetwear look — oversized hoodie, cargo pants, chunky sneakers and a cap | black · grey · white · red pop |
| `boho` | Boho | a free-spirited boho look — a flowy earthy-toned maxi dress with layered necklaces and a wide-brim hat | camel · olive `#6B6B3A` · cream · mustard |
| `edgy` | Edgy | an edgy look — a black leather jacket, black jeans and combat boots | black · burgundy `#5E2233` · charcoal · silver |
| `minimalist` | Minimalist | a minimalist monochrome outfit — a structured black top and white wide-leg trousers, clean lines | black · white · grey · beige |
| `romantic` | Romantic | a romantic dreamy look — a soft floral midi dress with puff sleeves | dusty pink · lavender · cream · rose `#C97B84` |

---

## Deliverables — ✅ done

- [x] 8 illustrations delivered (full-figure, transparent background).
- [x] Same flat + charcoal-outline style across all 8.
- [x] Each in its own aesthetic's palette.
- [x] Copied to `mobile/src/assets/aesthetics/<name>.png` (names below).

## Where they go — ✅ wired

Live in `mobile/src/assets/aesthetics/` and shown large (300pt tall) at the top
of each swipe card in `OnboardingScreen`, with the aesthetic name + vibe +
palette swatches below. `require()`d in the `AESTHETICS` array. Tags per card:

- Clean Girl → minimal, chic
- Old Money → classic, timeless, preppy
- Coquette → feminine, romantic
- Street → streetwear, edgy, sporty
- Boho → boho, romantic
- Edgy → edgy
- Minimalist → minimal
- Romantic → feminine, romantic, elegant

*Everything here derives from the live `OnboardingScreen`. See also
`docs/ICONOGRAPHY.md` for the app's icon set.*
