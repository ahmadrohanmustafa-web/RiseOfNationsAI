package com.riseofnations.core;

import java.util.ArrayList;
import java.util.List;

/** Minimal test runner (no downloads needed). A failed check throws; the runner counts and reports. */
public final class TestKit {

    public interface Body { void run() throws Exception; }

    private static final List<String> failures = new ArrayList<>();
    private static int passed;

    private TestKit() { }

    public static void test(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (Throwable t) {
            failures.add(name + " -> " + t);
            System.out.println("  FAIL  " + name + " -> " + t);
        }
    }

    public static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void eq(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static int report() {
        System.out.println();
        System.out.println(passed + " passed, " + failures.size() + " failed");
        for (String f : failures) System.out.println("  FAILED: " + f);
        return failures.isEmpty() ? 0 : 1;
    }
}
