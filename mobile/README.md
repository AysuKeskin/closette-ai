# Closette Mobile (Expo + React Native)

The Closette AI app: wardrobe & beauty inventory, Get Ready looks, and a shop
assistant — in a dusty-pink design system driven by semantic tokens
(`src/theme/tokens.ts`).

## Run

```bash
cd mobile
npm install
npx expo start
```

Then press `i` (iOS simulator), `a` (Android emulator), or scan the QR code with
Expo Go on a physical device.

## Point the app at the backend

The API base URL resolves in this order (`src/api/client.ts`):

1. `EXPO_PUBLIC_API_BASE_URL` env var
2. `app.json` → `extra.apiBaseUrl`
3. Platform default — iOS simulator `http://localhost:8080`, Android emulator
   `http://10.0.2.2:8080`

For a **physical device**, set your machine's LAN IP:

```bash
EXPO_PUBLIC_API_BASE_URL=http://192.168.x.x:8080 npx expo start
```

## Structure

```
src/
  theme/        Design tokens (colors/spacing/radius/typography) — single source of truth
  components/ui Reusable primitives (Button, Card, Chip, TextField, Screen, ItemTile…)
  navigation/   Root ↔ Auth/Tabs, per-tab stacks (5-item bottom bar)
  screens/      auth · Home · wardrobe · beauty · getready · shop · profile
  api/          axios client (JWT + refresh), typed endpoints, DTO types
  features/     React Query hooks per domain
  store/        Auth/session (zustand + SecureStore)
```

## Flow A (photo → wardrobe)

`Wardrobe → + Add → Take/Choose photo → ✨ AI analysis → Confirm (editable) → Save`.
See `screens/wardrobe/AddItemScreen.tsx` and `ConfirmItemScreen.tsx`.
