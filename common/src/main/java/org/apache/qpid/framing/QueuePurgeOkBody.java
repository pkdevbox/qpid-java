/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

/*
 * This file is auto-generated by Qpid Gentools v.0.1 - do not modify.
 * Supported AMQP version:
 *   8-0
 */

package org.apache.qpid.framing;

import java.io.DataOutput;
import java.io.IOException;

import org.apache.qpid.AMQException;
import org.apache.qpid.codec.MarkableDataInput;

public class QueuePurgeOkBody extends AMQMethodBodyImpl implements EncodableAMQDataBlock, AMQMethodBody
{

    public static final int CLASS_ID =  50;
    public static final int METHOD_ID = 31;

    // Fields declared in specification
    private final long _messageCount; // [messageCount]

    // Constructor
    public QueuePurgeOkBody(MarkableDataInput buffer) throws AMQFrameDecodingException, IOException
    {
        _messageCount = EncodingUtils.readUnsignedInteger(buffer);
    }

    public QueuePurgeOkBody(
            long messageCount
                           )
    {
        _messageCount = messageCount;
    }

    public int getClazz()
    {
        return CLASS_ID;
    }

    public int getMethod()
    {
        return METHOD_ID;
    }

    public final long getMessageCount()
    {
        return _messageCount;
    }

    protected int getBodySize()
    {
        int size = 4;
        return size;
    }

    public void writeMethodPayload(DataOutput buffer) throws IOException
    {
        writeUnsignedInteger( buffer, _messageCount );
    }

    public boolean execute(MethodDispatcher dispatcher, int channelId) throws AMQException
	{
        return dispatcher.dispatchQueuePurgeOk(this, channelId);
	}

    public String toString()
    {
        StringBuilder buf = new StringBuilder("[QueuePurgeOkBodyImpl: ");
        buf.append( "messageCount=" );
        buf.append(  getMessageCount() );
        buf.append("]");
        return buf.toString();
    }

    public static void process(final MarkableDataInput buffer,
                               final ClientChannelMethodProcessor dispatcher) throws IOException
    {
        long messageCount = EncodingUtils.readUnsignedInteger(buffer);
        if(!dispatcher.ignoreAllButCloseOk())
        {
            dispatcher.receiveQueuePurgeOk(messageCount);
        }
    }
}
