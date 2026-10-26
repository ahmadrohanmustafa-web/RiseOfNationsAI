package com.riseofnations.core;

/** Runs every test. Exit code 0 = all passed, 1 = something failed (so Gradle / CI fails visibly). */
public final class AllTests {

    private AllTests() { }

    public static void main(String[] args) {
        EconomyTests.run();
        StoryTests.run();
        PersistenceTests.run();
        CivilizationTests.run();
        PersistenceCivTests.run();
        SettlementTests.run();
        PersistenceSettlementTests.run();
        IsolationTests.run();
        System.exit(TestKit.report());
    }
}
