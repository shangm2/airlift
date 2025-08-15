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
package com.facebook.drift.protocol.bytebuffer;

import com.facebook.drift.protocol.TTransport;
import com.facebook.drift.protocol.TTransportException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static java.lang.String.format;

public class ByteBufferInputTransport
        implements TTransport
{
    private final ByteBufferInputStream inputStream;

    public ByteBufferInputTransport(List<ByteBuffer> byteBuffers)
    {
        inputStream = new ByteBufferInputStream(byteBuffers);
    }

    @Override
    public void read(byte[] buf, int off, int len)
            throws TTransportException
    {
        try {
            int bytesRead = inputStream.read(buf, off, len);

            if (bytesRead < 0) {
                if (len > 0) {
                    throw new TTransportException("End of stream reached when trying to read " + len + " bytes, but gets " + bytesRead + " bytes");
                }
                return;
            }
            if (bytesRead < len) {
                throw new TTransportException(format("Not enough bytes to read, read %s bytes but need %s bytes", bytesRead, len));
            }
        }
        catch (IOException e) {
            throw new TTransportException("Failed to read", e);
        }
    }

    @Override
    public void write(byte[] buf, int off, int len)
            throws TTransportException
    {
        throw new TTransportException("This is a read-only transport");
    }
}
