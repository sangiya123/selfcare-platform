/**
 * Jest configuration for Selfcare App.
 *
 * Usage:
 *   npm test                    # Run all tests
 *   npm test -- --watch        # Watch mode
 *   npm test -- --coverage     # With coverage report
 *   npm test -- --testPathPattern=ConfigSDK  # Filter by file
 */
module.exports = {
  // RN >= 0.87 split the jest preset into its own package.
  preset: '@react-native/jest-preset',

  // Test environment
  testEnvironment: 'node',

  // Transform files
  transform: {
    '^.+\\.(js|jsx|ts|tsx)$': 'babel-jest',
  },

  // File patterns to ignore
  testPathIgnorePatterns: [
    '/node_modules/',
    '/android/',
    '/ios/',
    '/e2e/',        // E2E tests run with Detox, not Jest
    '/visual-regression/',
  ],

  // Module path aliases (must match tsconfig.json paths)
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
    '^@components/(.*)$': '<rootDir>/src/components/$1',
    '^@config/(.*)$': '<rootDir>/src/config/$1',
    '^@hooks/(.*)$': '<rootDir>/src/hooks/$1',
    '^@styles/(.*)$': '<rootDir>/src/styles/$1',
    '^@renderer/(.*)$': '<rootDir>/src/renderer/$1',
    '^@types/(.*)$': '<rootDir>/src/types/$1',
    // Mock native modules
    '^react-native-mmkv$': '<rootDir>/__mocks__/react-native-mmkv.ts',
    '^react-native-encrypted-storage$': '<rootDir>/__mocks__/react-native-encrypted-storage.ts',
    '^@react-native-async-storage/async-storage$': '<rootDir>/__mocks__/async-storage.ts',
    '^react-native-linking$': '<rootDir>/__mocks__/react-native-linking.ts',
  },

  // Setup files
  setupFilesAfterEnv: ['<rootDir>/jest.setup.ts'],

  // Test location
  roots: ['<rootDir>'],

  // Collect coverage from these directories
  collectCoverageFrom: [
    'src/**/*.{ts,tsx}',
    '!src/**/*.d.ts',
    '!src/**/index.ts',
  ],

  // Coverage thresholds
  coverageThreshold: {
    global: {
      branches: 60,
      functions: 60,
      lines: 60,
      statements: 60,
    },
  },

  // Test timeout (30 seconds for async operations)
  testTimeout: 30000,

  // Verbose output
  verbose: true,

  // Clear mocks between tests
  clearMocks: true,
  resetMocks: true,
  restoreMocks: true,
};
