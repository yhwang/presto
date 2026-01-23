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
import SQLClientView from "./SQLClient";

// Mock the child components
jest.mock("./QueryResults", () => ({
    QueryResults: ({ results }: any) => (
        <div data-testid="query-results">
            {results?.error ? `Error: ${results.error.message}` : "Results displayed"}
        </div>
    ),
}));

jest.mock("./SessionProps", () => ({
    SessionProps: ({ show, changeHandler }: any) =>
        show ? (
            <div data-testid="session-props">
                <button onClick={() => changeHandler(["test_prop", "test_value", "default_value"])}>
                    Change Session
                </button>
            </div>
        ) : null,
}));

jest.mock("./SQLInput", () => ({
    SQLInput: ({ handleSQL, show, enabled, errorHandler }: any) =>
        show ? (
            <div data-testid="sql-input">
                <button
                    onClick={() => handleSQL("SELECT 1", "test_catalog", "test_schema")}
                    disabled={!enabled}
                    data-testid="execute-button"
                >
                    Execute
                </button>
                <button onClick={() => errorHandler(new Error("Test error"))} data-testid="error-button">
                    Trigger Error
                </button>
            </div>
        ) : null,
    createClient: jest.fn(() => ({
        query: jest.fn(() =>
            Promise.resolve({
                columns: [{ name: "col1", type: "varchar" }],
                data: [["value1"]],
            })
        ),
    })),
}));

jest.mock("./PageTitle", () => ({
    PageTitle: ({ titles, current }: any) => (
        <div data-testid="page-title">
            {titles.map((title: string, idx: number) => (
                <span key={idx} className={idx === current ? "active" : ""}>
                    {title}
                </span>
            ))}
        </div>
    ),
}));

describe("SQLClientView", () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it("should render page title", () => {
        render(<SQLClientView />);
        expect(screen.getByTestId("page-title")).toBeInTheDocument();
        expect(screen.getByText("SQL Client")).toBeInTheDocument();
    });

    it("should render warning alert", () => {
        render(<SQLClientView />);
        expect(screen.getByRole("alert")).toBeInTheDocument();
        expect(screen.getByText(/SQL client directly accesses the coordinator APIs/)).toBeInTheDocument();
    });

    it("should render SQL and Session tabs", () => {
        render(<SQLClientView />);
        expect(screen.getByText("SQL")).toBeInTheDocument();
        expect(screen.getByText("Session Properties")).toBeInTheDocument();
    });

    it("should show SQL input by default", () => {
        render(<SQLClientView />);
        expect(screen.getByTestId("sql-input")).toBeInTheDocument();
        expect(screen.queryByTestId("session-props")).not.toBeInTheDocument();
    });

    it("should switch to Session Properties view when clicked", () => {
        render(<SQLClientView />);

        const sessionTab = screen.getByText("Session Properties");
        fireEvent.click(sessionTab);

        expect(screen.getByTestId("session-props")).toBeInTheDocument();
        expect(screen.queryByTestId("sql-input")).not.toBeInTheDocument();
    });

    it("should switch back to SQL view when clicked", () => {
        render(<SQLClientView />);

        // Switch to Session Properties
        const sessionTab = screen.getByText("Session Properties");
        fireEvent.click(sessionTab);
        expect(screen.getByTestId("session-props")).toBeInTheDocument();

        // Switch back to SQL
        const sqlTab = screen.getByText("SQL");
        fireEvent.click(sqlTab);
        expect(screen.getByTestId("sql-input")).toBeInTheDocument();
        expect(screen.queryByTestId("session-props")).not.toBeInTheDocument();
    });

    it("should execute SQL query", async () => {
        render(<SQLClientView />);

        const executeButton = screen.getByTestId("execute-button");
        fireEvent.click(executeButton);

        await waitFor(() => {
            expect(screen.getByTestId("query-results")).toBeInTheDocument();
        });
    });

    it("should show loading state during query execution", async () => {
        const { createClient } = require("./SQLInput");

        // Mock a delayed query
        createClient.mockReturnValue({
            query: jest.fn(
                () =>
                    new Promise((resolve) => {
                        setTimeout(() => resolve({ columns: [], data: [] }), 100);
                    })
            ),
        });

        render(<SQLClientView />);

        const executeButton = screen.getByTestId("execute-button");
        fireEvent.click(executeButton);

        // Should show loading
        await waitFor(() => {
            expect(screen.getByText("Loading...")).toBeInTheDocument();
        });

        // Should hide loading after query completes
        await waitFor(
            () => {
                expect(screen.queryByText("Loading...")).not.toBeInTheDocument();
            },
            { timeout: 200 }
        );
    });

    it("should disable execute button during query execution", async () => {
        const { createClient } = require("./SQLInput");

        createClient.mockReturnValue({
            query: jest.fn(
                () =>
                    new Promise((resolve) => {
                        setTimeout(() => resolve({ columns: [], data: [] }), 100);
                    })
            ),
        });

        render(<SQLClientView />);

        const executeButton = screen.getByTestId("execute-button");
        expect(executeButton).not.toBeDisabled();

        fireEvent.click(executeButton);

        await waitFor(() => {
            expect(executeButton).toBeDisabled();
        });
    });

    it("should handle query errors", async () => {
        render(<SQLClientView />);

        const errorButton = screen.getByTestId("error-button");
        fireEvent.click(errorButton);

        await waitFor(() => {
            expect(screen.getByText(/Error: Test error/)).toBeInTheDocument();
        });
    });

    it("should handle session property changes", () => {
        render(<SQLClientView />);

        // Switch to Session Properties
        const sessionTab = screen.getByText("Session Properties");
        fireEvent.click(sessionTab);

        const changeButton = screen.getByText("Change Session");
        fireEvent.click(changeButton);

        // Session should be updated (no error thrown)
        expect(screen.getByTestId("session-props")).toBeInTheDocument();
    });

    it("should have active class on current tab", () => {
        render(<SQLClientView />);

        const sqlTab = screen.getByText("SQL").closest("a");
        const sessionTab = screen.getByText("Session Properties").closest("a");

        expect(sqlTab).toHaveClass("active");
        expect(sessionTab).not.toHaveClass("active");

        fireEvent.click(sessionTab!);

        expect(sqlTab).not.toHaveClass("active");
        expect(sessionTab).toHaveClass("active");
    });

    it("should render documentation links", () => {
        render(<SQLClientView />);

        const links = screen.getAllByRole("link");
        const docLinks = links.filter((link) => link.getAttribute("href")?.includes("prestodb.io"));

        expect(docLinks.length).toBeGreaterThan(0);
    });

    it("should have dismissible alert", () => {
        render(<SQLClientView />);

        const closeButton = screen.getByLabelText("Close");
        expect(closeButton).toBeInTheDocument();
        expect(closeButton).toHaveAttribute("data-bs-dismiss", "alert");
    });

    it("should maintain SQL input state across tab switches", () => {
        render(<SQLClientView />);

        // Execute a query
        const executeButton = screen.getByTestId("execute-button");
        fireEvent.click(executeButton);

        // Switch to Session Properties
        const sessionTab = screen.getByText("Session Properties");
        fireEvent.click(sessionTab);

        // Switch back to SQL
        const sqlTab = screen.getByText("SQL");
        fireEvent.click(sqlTab);

        // SQL input should still be there
        expect(screen.getByTestId("sql-input")).toBeInTheDocument();
    });
});

describe("SQLClientView Integration", () => {
    it("should render all child components correctly", () => {
        render(<SQLClientView />);

        // Verify that all main components are rendered
        expect(screen.getByTestId("page-title")).toBeInTheDocument();
        expect(screen.getByTestId("sql-input")).toBeInTheDocument();
        expect(screen.getByRole("alert")).toBeInTheDocument();
    });

    it("should integrate session properties with SQL execution", () => {
        render(<SQLClientView />);

        // Switch to Session Properties and make a change
        const sessionTab = screen.getByText("Session Properties");
        fireEvent.click(sessionTab);

        const changeButton = screen.getByText("Change Session");
        fireEvent.click(changeButton);

        // Switch back to SQL tab
        const sqlTab = screen.getByText("SQL");
        fireEvent.click(sqlTab);

        // Execute query - session changes should be applied
        const executeButton = screen.getByTestId("execute-button");
        fireEvent.click(executeButton);

        // Verify no errors occurred
        expect(screen.queryByText(/Error:/)).not.toBeInTheDocument();
    });
});

// Made with Bob
