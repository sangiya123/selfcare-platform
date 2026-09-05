/**
 * Babel config — uses React Native preset with module-resolver for path aliases.
 *
 * The path aliases must mirror tsconfig.json paths.
 */

module.exports = {
  presets: ['module:@react-native/babel-preset'],
  plugins: [
    [
      'module-resolver',
      {
        root: ['./src'],
        alias: {
          '@': './src',
          '@components': './src/components',
          '@config': './src/config',
          '@hooks': './src/hooks',
          '@styles': './src/styles',
          '@screens': './src/screens',
          '@utils': './src/utils',
        },
        extensions: ['.ts', '.tsx', '.js', '.jsx', '.json'],
      },
    ],
  ],
};
