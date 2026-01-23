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

import {
    getQueryStateColor,
    getStageStateColor,
    getHumanReadableState,
    getProgressBarPercentage,
    getProgressBarTitle,
    isQueryEnded,
    addToHistory,
    addExponentiallyWeightedToHistory,
    truncateString,
    getStageNumber,
    getTaskIdSuffix,
    getTaskNumber,
    getFirstParameter,
    getHostname,
    getPort,
    getHostAndPort,
    computeRate,
    precisionRound,
    formatDuration,
    formatRows,
    formatCount,
    formatDataSizeBytes,
    formatDataSize,
    parseDataSize,
    parseDuration,
    formatShortTime,
    formatShortDateTime,
    removeNodeTypePackage,
} from "./utils";

// Expected color constants for test assertions
const EXPECTED_COLORS = {
    QUEUED: "#1b8f72",
    RUNNING: "#19874e",
    BLOCKED: "#61003b",
    FINISHED: "#1a4629",
    USER_ERROR: "#9a7d66",
    USER_CANCELED: "#858959",
    EXTERNAL: "#ca7640",
    INSUFFICIENT_RESOURCES: "#7f5b72",
    UNKNOWN_ERROR: "#943524",
} as const;

type QueryStateColorTestCase = [string, boolean, string, string, string, string];
type StageStateColorTestCase = [{ state: string; stageStats?: { fullyBlocked: boolean } }, string, string];
type HumanReadableStateTestCase = [string, boolean, boolean, string[], string, string, string, string];
type QueryEndedTestCase = [string, boolean, string];

const QUERY_STATE_COLOR_TEST_CASES: QueryStateColorTestCase[] = [
    ["QUEUED", false, "", "", EXPECTED_COLORS.QUEUED, "QUEUED state"],
    ["RUNNING", false, "", "", EXPECTED_COLORS.RUNNING, "RUNNING state"],
    ["RUNNING", true, "", "", EXPECTED_COLORS.BLOCKED, "BLOCKED state"],
    ["FINISHED", false, "", "", EXPECTED_COLORS.FINISHED, "FINISHED state"],
    ["FAILED", false, "USER_ERROR", "", EXPECTED_COLORS.USER_ERROR, "USER_ERROR"],
    ["FAILED", false, "USER_ERROR", "USER_CANCELED", EXPECTED_COLORS.USER_CANCELED, "USER_CANCELED"],
    ["FAILED", false, "EXTERNAL", "", EXPECTED_COLORS.EXTERNAL, "EXTERNAL error"],
    ["FAILED", false, "INSUFFICIENT_RESOURCES", "", EXPECTED_COLORS.INSUFFICIENT_RESOURCES, "INSUFFICIENT_RESOURCES"],
    ["FAILED", false, "UNKNOWN", "", EXPECTED_COLORS.UNKNOWN_ERROR, "unknown error"],
];

const STAGE_STATE_COLOR_TEST_CASES: StageStateColorTestCase[] = [
    [{ state: "PLANNED" }, EXPECTED_COLORS.QUEUED, "PLANNED stage"],
    [{ state: "RUNNING", stageStats: { fullyBlocked: false } }, EXPECTED_COLORS.RUNNING, "RUNNING stage"],
    [{ state: "RUNNING", stageStats: { fullyBlocked: true } }, EXPECTED_COLORS.BLOCKED, "BLOCKED stage"],
    [{ state: "FINISHED" }, EXPECTED_COLORS.FINISHED, "FINISHED stage"],
    [{ state: "FAILED" }, EXPECTED_COLORS.UNKNOWN_ERROR, "FAILED stage"],
];

const HUMAN_READABLE_STATE_TEST_CASES: HumanReadableStateTestCase[] = [
    ["RUNNING", false, false, [], "", "", "RUNNING", "running query"],
    ["RUNNING", true, true, [], "", "", "BLOCKED", "blocked query"],
    [
        "RUNNING",
        true,
        true,
        ["WAITING_FOR_MEMORY"],
        "",
        "",
        "BLOCKED (WAITING_FOR_MEMORY)",
        "blocked query with reasons",
    ],
    ["FAILED", false, false, [], "USER_ERROR", "USER_CANCELED", "USER CANCELED", "user canceled query"],
    ["FAILED", false, false, [], "USER_ERROR", "", "USER ERROR", "user error"],
    ["FAILED", false, false, [], "INTERNAL_ERROR", "", "INTERNAL ERROR", "internal error"],
    ["FAILED", false, false, [], "INSUFFICIENT_RESOURCES", "", "INSUFFICIENT RESOURCES", "resource error"],
    ["FAILED", false, false, [], "EXTERNAL", "", "EXTERNAL ERROR", "external error"],
];

const QUERY_ENDED_TEST_CASES: QueryEndedTestCase[] = [
    ["FINISHED", true, "FINISHED"],
    ["FAILED", true, "FAILED"],
    ["CANCELED", true, "CANCELED"],
    ["RUNNING", false, "RUNNING"],
];

function testQueryStateColor(): void {
    describe("getQueryStateColor", () => {
        test.each(QUERY_STATE_COLOR_TEST_CASES)(
            "should return correct color for %s",
            (state, isBlocked, errorType, cancelType, expectedColor) => {
                expect(getQueryStateColor(state, isBlocked, errorType, cancelType)).toBe(expectedColor);
            }
        );
    });
}

function testStageStateColor(): void {
    describe("getStageStateColor", () => {
        test.each(STAGE_STATE_COLOR_TEST_CASES)("should return correct color for %s", (stage, expectedColor) => {
            expect(getStageStateColor(stage)).toBe(expectedColor);
        });
    });
}

function testHumanReadableState(): void {
    describe("getHumanReadableState", () => {
        test.each(HUMAN_READABLE_STATE_TEST_CASES)(
            "should return %s for %s",
            (state, scheduled, blocked, reasons, memoryPool, errorType, cancelType, expected) => {
                expect(
                    getHumanReadableState(state, scheduled, blocked, reasons, memoryPool, errorType, cancelType)
                ).toBe(expected);
            }
        );
    });
}

function testProgressBarPercentage(): void {
    describe("getProgressBarPercentage", () => {
        it("should return 100 for non-running queries", () => {
            expect(getProgressBarPercentage(50, "FINISHED")).toBe(100);
        });

        it("should return 100 when progress is not provided", () => {
            expect(getProgressBarPercentage(0, "RUNNING")).toBe(100);
        });

        it("should return rounded progress for running queries", () => {
            expect(getProgressBarPercentage(45.7, "RUNNING")).toBe(46);
        });
    });
}

function testProgressBarTitle(): void {
    describe("getProgressBarTitle", () => {
        it("should return state with percentage for running queries", () => {
            expect(getProgressBarTitle(50, "RUNNING", "RUNNING")).toBe("RUNNING (50%)");
        });

        it("should return only state for non-running queries", () => {
            expect(getProgressBarTitle(50, "FINISHED", "FINISHED")).toBe("FINISHED");
        });
    });
}

function testQueryEnded(): void {
    describe("isQueryEnded", () => {
        test.each(QUERY_ENDED_TEST_CASES)("should return %s for %s state", (state, expected) => {
            expect(isQueryEnded(state)).toBe(expected);
        });
    });
}

export function describeQueryStateFunctions(): void {
    testQueryStateColor();
    testStageStateColor();
    testHumanReadableState();
    testProgressBarPercentage();
    testProgressBarTitle();
    testQueryEnded();
}

describe("History Functions", () => {
    describe("addToHistory", () => {
        it("should add value to empty array", () => {
            expect(addToHistory(10, [])).toEqual([10]);
        });

        it("should add value to existing array", () => {
            expect(addToHistory(20, [10])).toEqual([10, 20]);
        });

        it("should limit history to MAX_HISTORY", () => {
            const largeArray = new Array(300).fill(1);
            const result = addToHistory(2, largeArray);
            expect(result.length).toBeLessThanOrEqual(301);
        });
    });

    describe("addExponentiallyWeightedToHistory", () => {
        it("should add value to empty array", () => {
            expect(addExponentiallyWeightedToHistory(10, [])).toEqual([10]);
        });

        it("should add weighted average to existing array", () => {
            const result = addExponentiallyWeightedToHistory(10, [5]);
            expect(result.length).toBe(2);
            expect(result[1]).toBeGreaterThan(5);
            expect(result[1]).toBeLessThan(10);
        });

        it("should return 0 for values less than 1", () => {
            const result = addExponentiallyWeightedToHistory(0.5, [10]);
            expect(result[1]).toBe(0);
        });
    });
});

describe("String Utility Functions", () => {
    describe("truncateString", () => {
        it("should not truncate short strings", () => {
            expect(truncateString("hello", 10)).toBe("hello");
        });

        it("should truncate long strings", () => {
            expect(truncateString("hello world", 5)).toBe("hello...");
        });

        it("should handle undefined input", () => {
            expect(truncateString(undefined as any, 5)).toBeUndefined();
        });
    });

    describe("removeNodeTypePackage", () => {
        it("should remove package from node type", () => {
            expect(removeNodeTypePackage("com.facebook.presto.spi.plan.OutputNode")).toBe("OutputNode");
        });

        it("should handle simple class names", () => {
            expect(removeNodeTypePackage("OutputNode")).toBe("OutputNode");
        });
    });
});

describe("ID Parsing Functions", () => {
    describe("getStageNumber", () => {
        it("should extract stage number from stage ID", () => {
            expect(getStageNumber("query.1")).toBe(1);
        });

        it("should handle multi-digit stage numbers", () => {
            expect(getStageNumber("query.123")).toBe(123);
        });
    });

    describe("getTaskIdSuffix", () => {
        it("should extract suffix from task ID", () => {
            expect(getTaskIdSuffix("query.1.2")).toBe("1.2");
        });
    });

    describe("getTaskNumber", () => {
        it("should extract task number from task ID", () => {
            expect(getTaskNumber("query.1.5")).toBe(5);
        });
    });

    describe("getFirstParameter", () => {
        it("should extract first parameter from search string", () => {
            expect(getFirstParameter("?param1=value1&param2=value2")).toBe("param1=value1");
        });

        it("should return entire string if no ampersand", () => {
            expect(getFirstParameter("?param1=value1")).toBe("param1=value1");
        });
    });
});

describe("URL Parsing Functions", () => {
    describe("getHostname", () => {
        it("should extract hostname from URL", () => {
            expect(getHostname("http://localhost:8080/path")).toBe("localhost");
        });

        it("should handle IPv6 addresses", () => {
            expect(getHostname("http://[::1]:8080/path")).toBe("::1");
        });
    });

    describe("getPort", () => {
        it("should extract port from URL", () => {
            expect(getPort("http://localhost:8080/path")).toBe("8080");
        });
    });

    describe("getHostAndPort", () => {
        it("should extract host and port from URL", () => {
            expect(getHostAndPort("http://localhost:8080/path")).toBe("localhost:8080");
        });
    });
});

describe("Numeric Functions", () => {
    describe("computeRate", () => {
        it("should compute rate correctly", () => {
            expect(computeRate(100, 1000)).toBe(100);
        });

        it("should return 0 for zero milliseconds", () => {
            expect(computeRate(100, 0)).toBe(0);
        });
    });

    describe("precisionRound", () => {
        it("should return empty string for undefined", () => {
            expect(precisionRound(undefined as any)).toBe("");
        });

        it("should format small numbers with 2 decimals", () => {
            expect(precisionRound(5.678)).toBe("5.68");
        });

        it("should format medium numbers with 1 decimal", () => {
            expect(precisionRound(56.78)).toBe("56.8");
        });

        it("should format large numbers without decimals", () => {
            expect(precisionRound(567.89)).toBe("568");
        });
    });
});

describe("Formatting Functions", () => {
    describe("formatDuration", () => {
        it("should format milliseconds", () => {
            expect(formatDuration(500)).toBe("500ms");
        });

        it("should format seconds", () => {
            expect(formatDuration(5000)).toBe("5.00s");
        });

        it("should format minutes", () => {
            expect(formatDuration(120000)).toBe("2.00m");
        });

        it("should format hours", () => {
            expect(formatDuration(7200000)).toBe("2.00h");
        });

        it("should format days", () => {
            expect(formatDuration(172800000)).toBe("2.00d");
        });

        it("should format weeks", () => {
            expect(formatDuration(1209600000)).toBe("2.00w");
        });
    });

    describe("formatRows", () => {
        it("should format single row", () => {
            expect(formatRows(1)).toBe("1 row");
        });

        it("should format multiple rows", () => {
            expect(formatRows(100)).toBe("100 rows");
        });

        it("should format large row counts", () => {
            expect(formatRows(1500)).toBe("1.50K rows");
        });
    });

    describe("formatCount", () => {
        it("should format small counts", () => {
            expect(formatCount(500)).toBe("500");
        });

        it("should format thousands", () => {
            expect(formatCount(1500)).toBe("1.50K");
        });

        it("should format millions", () => {
            expect(formatCount(1500000)).toBe("1.50M");
        });

        it("should format billions", () => {
            expect(formatCount(1500000000)).toBe("1.50B");
        });
    });

    describe("formatDataSize", () => {
        it("should format bytes", () => {
            expect(formatDataSize(0)).toBe("0B");
        });

        it("should format kilobytes", () => {
            expect(formatDataSize(1536)).toBe("1.50KB");
        });

        it("should format megabytes", () => {
            expect(formatDataSize(1572864)).toBe("1.50MB");
        });

        it("should format gigabytes", () => {
            expect(formatDataSize(1610612736)).toBe("1.50GB");
        });
    });

    describe("formatDataSizeBytes", () => {
        it("should format without unit suffix", () => {
            expect(formatDataSizeBytes(0)).toBe("0");
        });

        it("should format kilobytes without B", () => {
            expect(formatDataSizeBytes(1536)).toBe("1.50K");
        });
    });

    describe("formatShortTime", () => {
        it("should format AM time", () => {
            const date = new Date("2024-01-01T09:30:00");
            expect(formatShortTime(date)).toMatch(/9:30am/);
        });

        it("should format PM time", () => {
            const date = new Date("2024-01-01T15:45:00");
            expect(formatShortTime(date)).toMatch(/3:45pm/);
        });

        it("should format midnight as 12am", () => {
            const date = new Date("2024-01-01T00:00:00");
            expect(formatShortTime(date)).toMatch(/12:00am/);
        });
    });

    describe("formatShortDateTime", () => {
        it("should format date and time", () => {
            const date = new Date("2024-01-15T09:30:00");
            const result = formatShortDateTime(date);
            expect(result).toContain("2024-01-15");
            expect(result).toMatch(/9:30am/);
        });
    });
});

describe("Parsing Functions", () => {
    describe("parseDataSize", () => {
        it("should parse bytes", () => {
            expect(parseDataSize("100B")).toBe(100);
        });

        it("should parse kilobytes", () => {
            expect(parseDataSize("1kB")).toBe(1024);
        });

        it("should parse megabytes", () => {
            expect(parseDataSize("1MB")).toBe(1048576);
        });

        it("should parse gigabytes", () => {
            expect(parseDataSize("1GB")).toBe(1073741824);
        });

        it("should return null for invalid format", () => {
            expect(parseDataSize("invalid")).toBeNull();
        });

        it("should handle decimal values", () => {
            expect(parseDataSize("1.5MB")).toBe(1572864);
        });
    });

    describe("parseDuration", () => {
        it("should parse nanoseconds", () => {
            expect(parseDuration("1000000ns")).toBe(1);
        });

        it("should parse microseconds", () => {
            expect(parseDuration("1000us")).toBe(1);
        });

        it("should parse milliseconds", () => {
            expect(parseDuration("100ms")).toBe(100);
        });

        it("should parse seconds", () => {
            expect(parseDuration("1s")).toBe(1000);
        });

        it("should parse minutes", () => {
            expect(parseDuration("1m")).toBe(60000);
        });

        it("should parse hours", () => {
            expect(parseDuration("1h")).toBe(3600000);
        });

        it("should parse days", () => {
            expect(parseDuration("1d")).toBe(86400000);
        });

        it("should return 0 for invalid format", () => {
            expect(parseDuration("invalid")).toBe(0);
        });

        it("should handle decimal values", () => {
            expect(parseDuration("1.5s")).toBe(1500);
        });
    });
});

// Made with Bob
