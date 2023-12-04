// https://www.mohammadfaisal.dev/blog/how-to-add-linting-and-formatting-for-your-react-app
module.exports = {
  root: true,
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    ecmaFeatures: {
      jsx: true,
      classes: true
    }
  },
  plugins: ['react', 'prettier'],
  settings: {
    react: {
      version: 'detect'
    }
  },
  env: {
    jest: true,
    browser: true,
    amd: true,
    node: true
  },
  extends: [
    'plugin:@typescript-eslint/recommended',
    'eslint:recommended',
    'plugin:react/recommended',
    'plugin:prettier/recommended' // Make this the last element so prettier config overrides other formatting rules
  ],
  rules: {
    '@typescript-eslint/no-non-null-assertion': ['off'],
    '@typescript-eslint/no-explicit-any': ['off'],
    'no-unused-vars': ['error', { vars: 'all', args: 'after-used', ignoreRestSiblings: false }],
    'prettier/prettier': ['error', {}, { usePrettierrc: true }]
  }
}
