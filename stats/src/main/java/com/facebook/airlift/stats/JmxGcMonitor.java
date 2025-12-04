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
package com.facebook.airlift.stats;

import com.facebook.airlift.log.Logger;
import com.facebook.airlift.units.Duration;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.management.JMException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Monitor GC events via JMX. GC events are divided into major and minor using
 * the OpenJDK naming convention for gcAction.  Also, application time is calculated
 * using the assumption that major collections stop the application.
 * <p>
 * Major and minor GCs are logged to standard logging system, which makes it
 * easy to debug the full log stream. TimeStats are exported for major, minor,
 * and application time.
 */
public class JmxGcMonitor
        implements GcMonitor
{
    private final Logger log = Logger.get(JmxGcMonitor.class);

    private final NotificationListener notificationListener = (notification, ignored) -> onNotification(notification);

    private final AtomicLong majorGcCount = new AtomicLong();
    private final AtomicLong majorGcTime = new AtomicLong();
    private final TimeStat majorGc = new TimeStat();

    private final TimeStat minorGc = new TimeStat();

    private final AtomicLong lastHeapAfterGcBytes = new AtomicLong();
    private final AtomicLong previousHeapAfterGcBytes = new AtomicLong();
    private final AtomicLong lastHeapBeforeGcBytes = new AtomicLong();
    private final AtomicLong lastBytesReclaimed = new AtomicLong();
    private final AtomicLong lastGcDurationMs = new AtomicLong();
    private final AtomicLong lastGcTimestampMs = new AtomicLong();
    private final AtomicLong previousGcTimestampMs = new AtomicLong();

    @GuardedBy("this")
    private long lastGcEndTime = System.currentTimeMillis();

    @PostConstruct
    public void start()
    {
        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            ObjectName objectName = mbean.getObjectName();
            try {
                ManagementFactory.getPlatformMBeanServer().addNotificationListener(
                        objectName,
                        notificationListener,
                        null,
                        null);
            }
            catch (JMException e) {
                throw new RuntimeException("Unable to add GC listener", e);
            }
        }
    }

    @PreDestroy
    public void stop()
    {
        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            ObjectName objectName = mbean.getObjectName();
            try {
                ManagementFactory.getPlatformMBeanServer().removeNotificationListener(objectName, notificationListener);
            }
            catch (JMException ignored) {
            }
        }
    }

    @Override
    public long getMajorGcCount()
    {
        return majorGcCount.get();
    }

    @Override
    public Duration getMajorGcTime()
    {
        return new Duration(majorGcTime.get(), MILLISECONDS);
    }

    @Managed
    @Nested
    public TimeStat getMajorGc()
    {
        return majorGc;
    }

    @Managed
    @Nested
    public TimeStat getMinorGc()
    {
        return minorGc;
    }

    private synchronized void onNotification(Notification notification)
    {
        if ("com.sun.management.gc.notification".equals(notification.getType())) {
            com.facebook.airlift.stats.GarbageCollectionNotificationInfo info = new com.facebook.airlift.stats.GarbageCollectionNotificationInfo((CompositeData) notification.getUserData());

            if (info.isFullGcCycle()) {
                // This handles both traditional major GC (G1, Parallel) and ZGC cycles
                majorGcCount.incrementAndGet();
                majorGcTime.addAndGet(info.getDurationMs());
                majorGc.add(info.getDurationMs(), MILLISECONDS);

                // Update memory leak detection metrics
                updateMemoryMetrics(info);

                // assume that major GCs / ZGC cycles stop the application (or have pauses)
                long applicationRuntime = max(0, info.getStartTime() - lastGcEndTime);
                lastGcEndTime = info.getEndTime();

                log.info(
                        "%s: application %sms, stopped %sms: %s -> %s",
                        info.isZgcCycle() ? "ZGC Cycle" : "Major GC",
                        applicationRuntime,
                        info.getDurationMs(),
                        info.getBeforeGcTotal(),
                        info.getAfterGcTotal());
            }
            else if (info.isMinorGc()) {
                minorGc.add(info.getDurationMs(), MILLISECONDS);

                // assumption that minor GCs run concurrently, so we do not print stopped or application time
                log.debug(
                        "Minor GC: duration %sms: %s -> %s",
                        info.getDurationMs(),
                        info.getBeforeGcTotal(),
                        info.getAfterGcTotal());
            }
            else if (info.isZgcPause()) {
                // ZGC pauses are very short STW events, track them separately
                minorGc.add(info.getDurationMs(), MILLISECONDS);

                log.debug(
                        "ZGC Pause: duration %sms",
                        info.getDurationMs());
            }
        }
    }

    private void updateMemoryMetrics(com.facebook.airlift.stats.GarbageCollectionNotificationInfo info)
    {
        long now = System.currentTimeMillis();

        // Calculate heap before and after GC
        long heapBefore = info.getBeforeGcTotal().toBytes();
        long heapAfter = info.getAfterGcTotal().toBytes();
        long reclaimed = heapBefore - heapAfter;

        // Update previous values before setting new ones
        previousHeapAfterGcBytes.set(lastHeapAfterGcBytes.get());
        previousGcTimestampMs.set(lastGcTimestampMs.get());

        // Update current values
        lastHeapBeforeGcBytes.set(heapBefore);
        lastHeapAfterGcBytes.set(heapAfter);
        lastGcTimestampMs.set(now);
        lastBytesReclaimed.set(reclaimed);
        lastGcDurationMs.set(info.getDurationMs());
    }

    // ==================== Memory Metrics JMX ====================

    @Override
    @Managed(description = "Heap used after last major GC in bytes")
    public long getLastHeapAfterGcBytes()
    {
        return lastHeapAfterGcBytes.get();
    }

    @Override
    @Managed(description = "Heap used before last major GC in bytes")
    public long getLastHeapBeforeGcBytes()
    {
        return lastHeapBeforeGcBytes.get();
    }

    @Override
    @Managed(description = "Bytes reclaimed in last major GC")
    public long getLastBytesReclaimed()
    {
        return lastBytesReclaimed.get();
    }

    @Override
    @Managed(description = "Ratio of memory reclaimed in last GC (0.0-1.0)")
    public double getLastGcReclaimRatio()
    {
        long before = lastHeapBeforeGcBytes.get();
        if (before == 0) {
            return 0.0;
        }
        return (double) lastBytesReclaimed.get() / before;
    }

    @Override
    @Managed(description = "Heap growth between last two major GCs in bytes")
    public long getHeapAfterGcGrowthBytes()
    {
        long previous = previousHeapAfterGcBytes.get();
        if (previous == 0) {
            return 0;
        }
        return lastHeapAfterGcBytes.get() - previous;
    }

    @Override
    @Managed(description = "Duration of last major GC in milliseconds")
    public long getLastGcDurationMs()
    {
        return lastGcDurationMs.get();
    }

    @Override
    @Managed(description = "Time between last two major GCs in milliseconds")
    public long getLastMajorGcIntervalMs()
    {
        long previous = previousGcTimestampMs.get();
        if (previous == 0) {
            return 0;
        }
        return lastGcTimestampMs.get() - previous;
    }

    @Override
    @Managed(description = "Timestamp of last major GC in epoch milliseconds")
    public long getLastGcTimestampMs()
    {
        return lastGcTimestampMs.get();
    }
}
