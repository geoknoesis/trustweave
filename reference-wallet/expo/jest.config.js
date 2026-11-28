/** @type {import('jest').Config} */
const moduleNameMapper = {}
try {
  require.resolve('@noble/hashes/sha2.js')
} catch {
  moduleNameMapper['^@noble/hashes/sha2\\.js$'] = '@noble/hashes/sha2'
}

module.exports = {
  preset: 'jest-expo',
  testMatch: ['**/__tests__/**/*.test.ts?(x)'],
  moduleNameMapper,
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|@react-native(-community)?)|expo(nent)?|@expo(nent)?/.*|@expo-google-fonts/.*|react-navigation|@react-navigation/.*|@sentry/react-native|native-base|react-native-svg|@noble/.*|bs58|base-x)',
  ],
}
