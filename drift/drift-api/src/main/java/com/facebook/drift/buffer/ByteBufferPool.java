/*
 * Copyright (C) 2012 ${project.organization.name}
 *
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
package com.facebook.drift.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ByteBufferPool
{
    private static final int DEFAULT_BUFFER_SIZE = 4096; // 4KB
    private static final int DEFAULT_BUFFER_COUNT = 1024; // 4MB max pool size
    private final ArrayBlockingQueue<ByteBuffer> pool;
    private final int bufferSize;

    private static final AtomicInteger idGenerator = new AtomicInteger();

    public ByteBufferPool()
    {
        this(DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_COUNT);
    }

    public ByteBufferPool(int bufferSize, int maxCount)
    {
        this.bufferSize = bufferSize;
        pool = new ArrayBlockingQueue<>(maxCount);

        // Pre-populate the pool
        for (int i = 0; i < maxCount; i++) {
            pool.offer(ByteBuffer.allocate(bufferSize));
        }
    }

    public ByteBuffer acquire()
    {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            buffer = ByteBuffer.allocate(bufferSize);
        }
        buffer.clear();
        return buffer;
    }

    public void release(ByteBuffer byteBuffer)
    {
        byteBuffer.clear();
        boolean ignored = pool.offer(byteBuffer);
    }
}
