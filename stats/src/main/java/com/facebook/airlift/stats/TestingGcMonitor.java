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

import com.facebook.airlift.units.Duration;
import com.google.errorprone.annotations.concurrent.GuardedBy;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class TestingGcMonitor
        implements GcMonitor
{
    @GuardedBy("this")
    private long majorGcCount;

    @GuardedBy("this")
    private long majorGcTimeNanos;

    @GuardedBy("this")
    private long lastHeapAfterGcBytes;

    @GuardedBy("this")
    private long previousHeapAfterGcBytes;

    @GuardedBy("this")
    private long lastHeapBeforeGcBytes;

    @GuardedBy("this")
    private long lastBytesReclaimed;

    @GuardedBy("this")
    private long lastGcDurationMs;

    @GuardedBy("this")
    private long lastGcTimestampMs;

    @GuardedBy("this")
    private long previousGcTimestampMs;

    public synchronized void recordMajorGc(Duration duration)
    {
        majorGcCount++;
        majorGcTimeNanos += duration.roundTo(NANOSECONDS);
    }

    public synchronized void recordMajorGc(Duration duration, long heapBeforeBytes, long heapAfterBytes)
    {
        majorGcCount++;
        majorGcTimeNanos += duration.roundTo(NANOSECONDS);

        previousHeapAfterGcBytes = lastHeapAfterGcBytes;
        previousGcTimestampMs = lastGcTimestampMs;

        lastHeapBeforeGcBytes = heapBeforeBytes;
        lastHeapAfterGcBytes = heapAfterBytes;
        lastBytesReclaimed = heapBeforeBytes - heapAfterBytes;
        lastGcDurationMs = duration.toMillis();
        lastGcTimestampMs = System.currentTimeMillis();
    }

    @Override
    public synchronized long getMajorGcCount()
    {
        return majorGcCount;
    }

    @Override
    public synchronized Duration getMajorGcTime()
    {
        return new Duration(majorGcTimeNanos, NANOSECONDS);
    }

    @Override
    public synchronized long getLastHeapAfterGcBytes()
    {
        return lastHeapAfterGcBytes;
    }

    @Override
    public synchronized long getLastHeapBeforeGcBytes()
    {
        return lastHeapBeforeGcBytes;
    }

    @Override
    public synchronized long getLastBytesReclaimed()
    {
        return lastBytesReclaimed;
    }

    @Override
    public synchronized double getLastGcReclaimRatio()
    {
        if (lastHeapBeforeGcBytes == 0) {
            return 0.0;
        }
        return (double) lastBytesReclaimed / lastHeapBeforeGcBytes;
    }

    @Override
    public synchronized long getHeapAfterGcGrowthBytes()
    {
        if (previousHeapAfterGcBytes == 0) {
            return 0;
        }
        return lastHeapAfterGcBytes - previousHeapAfterGcBytes;
    }

    @Override
    public synchronized long getLastGcDurationMs()
    {
        return lastGcDurationMs;
    }

    @Override
    public synchronized long getLastMajorGcIntervalMs()
    {
        if (previousGcTimestampMs == 0) {
            return 0;
        }
        return lastGcTimestampMs - previousGcTimestampMs;
    }

    @Override
    public synchronized long getLastGcTimestampMs()
    {
        return lastGcTimestampMs;
    }
}
