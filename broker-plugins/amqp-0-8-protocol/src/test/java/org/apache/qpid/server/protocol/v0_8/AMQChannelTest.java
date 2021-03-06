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
package org.apache.qpid.server.protocol.v0_8;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.security.auth.Subject;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageContentSource;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.store.MessageHandle;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.NullMessageStore;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoredMemoryMessage;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.test.utils.QpidTestCase;

public class AMQChannelTest extends QpidTestCase
{
    public static final AMQShortString ROUTING_KEY = AMQShortString.valueOf("routingKey");

    private VirtualHostImpl<?, AMQQueue<?>, ExchangeImpl<?>> _virtualHost;
    private AMQPConnection_0_8 _amqConnection;
    private MessageStore _messageStore;
    private AmqpPort<?> _port;
    private Broker<?> _broker;
    private ProtocolOutputConverter _protocolOutputConverter;
    private MessageDestination _messageDestination;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        TaskExecutor taskExecutor = mock(TaskExecutor.class);

        _broker = mock(Broker.class);
        when(_broker.getEventLogger()).thenReturn(mock(EventLogger.class));
        when(_broker.getContextValue(Long.class, Broker.CHANNEL_FLOW_CONTROL_ENFORCEMENT_TIMEOUT)).thenReturn(1l);

        _messageStore = mock(MessageStore.class);

        _virtualHost = mock(VirtualHostImpl.class);
        when(_virtualHost.getContextValue(Integer.class, Broker.MESSAGE_COMPRESSION_THRESHOLD_SIZE)).thenReturn(1);
        when(_virtualHost.getContextValue(Long.class, Connection.MAX_UNCOMMITTED_IN_MEMORY_SIZE)).thenReturn(1l);
        when(_virtualHost.getContextValue(Boolean.class, Broker.BROKER_MSG_AUTH)).thenReturn(false);
        when(_virtualHost.getPrincipal()).thenReturn(mock(Principal.class));
        when(_virtualHost.getEventLogger()).thenReturn(mock(EventLogger.class));
        when(_virtualHost.getSecurityManager()).thenReturn(new org.apache.qpid.server.security.SecurityManager(_broker, false));

        _port = mock(AmqpPort.class);
        when(_port.getChildExecutor()).thenReturn(taskExecutor);
        when(_port.getModel()).thenReturn(BrokerModel.getInstance());
        when(_port.getContextValue(Integer.class, AmqpPort.PORT_MAX_MESSAGE_SIZE)).thenReturn(1);

        AuthenticatedPrincipal authenticatedPrincipal = new AuthenticatedPrincipal("user");
        Set<Principal> authenticatedUser = Collections.<Principal>singleton(authenticatedPrincipal);
        Subject authenticatedSubject = new Subject(true, authenticatedUser, Collections.<Principal>emptySet(), Collections.<Principal>emptySet());

        _protocolOutputConverter = mock(ProtocolOutputConverter.class);

        _amqConnection = mock(AMQPConnection_0_8.class);
        when(_amqConnection.getAuthorizedSubject()).thenReturn(authenticatedSubject);
        when(_amqConnection.getAuthorizedPrincipal()).thenReturn(authenticatedPrincipal);
        when(_amqConnection.getVirtualHost()).thenReturn((VirtualHostImpl)_virtualHost);
        when(_amqConnection.getProtocolOutputConverter()).thenReturn(_protocolOutputConverter);
        when(_amqConnection.getBroker()).thenReturn((Broker) _broker);
        when(_amqConnection.getMethodRegistry()).thenReturn(new MethodRegistry(ProtocolVersion.v0_9));

        _messageDestination = mock(MessageDestination.class);
    }

    public void testReceiveExchangeDeleteWhenIfUsedIsSetAndExchangeHasBindings() throws Exception
    {
        String testExchangeName = getTestName();
        ExchangeImpl<?> exchange = mock(ExchangeImpl.class);
        when(exchange.hasBindings()).thenReturn(true);
        doReturn(exchange).when(_virtualHost).getAttainedExchange(testExchangeName);

        AMQChannel channel = new AMQChannel(_amqConnection, 1, _messageStore);

        channel.receiveExchangeDelete(AMQShortString.valueOf(testExchangeName), true, false);

        verify(_amqConnection).closeChannelAndWriteFrame(eq(channel),
                                                         eq(AMQConstant.IN_USE),
                                                         eq("Exchange has bindings"));
    }

    public void testReceiveExchangeDeleteWhenIfUsedIsSetAndExchangeHasNoBinding() throws Exception
    {
        ExchangeImpl<?> exchange = mock(ExchangeImpl.class);
        when(exchange.hasBindings()).thenReturn(false);
        doReturn(exchange).when(_virtualHost).getAttainedExchange(getTestName());

        AMQChannel channel = new AMQChannel(_amqConnection, 1, _messageStore);
        channel.receiveExchangeDelete(AMQShortString.valueOf(getTestName()), true, false);

        verify(exchange).delete();
    }

    public void testOversizedMessageClosesChannel() throws Exception
    {
        when(_virtualHost.getDefaultDestination()).thenReturn(mock(MessageDestination.class));

        long maximumMessageSize = 1024l;
        when(_amqConnection.getMaxMessageSize()).thenReturn(maximumMessageSize);

        AMQChannel channel = new AMQChannel(_amqConnection, 1, _virtualHost.getMessageStore());

        BasicContentHeaderProperties properties = new BasicContentHeaderProperties();
        channel.receiveBasicPublish(AMQShortString.EMPTY_STRING, AMQShortString.EMPTY_STRING, false, false);
        channel.receiveMessageHeader(properties, maximumMessageSize + 1);

        verify(_amqConnection).closeChannelAndWriteFrame(eq(channel),
                                                         eq(AMQConstant.MESSAGE_TOO_LARGE),
                                                         eq("Message size of 1025 greater than allowed maximum of 1024"));

    }

    public void testPublishContentHeaderWhenMessageAuthorizationFails() throws Exception
    {
        when(_virtualHost.getDefaultDestination()).thenReturn(mock(MessageDestination.class));
        when(_virtualHost.getContextValue(Boolean.class, Broker.BROKER_MSG_AUTH)).thenReturn(true);
        when(_virtualHost.getMessageStore()).thenReturn(new NullMessageStore()
        {
            @Override
            public <T extends StorableMessageMetaData> MessageHandle<T> addMessage(final T metaData)
            {
                MessageHandle messageHandle = new StoredMemoryMessage(1, metaData);
                return messageHandle;
            }
        });

        Set<Principal> authenticatedUser = Collections.<Principal>singleton(new AuthenticatedPrincipal("user"));
        _amqConnection.setAuthorizedSubject(new Subject(true,
                                                        authenticatedUser,
                                                        Collections.<Principal>emptySet(),
                                                        Collections.<Principal>emptySet()));

        int channelId = 1;
        AMQChannel channel = new AMQChannel(_amqConnection, channelId, _virtualHost.getMessageStore());

        BasicContentHeaderProperties properties = new BasicContentHeaderProperties();
        properties.setUserId("impostor");
        channel.receiveBasicPublish(AMQShortString.EMPTY_STRING, AMQShortString.EMPTY_STRING, false, false);
        channel.receiveMessageHeader(properties, 0);

        verify(_protocolOutputConverter).writeReturn(any(MessagePublishInfo.class),
                                                     any(ContentHeaderBody.class),
                                                     any(MessageContentSource.class),
                                                     eq(channelId),
                                                     eq(AMQConstant.ACCESS_REFUSED.getCode()),
                                                     eq(AMQShortString.valueOf("Access Refused")));
        verifyZeroInteractions(_messageDestination);
    }

    public void testPublishContentHeaderWhenMessageAuthorizationSucceeds() throws Exception
    {
        when(_virtualHost.getDefaultDestination()).thenReturn(_messageDestination);
        when(_virtualHost.getContextValue(Boolean.class, Broker.BROKER_MSG_AUTH)).thenReturn(true);
        when(_virtualHost.getMessageStore()).thenReturn(new NullMessageStore()
        {
            @Override
            public <T extends StorableMessageMetaData> MessageHandle<T> addMessage(final T metaData)
            {
                MessageHandle messageHandle = new StoredMemoryMessage(1, metaData);
                return messageHandle;
            }
        });

        Set<Principal> authenticatedUser = Collections.<Principal>singleton(new AuthenticatedPrincipal("user"));
        _amqConnection.setAuthorizedSubject(new Subject(true, authenticatedUser, Collections.<Principal>emptySet(),  Collections.<Principal>emptySet()));

        AMQChannel channel = new AMQChannel(_amqConnection, 1, _virtualHost.getMessageStore());

        BasicContentHeaderProperties properties = new BasicContentHeaderProperties();
        properties.setUserId(_amqConnection.getAuthorizedPrincipal().getName());
        channel.receiveBasicPublish(AMQShortString.EMPTY_STRING, ROUTING_KEY, false, false);
        channel.receiveMessageHeader(properties, 0);

        verify(_messageDestination).send((ServerMessage) any(),
                                         eq(ROUTING_KEY.toString()),
                                         any(InstanceProperties.class),
                                         any(ServerTransaction.class),
                                         any(Action.class) );
    }
}
