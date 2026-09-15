/**
 * Detox E2E Configuration
 *
 * Setup:
 *   npm run build:e2e          # Build the app
 *   npm run test:e2e           # Run E2E tests
 *   npm run test:e2e:ios        # Run on iOS
 *   npm run test:e2e:android    # Run on Android
 *
 * Files:
 *   e2e/*.e2e.ts  — test specs
 *   e2e/before-each.ts — shared setup
 */

module.exports = {
  // Test specs
  specs: ['e2e/**/*.e2e.ts'],

  // Default timeout for waiting for elements
  waitForIdleTimeout: 2000,

  // Detox logging
  logger: {
    suppressCapture: false,
    suppressHighlight: false,
  },

  // ----------------------------------------
  // iOS Configuration
  // ----------------------------------------
  ios: {
    debug: {
      type: 'ios.simulator',
      device: {
        id: process.env.DETOX_IOS_DEVICE_ID || 'iPhone 16 Pro',
      },
      app: 'ios/build/Build/Products/Debug-iphonesimulator/selfcareSelfcare.app',
      bundleId: 'com.selfcare.selfcare',
      language: 'en',
      locale: 'US',
    },
    release: {
      type: 'ios.device',
      device: {
        id: process.env.DETOX_IOS_DEVICE_ID || 'auto',
        type: 'iPhone',
      },
      app: 'ios/build/Build/Products/Release-iphoneos/selfcareSelfcare.app',
      bundleId: 'com.selfcare.selfcare',
      language: 'en',
      locale: 'US',
    },
  },

  // ----------------------------------------
  // Android Configuration
  // ----------------------------------------
  android: {
    debug: {
      type: 'android.emulator',
      device: {
        apiLevel: process.env.ANDROID_API_LEVEL || 34,
        id: process.env.ANDROID_EMULATOR_ID || 'emulator-5554',
        avdName: process.env.AVD_NAME || 'SELFCARE_API_34',
      },
      app: 'android/app/build/outputs/apk/debug/app-debug.apk',
      package: 'com.selfcare.selfcare',
      language: 'en',
      locale: 'US',
    },
    release: {
      type: 'android.device',
      device: {
        id: process.env.ANDROID_DEVICE_ID || 'auto',
      },
      app: 'android/app/build/outputs/apk/release/app-release.apk',
      package: 'com.selfcare.selfcare',
      language: 'en',
      locale: 'US',
    },
  },

  // ----------------------------------------
  // Test runner configuration
  // ----------------------------------------
  testRunner: 'jest-circus',

  runnerConfig: {
    testEnvironment: './e2e/environment.js',
    setupFilesAfterEnv: ['./e2e/before-each.ts'],
    jest: {
      preset: 'react-native',
      moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node'],
      transformIgnorePatterns: [
        'node_modules/(?!(react-native|@react-native|@react-navigation|react-native-reanimated|@react-native-community)/)',
      ],
    },
  },
};
