/** @type {import('@stryker-mutator/api/core').PartialStrykerOptions} */
export default {
  testRunner: 'vitest',
  checkers: ['typescript'],
  tsconfigFile: 'tsconfig.app.json',
  mutate: [
    'src/**/*.{ts,tsx}',
    '!src/main.tsx',
    '!src/theme.ts',
    '!src/test/**',
  ],
  reporters: ['clear-text', 'html', 'json'],
  thresholds: { high: 80, low: 60, break: null },
};
