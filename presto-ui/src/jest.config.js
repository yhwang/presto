/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

module.exports = {
    preset: "ts-jest",
    testEnvironment: "jsdom",
    roots: ["<rootDir>"],
    testMatch: ["**/__tests__/**/*.+(ts|tsx|js|jsx)", "**/?(*.)+(spec|test).+(ts|tsx|js|jsx)"],
    transform: {
        "^.+\\.(ts|tsx)$": "ts-jest",
        "^.+\\.(js|jsx)$": ["babel-jest", { configFile: "./babel.config.js" }],
    },
    moduleFileExtensions: ["ts", "tsx", "js", "jsx", "json", "node"],
    setupFilesAfterEnv: ["<rootDir>/jest.setup.js"],
    moduleNameMapper: {
        "\\.(css|less|scss|sass)$": "<rootDir>/__mocks__/styleMock.js",
        "\\.(jpg|jpeg|png|gif|svg)$": "<rootDir>/__mocks__/fileMock.js",
    },
    collectCoverageFrom: [
        "**/*.{ts,tsx,js,jsx}",
        "!**/node_modules/**",
        "!**/vendor/**",
        "!**/dist/**",
        "!**/sql-parser/**",
        "!**/*.config.js",
        "!**/coverage/**",
        "!**/__tests__/**",
        "!**/__mocks__/**",
    ],
    coverageThreshold: {
        global: {
            branches: 50,
            functions: 50,
            lines: 50,
            statements: 50,
        },
    },
    globals: {
        "ts-jest": {
            tsconfig: {
                jsx: "react",
                esModuleInterop: true,
                allowSyntheticDefaultImports: true,
            },
        },
    },
};

// Made with Bob
