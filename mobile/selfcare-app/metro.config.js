/**
 * Metro config — enables custom path aliases for both dev and bundle.
 *
 * Run the bundler with:
 *   npx react-native start --reset-cache
 */

const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

const config = {
  resolver: {
    extraNodeModules: {
      '@': require('path').resolve(__dirname, 'src'),
    },
    resolveRequest: (context, moduleName, platform) => {
      // Map '@' alias to src/
      if (moduleName.startsWith('@/')) {
        return context.resolveRequest(
          context,
          moduleName.replace(/^@\//, require('path').resolve(__dirname, 'src') + '/'),
          platform
        );
      }
      if (moduleName.startsWith('@components/')) {
        return context.resolveRequest(
          context,
          moduleName.replace(/^@components\//, require('path').resolve(__dirname, 'src/components') + '/'),
          platform
        );
      }
      if (moduleName.startsWith('@config/')) {
        return context.resolveRequest(
          context,
          moduleName.replace(/^@config\//, require('path').resolve(__dirname, 'src/config') + '/'),
          platform
        );
      }
      if (moduleName.startsWith('@hooks/')) {
        return context.resolveRequest(
          context,
          moduleName.replace(/^@hooks\//, require('path').resolve(__dirname, 'src/hooks') + '/'),
          platform
        );
      }
      if (moduleName.startsWith('@styles/')) {
        return context.resolveRequest(
          context,
          moduleName.replace(/^@styles\//, require('path').resolve(__dirname, 'src/styles') + '/'),
          platform
        );
      }
      return context.resolveRequest(context, moduleName, platform);
    },
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
