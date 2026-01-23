# Presto UI Testing Guide

This document provides information about the testing setup and how to run tests for the Presto UI.

## Testing Stack

The Presto UI uses the following testing tools:

- **Jest**: JavaScript testing framework
- **React Testing Library**: Testing utilities for React components
- **ts-jest**: TypeScript preprocessor for Jest
- **@testing-library/jest-dom**: Custom Jest matchers for DOM assertions

## Setup

All testing dependencies are already configured in `package.json`. To install them, run:

```bash
yarn install
```

## Running Tests

### Run all tests
```bash
yarn test
```

### Run tests in watch mode
```bash
yarn test:watch
```

### Run tests with coverage
```bash
yarn test:coverage
```

## Test Files

Test files are located alongside their source files with the following naming conventions:
- `*.test.ts` - TypeScript test files
- `*.test.tsx` - TypeScript React component test files
- `*.test.js` - JavaScript test files
- `*.test.jsx` - JavaScript React component test files

### Current Test Coverage

1. **utils.test.ts** - Comprehensive tests for utility functions including:
   - Query state color functions
   - Stage state color functions
   - Human-readable state formatting
   - Progress bar calculations
   - History management functions
   - String utilities
   - ID parsing functions
   - URL parsing functions
   - Numeric computations
   - Formatting functions (duration, data size, counts, dates)
   - Parsing functions (data size, duration)

2. **components/QueryList.test.jsx** - Tests for QueryList component including:
   - QueryListItem rendering
   - Query information display
   - Progress bars
   - Warning indicators
   - Driver and split statistics
   - Timing and memory information
   - Resource group links
   - Query filtering
   - Search functionality
   - State filters
   - Sort options
   - API integration

3. **components/SQLClient.test.tsx** - Tests for SQLClient component including:
   - Page rendering
   - Tab navigation (SQL and Session Properties)
   - SQL query execution
   - Loading states
   - Error handling
   - Session property management
   - Component integration

## Configuration Files

### jest.config.js
Main Jest configuration file that defines:
- Test environment (jsdom for browser simulation)
- File transformations (TypeScript and JavaScript)
- Module name mappings (CSS and asset mocks)
- Coverage thresholds
- Setup files

### jest.setup.js
Setup file that runs before each test suite:
- Imports @testing-library/jest-dom for custom matchers
- Mocks jQuery globally
- Mocks window.location
- Configures console output

### babel.config.js
Babel configuration for transforming JavaScript/JSX files in tests

### tsconfig.json
TypeScript configuration includes Jest types for proper type checking

## Writing Tests

### Example: Testing a Utility Function

```typescript
import { formatDuration } from './utils';

describe('formatDuration', () => {
    it('should format milliseconds', () => {
        expect(formatDuration(500)).toBe('500ms');
    });

    it('should format seconds', () => {
        expect(formatDuration(5000)).toBe('5s');
    });
});
```

### Example: Testing a React Component

```jsx
import { render, screen, fireEvent } from '@testing-library/react';
import MyComponent from './MyComponent';

describe('MyComponent', () => {
    it('should render button', () => {
        render(<MyComponent />);
        expect(screen.getByRole('button')).toBeInTheDocument();
    });

    it('should handle click events', () => {
        render(<MyComponent />);
        const button = screen.getByRole('button');
        fireEvent.click(button);
        // Assert expected behavior
    });
});
```

## Mocking

### Mocking jQuery
jQuery is mocked globally in `jest.setup.js`. You can override it in individual tests:

```javascript
global.$ = jest.fn(() => ({
    get: jest.fn((url, callback) => {
        callback(mockData);
        return { fail: jest.fn() };
    }),
}));
```

### Mocking Modules
Use `jest.mock()` to mock entire modules:

```javascript
jest.mock('./MyModule', () => ({
    myFunction: jest.fn(() => 'mocked value'),
}));
```

## Best Practices

1. **Test Behavior, Not Implementation**: Focus on what the component does, not how it does it
2. **Use Descriptive Test Names**: Test names should clearly describe what is being tested
3. **Arrange-Act-Assert**: Structure tests with clear setup, action, and assertion phases
4. **Mock External Dependencies**: Mock API calls, timers, and external modules
5. **Test Edge Cases**: Include tests for error conditions and boundary values
6. **Keep Tests Independent**: Each test should be able to run in isolation
7. **Use Testing Library Queries**: Prefer `getByRole`, `getByLabelText` over `getByTestId`

## Coverage Goals

The project aims for the following coverage thresholds:
- Branches: 50%
- Functions: 50%
- Lines: 50%
- Statements: 50%

These thresholds are configured in `jest.config.js` and can be adjusted as needed.

## Troubleshooting

### Tests fail with "Cannot find module"
- Ensure all dependencies are installed: `yarn install`
- Check that the module path is correct
- Verify the module is not excluded in `jest.config.js`

### TypeScript errors in test files
- Ensure `@types/jest` is installed
- Check that `tsconfig.json` includes `"types": ["jest"]`
- Run `yarn install` to update type definitions

### jQuery-related errors
- Check that jQuery is properly mocked in `jest.setup.js`
- Override the mock in your test if needed

### Timeout errors
- Increase Jest timeout: `jest.setTimeout(10000)`
- Use `waitFor` for async operations
- Check for infinite loops or unresolved promises

## Adding New Tests

When adding new components or utilities:

1. Create a test file with the same name as the source file plus `.test.ts(x)` or `.test.js(x)`
2. Import the component/function to test
3. Write describe blocks to group related tests
4. Write individual test cases with `it()` or `test()`
5. Run tests to ensure they pass
6. Check coverage with `npm run test:coverage`

## Continuous Integration

Tests should be run as part of the CI/CD pipeline:

```bash
# In CI environment
yarn install
yarn test --coverage --watchAll=false
```

## Resources

- [Jest Documentation](https://jestjs.io/docs/getting-started)
- [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/)
- [Testing Library Queries](https://testing-library.com/docs/queries/about)
- [Jest DOM Matchers](https://github.com/testing-library/jest-dom)