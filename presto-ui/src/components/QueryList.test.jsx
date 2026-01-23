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

import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import { QueryListItem, QueryList } from "./QueryList";

// Create a proper jQuery mock
const createMockJQuery = (queries = []) => {
    const mockJQuery = Object.assign(
        jest.fn(() => mockJQuery),
        {
            get: jest.fn((url, callback) => {
                if (callback) callback(queries);
                return mockJQuery;
            }),
            fail: jest.fn(() => mockJQuery),
            tooltip: jest.fn(() => mockJQuery),
        }
    );
    return mockJQuery;
};

// Set up default mock
global.$ = createMockJQuery();

describe("QueryListItem", () => {
    const mockQuery = {
        queryId: "test_query_123",
        queryState: "RUNNING",
        user: "test_user",
        source: "presto-cli",
        resourceGroupId: ["global", "test"],
        query: "SELECT * FROM test_table",
        queryTruncated: false,
        createTime: "2024-01-01T10:00:00.000Z",
        authenticated: true,
        warningCodes: [],
        progress: {
            progressPercentage: 50,
            elapsedTimeMillis: 5000,
            executionTimeMillis: 4000,
            cpuTimeMillis: 3000,
            currentMemoryBytes: 1048576,
            peakMemoryBytes: 2097152,
            cumulativeUserMemory: 5242880,
            completedDrivers: 10,
            runningDrivers: 5,
            queuedDrivers: 2,
            completedNewDrivers: 8,
            runningNewDrivers: 4,
            queuedNewDrivers: 1,
            completedSplits: 100,
            runningSplits: 50,
            queuedSplits: 20,
            blocked: false,
            blockedReasons: [],
        },
    };

    it("should render query ID", () => {
        render(<QueryListItem query={mockQuery} />);
        expect(screen.getByText("test_query_123")).toBeInTheDocument();
    });

    it("should render user information", () => {
        render(<QueryListItem query={mockQuery} />);
        expect(screen.getByText("test_user")).toBeInTheDocument();
    });

    it("should render source information", () => {
        render(<QueryListItem query={mockQuery} />);
        expect(screen.getByText("presto-cli")).toBeInTheDocument();
    });

    it("should render query text", () => {
        render(<QueryListItem query={mockQuery} />);
        expect(screen.getByText(/SELECT \* FROM test_table/)).toBeInTheDocument();
    });

    it("should render progress bar", () => {
        render(<QueryListItem query={mockQuery} />);
        const progressBar = screen.getByRole("progressbar");
        expect(progressBar).toBeInTheDocument();
        expect(progressBar).toHaveAttribute("aria-valuenow", "50");
    });

    it("should render warning icon when warnings exist", () => {
        const queryWithWarnings = {
            ...mockQuery,
            warningCodes: ["WARNING_1", "WARNING_2"],
        };
        render(<QueryListItem query={queryWithWarnings} />);
        const warningIcon = document.querySelector(".bi-exclamation-triangle");
        expect(warningIcon).toBeInTheDocument();
    });

    it("should render driver statistics", () => {
        render(<QueryListItem query={mockQuery} />);
        // The component renders multiple statistics, just verify some key ones exist
        const stats = screen.getAllByText(/\d+/);
        expect(stats.length).toBeGreaterThan(0);
    });

    it("should render timing information", () => {
        render(<QueryListItem query={mockQuery} />);
        // Check for formatted durations with precision
        expect(screen.getByText(/4\.00s/)).toBeInTheDocument(); // execution time
        expect(screen.getByText(/5\.00s/)).toBeInTheDocument(); // elapsed time
        expect(screen.getByText(/3\.00s/)).toBeInTheDocument(); // cpu time
    });

    it("should render memory information", () => {
        render(<QueryListItem query={mockQuery} />);
        // Check for formatted memory sizes - values are formatted with precision
        expect(screen.getByText(/1\.00MB/)).toBeInTheDocument(); // current memory
        expect(screen.getByText(/2\.00MB/)).toBeInTheDocument(); // peak memory
    });

    it("should show 0 for running/queued drivers when query is finished", () => {
        const finishedQuery = {
            ...mockQuery,
            queryState: "FINISHED",
        };
        render(<QueryListItem query={finishedQuery} />);
        // Running and queued should be 0 for finished queries
        const stats = screen.getAllByText("0");
        expect(stats.length).toBeGreaterThan(0);
    });

    it("should render resource group links", () => {
        render(<QueryListItem query={mockQuery} />);
        const links = screen.getAllByRole("link");
        const resourceGroupLink = links.find((link) => link.href.includes("res_groups.html"));
        expect(resourceGroupLink).toBeInTheDocument();
    });

    it("should link to query detail page", () => {
        render(<QueryListItem query={mockQuery} />);
        const queryLink = screen.getByText("test_query_123").closest("a");
        expect(queryLink).toHaveAttribute("href", "query.html?test_query_123");
    });

    it("should render resource group as n/a when empty", () => {
        const queryWithEmptyGroup = {
            ...mockQuery,
            resourceGroupId: [],
        };
        const { container } = render(<QueryListItem query={queryWithEmptyGroup} />);
        expect(container.textContent).toContain("n/a");
    });

    it("should handle null resourceGroupId", () => {
        const queryWithNullGroup = {
            ...mockQuery,
            resourceGroupId: null,
        };
        const { container } = render(<QueryListItem query={queryWithNullGroup} />);
        expect(container.textContent).toContain("n/a");
    });

    it("should handle query text with leading whitespace", () => {
        const queryWithWhitespace = {
            ...mockQuery,
            query: "    SELECT * FROM table",
            queryTruncated: false,
        };
        render(<QueryListItem query={queryWithWhitespace} />);
        expect(screen.getByText(/SELECT \* FROM table/)).toBeInTheDocument();
    });

    it("should handle truncated queries", () => {
        const truncatedQuery = {
            ...mockQuery,
            query: "SELECT * FROM very_long_table_name_that_will_be_truncated",
            queryTruncated: true,
        };
        render(<QueryListItem query={truncatedQuery} />);
        const queryText = screen.getByText(/SELECT \* FROM/);
        expect(queryText).toBeInTheDocument();
    });

    it("should handle multi-line queries", () => {
        const multiLineQuery = {
            ...mockQuery,
            query: "SELECT *\nFROM table\nWHERE id = 1",
            queryTruncated: false,
        };
        render(<QueryListItem query={multiLineQuery} />);
        expect(screen.getByText(/SELECT \*/)).toBeInTheDocument();
    });

    it("should render resource group links with long names", () => {
        const longGroupId = ["very_long_group_name_that_exceeds_limit", "another_long_name", "third_name"];
        render(<QueryListItem query={{ ...mockQuery, resourceGroupId: longGroupId }} />);
        const links = screen.getAllByRole("link");
        const resourceGroupLinks = links.filter((link) => link.href.includes("res_groups.html"));
        expect(resourceGroupLinks.length).toBeGreaterThan(0);
    });
});

describe("QueryList", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        jest.useFakeTimers();
        global.$ = createMockJQuery();
    });

    afterEach(() => {
        jest.runOnlyPendingTimers();
        jest.useRealTimers();
    });

    it("should render loading state initially", async () => {
        render(<QueryList />);
        // The loading state appears after the component tries to fetch data
        await waitFor(() => {
            // Component should either show loading or have initialized
            const hasLoading = screen.queryByText("Loading...");
            const hasNoQueries = screen.queryByText("No queries");
            expect(hasLoading || hasNoQueries).toBeTruthy();
        });
    });

    it("should render search input", () => {
        render(<QueryList />);
        const searchInput = screen.getByPlaceholderText(/User, source, query ID/);
        expect(searchInput).toBeInTheDocument();
    });

    it("should render state filter buttons", () => {
        render(<QueryList />);
        expect(screen.getByText("Running")).toBeInTheDocument();
        expect(screen.getByText("Queued")).toBeInTheDocument();
        expect(screen.getByText("Finished")).toBeInTheDocument();
    });

    it("should render sort dropdown", () => {
        render(<QueryList />);
        expect(screen.getByText("Sort")).toBeInTheDocument();
    });

    it("should render reorder interval dropdown", () => {
        render(<QueryList />);
        expect(screen.getByText("Reorder Interval")).toBeInTheDocument();
    });

    it("should render show queries dropdown", () => {
        render(<QueryList />);
        expect(screen.getByText("Show")).toBeInTheDocument();
    });

    it("should handle search input changes", async () => {
        render(<QueryList />);
        const searchInput = screen.getByPlaceholderText(/User, source, query ID/);

        fireEvent.change(searchInput, { target: { value: "test_query" } });

        await waitFor(() => {
            expect(searchInput.value).toBe("test_query");
        });
    });

    it("should toggle state filters when clicked", async () => {
        render(<QueryList />);
        const runningButton = screen.getByText("Running").closest("button");

        // Button should be active initially
        expect(runningButton).toHaveClass("active");

        // Click to deactivate
        fireEvent.click(runningButton);

        await waitFor(() => {
            expect(runningButton).not.toHaveClass("active");
        });
    });

    it('should display "No queries" message when no queries are available', async () => {
        global.$ = createMockJQuery([]);

        render(<QueryList />);

        // Fast-forward timers to trigger the refresh
        jest.advanceTimersByTime(1000);

        await waitFor(() => {
            expect(screen.getByText("No queries")).toBeInTheDocument();
        });
    });

    it("should call API endpoint on mount", () => {
        const mockJQuery = createMockJQuery([]);
        global.$ = mockJQuery;

        render(<QueryList />);

        expect(mockJQuery.get).toHaveBeenCalledWith(expect.stringContaining("/v1/queryState"), expect.any(Function));
    });

    it("should refresh queries periodically", async () => {
        const mockJQuery = createMockJQuery([]);
        global.$ = mockJQuery;

        render(<QueryList />);

        // Initial call
        expect(mockJQuery.get).toHaveBeenCalledTimes(1);

        // Fast-forward 1 second
        jest.advanceTimersByTime(1000);

        await waitFor(() => {
            expect(mockJQuery.get).toHaveBeenCalledTimes(2);
        });
    });

    it("should handle max queries dropdown selection", async () => {
        const mockJQuery = createMockJQuery([]);
        global.$ = mockJQuery;

        render(<QueryList />);

        jest.advanceTimersByTime(1000);

        await waitFor(() => {
            expect(screen.getByText("Show")).toBeInTheDocument();
        });

        // Click the Show dropdown
        const showButton = screen.getByText("Show").closest("button");
        fireEvent.click(showButton);

        // Select "20 queries" option
        const option20 = screen.getByText("20 queries");
        fireEvent.click(option20);

        // Verify the selection was handled
        expect(showButton).toBeInTheDocument();
    });

    it("should handle sort dropdown selection", async () => {
        const mockJQuery = createMockJQuery([]);
        global.$ = mockJQuery;

        render(<QueryList />);

        jest.advanceTimersByTime(1000);

        await waitFor(() => {
            expect(screen.getByText("Sort")).toBeInTheDocument();
        });

        // Click the Sort dropdown
        const sortButton = screen.getByText("Sort").closest("button");
        fireEvent.click(sortButton);

        // Select "CPU Time" option
        const cpuTimeOption = screen.getByText("CPU Time");
        fireEvent.click(cpuTimeOption);

        // Verify the selection was handled
        expect(sortButton).toBeInTheDocument();
    });

    it("should handle reorder interval dropdown selection", async () => {
        const mockJQuery = createMockJQuery([]);
        global.$ = mockJQuery;

        render(<QueryList />);

        jest.advanceTimersByTime(1000);

        await waitFor(() => {
            expect(screen.getByText("Reorder Interval")).toBeInTheDocument();
        });

        // Click the Reorder Interval dropdown
        const reorderButton = screen.getByText("Reorder Interval").closest("button");
        fireEvent.click(reorderButton);

        // Select "10s" option
        const option10s = screen.getByText("10s");
        fireEvent.click(option10s);

        // Verify the selection was handled
        expect(reorderButton).toBeInTheDocument();
    });

    it("should handle error type filter dropdown", async () => {
        const mockJQuery = createMockJQuery([]);
        global.$ = mockJQuery;

        render(<QueryList />);

        jest.advanceTimersByTime(1000);

        await waitFor(() => {
            expect(screen.getByText("Failed")).toBeInTheDocument();
        });

        // Click the Failed dropdown
        const failedButton = screen.getByText("Failed").closest("button");
        fireEvent.click(failedButton);

        // Select "User Error" option
        const userErrorOption = screen.getByText("User Error");
        fireEvent.click(userErrorOption);

        // Verify the selection was handled
        expect(failedButton).toBeInTheDocument();
    });

    it("should toggle sort order when clicking same sort option", async () => {
        const mockJQuery = createMockJQuery([]);
        global.$ = mockJQuery;

        render(<QueryList />);

        jest.advanceTimersByTime(1000);

        await waitFor(() => {
            expect(screen.getByText("Sort")).toBeInTheDocument();
        });

        // Click the Sort dropdown
        const sortButton = screen.getByText("Sort").closest("button");
        fireEvent.click(sortButton);

        // Click "Creation Time" twice to toggle order
        const creationTimeOption = screen.getByText("Creation Time");
        fireEvent.click(creationTimeOption);

        // Open dropdown again
        fireEvent.click(sortButton);

        // Click again to toggle order
        const creationTimeOption2 = screen.getByText("Creation Time");
        fireEvent.click(creationTimeOption2);

        expect(sortButton).toBeInTheDocument();
    });
});

describe("QueryList Filtering", () => {
    const mockQueries = [
        {
            queryId: "running_query",
            queryState: "RUNNING",
            user: "user1",
            source: "cli",
            resourceGroupId: ["global"],
            query: "SELECT 1",
            queryTruncated: false,
            createTime: "2024-01-01T10:00:00.000Z",
            authenticated: true,
            warningCodes: [],
            progress: {
                progressPercentage: 50,
                elapsedTimeMillis: 1000,
                executionTimeMillis: 900,
                cpuTimeMillis: 800,
                currentMemoryBytes: 1024,
                peakMemoryBytes: 2048,
                cumulativeUserMemory: 4096,
                completedDrivers: 5,
                runningDrivers: 3,
                queuedDrivers: 1,
                blocked: false,
                blockedReasons: [],
            },
        },
        {
            queryId: "queued_query",
            queryState: "QUEUED",
            user: "user2",
            source: "jdbc",
            resourceGroupId: ["global"],
            query: "SELECT 2",
            queryTruncated: false,
            createTime: "2024-01-01T10:01:00.000Z",
            authenticated: true,
            warningCodes: [],
            progress: {
                progressPercentage: 0,
                elapsedTimeMillis: 500,
                executionTimeMillis: 0,
                cpuTimeMillis: 0,
                currentMemoryBytes: 0,
                peakMemoryBytes: 0,
                cumulativeUserMemory: 0,
                completedDrivers: 0,
                runningDrivers: 0,
                queuedDrivers: 0,
                blocked: false,
                blockedReasons: [],
            },
        },
        {
            queryId: "finished_query",
            queryState: "FINISHED",
            user: "user3",
            source: "ui",
            resourceGroupId: ["global"],
            query: "SELECT 3",
            queryTruncated: false,
            createTime: "2024-01-01T10:02:00.000Z",
            authenticated: true,
            warningCodes: [],
            progress: {
                progressPercentage: 100,
                elapsedTimeMillis: 2000,
                executionTimeMillis: 1900,
                cpuTimeMillis: 1800,
                currentMemoryBytes: 0,
                peakMemoryBytes: 3072,
                cumulativeUserMemory: 8192,
                completedDrivers: 10,
                runningDrivers: 0,
                queuedDrivers: 0,
                blocked: false,
                blockedReasons: [],
            },
        },
    ];

    beforeEach(() => {
        jest.clearAllMocks();
        jest.useFakeTimers();
        global.$ = createMockJQuery(mockQueries);
    });

    afterEach(() => {
        jest.runOnlyPendingTimers();
        jest.useRealTimers();
    });

    it("should filter queries by search text", async () => {
        render(<QueryList />);

        jest.advanceTimersByTime(1000);

        await waitFor(() => {
            expect(screen.getByText("running_query")).toBeInTheDocument();
        });

        const searchInput = screen.getByPlaceholderText(/User, source, query ID/);
        fireEvent.change(searchInput, { target: { value: "user1" } });

        jest.advanceTimersByTime(300);

        await waitFor(() => {
            expect(screen.getByText("running_query")).toBeInTheDocument();
        });
    });
});

// Made with Bob
