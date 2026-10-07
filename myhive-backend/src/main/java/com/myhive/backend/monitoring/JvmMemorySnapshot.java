package com.myhive.backend.monitoring;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The JVM's memory split at one moment, in bytes: the heap against its ceiling, and the two
 * non-heap areas that keep growing with uptime (metaspace, code cache). One {@link #summary()} line
 * of this every few minutes is what lets a container OOM kill be read off the logs instead of
 * reconstructed by rebuilding the app locally.
 *
 * <p>This is the JMX view. Symbol tables, the CDS archive and thread stacks (~55 MiB on the prod
 * instance) are not memory pools and come on top of {@link #committedTotal()} in the process RSS.
 */
public record JvmMemorySnapshot(long heapUsed, long heapCommitted, long heapMax,
                                long metaspaceCommitted, long classSpaceCommitted, long codeCacheCommitted,
                                int threadCount) {

    /** HotSpot's Metaspace pool is the whole metaspace: the class space pool below is a part of it. */
    private static final String METASPACE_POOL = "Metaspace";
    private static final String CLASS_SPACE_POOL = "Compressed Class Space";
    /** A segmented code cache (the default) reports three {@code CodeHeap '...'} pools, an unsegmented one a single pool. */
    private static final String CODE_HEAP_PREFIX = "CodeHeap";
    private static final String CODE_CACHE_POOL = "CodeCache";
    private static final long MIB = 1024L * 1024L;

    public static JvmMemorySnapshot capture() {
        Map<String, Long> committedByPool = new LinkedHashMap<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage usage = pool.getUsage();
            if (pool.getType() == MemoryType.NON_HEAP && usage != null) {
                committedByPool.put(pool.getName(), usage.getCommitted());
            }
        }
        return from(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage(), committedByPool,
                ManagementFactory.getThreadMXBean().getThreadCount());
    }

    /** {@code committedByPool} is keyed by the HotSpot pool names; a pool this JVM lacks counts as 0. */
    static JvmMemorySnapshot from(MemoryUsage heap, Map<String, Long> committedByPool, int threadCount) {
        long codeCache = 0L;
        for (Map.Entry<String, Long> pool : committedByPool.entrySet()) {
            if (pool.getKey().startsWith(CODE_HEAP_PREFIX) || pool.getKey().equals(CODE_CACHE_POOL)) {
                codeCache += pool.getValue();
            }
        }
        return new JvmMemorySnapshot(heap.getUsed(), heap.getCommitted(), heap.getMax(),
                committedByPool.getOrDefault(METASPACE_POOL, 0L),
                committedByPool.getOrDefault(CLASS_SPACE_POOL, 0L),
                codeCache, threadCount);
    }

    /** Heap, metaspace and code cache committed; the class space is already inside the metaspace figure. */
    public long committedTotal() {
        return heapCommitted + metaspaceCommitted + codeCacheCommitted;
    }

    public String summary() {
        return "JVM memory: heap " + mib(heapUsed) + "/" + mib(heapCommitted) + "/" + mibOrUnknown(heapMax)
                + " MiB used/committed/max; metaspace " + mib(metaspaceCommitted)
                + " MiB (class space " + mib(classSpaceCommitted)
                + "); code cache " + mib(codeCacheCommitted)
                + " MiB; heap+metaspace+code " + mib(committedTotal())
                + " MiB committed; " + threadCount + " threads";
    }

    private static String mib(long bytes) {
        return Long.toString(bytes / MIB);
    }

    /** {@link MemoryUsage#getMax()} is -1 when the JVM has no ceiling for the area. */
    private static String mibOrUnknown(long bytes) {
        return bytes < 0 ? "?" : mib(bytes);
    }
}
