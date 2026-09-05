/**
 * Detox Environment Configuration
 */

const { Environment } = require('detox/environment');

class CustomDetoxEnvironment extends Environment {
  constructor(config) {
    super(config);
    this.initTimeout = 120000; // 2 minutes for CI
  }

  async beforeEach() {
    await super.beforeEach();
  }

  async afterEach() {
    await super.afterEach();
  }
}

module.exports = { Default: CustomDetoxEnvironment };
