// polyfills.ts
//
// React Native lacks Web Crypto. Imports here run BEFORE any application code via
// `expo-router/entry` (which evaluates this file at the top of `app/_layout.tsx`'s
// module-load order — we re-export from there).
//
// `react-native-get-random-values` shims `crypto.getRandomValues`. @noble/ed25519
// uses that for `randomPrivateKey()`; without the shim, ed25519 keygen throws.

import 'react-native-get-random-values'
