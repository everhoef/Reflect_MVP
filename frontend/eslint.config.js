import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'
import importPlugin from 'eslint-plugin-import'

const isBoundaryCheck = process.env.LINT_BOUNDARIES === 'true';

const baseConfig = [
  globalIgnores(['dist', 'src/types/sse-schemas/**', 'src/generated/**', 'node_modules', 'src/types/generated/**']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
  },
];

const boundaryConfig = [
  globalIgnores(['dist', 'src/types/sse-schemas/**', 'src/generated/**', 'node_modules', 'src/types/generated/**']),
  {
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    plugins: {
      import: importPlugin,
      'react-refresh': reactRefresh
    },
    languageOptions: {
      parser: tseslint.parser,
    },
    settings: {
      'import/resolver': {
        typescript: {
          alwaysTryTypes: true,
          project: './tsconfig.app.json'
        }
      }
    },
    rules: {
      'react-refresh/only-export-components': 'off',
      'import/no-restricted-paths': ['error', {
        zones: [
          { target: './src/shared', from: './src/modules', message: 'Shared code cannot import from feature modules.' },
          { target: './src/shared', from: './src/app', message: 'Shared code cannot import from app layer.' },

          { target: './src/modules', from: './src/app', message: 'Feature modules cannot import from the app layer.' },

          { target: './src/modules/auth', from: ['./src/modules/facilitation', './src/modules/organization'], message: 'Auth module cannot import from facilitation or organization modules.' },

          { target: './src/modules/facilitation', from: './src/modules/organization', message: 'Facilitation module cannot import from organization module.' },

          { target: './src/modules/organization', from: './src/modules/facilitation', message: 'Organization module cannot import from facilitation module.' }
        ]
      }]
    }
  }
];

export default defineConfig(isBoundaryCheck ? boundaryConfig : baseConfig);
