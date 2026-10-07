package com.myhive.backend.monitoring;

import org.junit.jupiter.api.Test;

import java.lang.management.MemoryUsage;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JvmMemorySnapshotTest {

    private static final long MIB = 1024L * 1024L;

    /** {@code maxMib} -1 stands for an area without a ceiling, which {@link MemoryUsage} spells as -1. */
    private static MemoryUsage usage(long usedMib, long committedMib, long maxMib) {
        return new MemoryUsage(0, usedMib * MIB, committedMib * MIB, maxMib < 0 ? -1 : maxMib * MIB);
    }

    @Test
    void summary_reportsHeapMetaspaceCodeCacheAndThreadsInMebibytes() {
        JvmMemorySnapshot snapshot = new JvmMemorySnapshot(
                116 * MIB, 145 * MIB, 282 * MIB, 127 * MIB, 20 * MIB, 49 * MIB, 41);

        String expectedSummary = "JVM memory: heap 116/145/282 MiB used/committed/max; "
                + "metaspace 127 MiB (class space 20); code cache 49 MiB; "
                + "heap+metaspace+code 321 MiB committed; 41 threads";
        assertThat(snapshot.summary()).isEqualTo(expectedSummary);
    }

    @Test
    void summary_printsAnUndefinedHeapMaxAsUnknown() {
        JvmMemorySnapshot snapshot = new JvmMemorySnapshot(
                10 * MIB, 20 * MIB, -1, 5 * MIB, 1 * MIB, 2 * MIB, 3);

        assertThat(snapshot.summary()).startsWith("JVM memory: heap 10/20/? MiB used/committed/max;");
    }

    @Test
    void from_sumsTheSegmentedCodeHeapsAndPicksMetaspacePoolsByName() {
        long expectedMetaspace = 127 * MIB;
        long expectedClassSpace = 20 * MIB;
        Map<String, Long> committedByPool = Map.of(
                "Metaspace", expectedMetaspace,
                "Compressed Class Space", expectedClassSpace,
                "CodeHeap 'non-nmethods'", 5 * MIB,
                "CodeHeap 'profiled nmethods'", 24 * MIB,
                "CodeHeap 'non-profiled nmethods'", 20 * MIB);

        JvmMemorySnapshot snapshot = JvmMemorySnapshot.from(usage(116, 145, 282), committedByPool, 41);

        assertThat(snapshot.heapUsed()).isEqualTo(116 * MIB);
        assertThat(snapshot.heapCommitted()).isEqualTo(145 * MIB);
        assertThat(snapshot.heapMax()).isEqualTo(282 * MIB);
        assertThat(snapshot.metaspaceCommitted()).isEqualTo(expectedMetaspace);
        assertThat(snapshot.classSpaceCommitted()).isEqualTo(expectedClassSpace);
        assertThat(snapshot.codeCacheCommitted()).isEqualTo(49 * MIB);
        assertThat(snapshot.threadCount()).isEqualTo(41);
    }

    @Test
    void committedTotal_addsHeapMetaspaceAndCodeCacheOnce() {
        // The Metaspace pool already contains the class space, so class space must not be added again.
        JvmMemorySnapshot snapshot = new JvmMemorySnapshot(
                116 * MIB, 145 * MIB, 282 * MIB, 127 * MIB, 20 * MIB, 49 * MIB, 41);

        assertThat(snapshot.committedTotal()).isEqualTo((145 + 127 + 49) * MIB);
    }

    @Test
    void from_acceptsAJvmWithAnUnsegmentedCodeCache() {
        Map<String, Long> committedByPool = Map.of(
                "Metaspace", 50 * MIB,
                "Compressed Class Space", 6 * MIB,
                "CodeCache", 30 * MIB);

        JvmMemorySnapshot snapshot = JvmMemorySnapshot.from(usage(10, 20, 40), committedByPool, 7);

        assertThat(snapshot.codeCacheCommitted()).isEqualTo(30 * MIB);
    }

    @Test
    void capture_readsTheRunningJvm() {
        JvmMemorySnapshot snapshot = JvmMemorySnapshot.capture();

        assertThat(snapshot.heapUsed()).isPositive();
        assertThat(snapshot.heapCommitted()).isGreaterThanOrEqualTo(snapshot.heapUsed());
        assertThat(snapshot.codeCacheCommitted()).isPositive();
        assertThat(snapshot.classSpaceCommitted()).isPositive();
        // HotSpot reports the class space as a part of the Metaspace pool, never beside it.
        assertThat(snapshot.metaspaceCommitted()).isGreaterThan(snapshot.classSpaceCommitted());
        assertThat(snapshot.threadCount()).isPositive();
    }
}
