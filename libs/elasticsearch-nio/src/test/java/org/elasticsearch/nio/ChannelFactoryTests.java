/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.nio;

import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.test.ESTestCase;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Supplier;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChannelFactoryTests extends ESTestCase {

    private ChannelFactory<NioServerSocketChannel, NioSocketChannel> channelFactory;
    private ChannelFactory.RawChannelFactory rawChannelFactory;
    private SocketChannel rawChannel;
    private ServerSocketChannel rawServerChannel;
    private SocketSelector socketSelector;
    private Supplier<SocketSelector> socketSelectorSupplier;
    private Supplier<AcceptingSelector> acceptingSelectorSupplier;
    private AcceptingSelector acceptingSelector;

    @Before
    @SuppressWarnings("unchecked")
    public void setupFactory() throws IOException {
        rawChannelFactory = mock(ChannelFactory.RawChannelFactory.class);
        channelFactory = new TestChannelFactory(rawChannelFactory);
        socketSelector = mock(SocketSelector.class);
        acceptingSelector = mock(AcceptingSelector.class);
        socketSelectorSupplier = mock(Supplier.class);
        acceptingSelectorSupplier = mock(Supplier.class);
        rawChannel = SocketChannel.open();
        rawServerChannel = ServerSocketChannel.open();
        when(socketSelectorSupplier.get()).thenReturn(socketSelector);
        when(acceptingSelectorSupplier.get()).thenReturn(acceptingSelector);
    }

    @After
    public void ensureClosed() throws IOException {
        IOUtils.closeWhileHandlingException(rawChannel, rawServerChannel);
    }

    public void testAcceptChannel() throws IOException {
        ServerChannelContext serverChannelContext = mock(ServerChannelContext.class);
        when(rawChannelFactory.acceptNioChannel(serverChannelContext)).thenReturn(rawChannel);

        NioSocketChannel channel = channelFactory.acceptNioChannel(serverChannelContext, socketSelectorSupplier);

        verify(socketSelector).scheduleForRegistration(channel);

        assertEquals(rawChannel, channel.getRawChannel());
    }

    public void testAcceptedChannelRejected() throws IOException {
        ServerChannelContext serverChannelContext = mock(ServerChannelContext.class);
        when(rawChannelFactory.acceptNioChannel(serverChannelContext)).thenReturn(rawChannel);
        doThrow(new IllegalStateException()).when(socketSelector).scheduleForRegistration(any());

        expectThrows(IllegalStateException.class, () -> channelFactory.acceptNioChannel(serverChannelContext, socketSelectorSupplier));

        assertFalse(rawChannel.isOpen());
    }

    public void testOpenChannel() throws IOException {
        InetSocketAddress address = mock(InetSocketAddress.class);
        when(rawChannelFactory.openNioChannel(same(address))).thenReturn(rawChannel);

        NioSocketChannel channel = channelFactory.openNioChannel(address, socketSelectorSupplier);

        verify(socketSelector).scheduleForRegistration(channel);

        assertEquals(rawChannel, channel.getRawChannel());
    }

    public void testOpenedChannelRejected() throws IOException {
        InetSocketAddress address = mock(InetSocketAddress.class);
        when(rawChannelFactory.openNioChannel(same(address))).thenReturn(rawChannel);
        doThrow(new IllegalStateException()).when(socketSelector).scheduleForRegistration(any());

        expectThrows(IllegalStateException.class, () -> channelFactory.openNioChannel(address, socketSelectorSupplier));

        assertFalse(rawChannel.isOpen());
    }

    public void testOpenServerChannel() throws IOException {
        InetSocketAddress address = mock(InetSocketAddress.class);
        when(rawChannelFactory.openNioServerSocketChannel(same(address))).thenReturn(rawServerChannel);

        NioServerSocketChannel channel = channelFactory.openNioServerSocketChannel(address, acceptingSelectorSupplier);

        verify(acceptingSelector).scheduleForRegistration(channel);

        assertEquals(rawServerChannel, channel.getRawChannel());
    }

    public void testOpenedServerChannelRejected() throws IOException {
        InetSocketAddress address = mock(InetSocketAddress.class);
        when(rawChannelFactory.openNioServerSocketChannel(same(address))).thenReturn(rawServerChannel);
        doThrow(new IllegalStateException()).when(acceptingSelector).scheduleForRegistration(any());

        expectThrows(IllegalStateException.class, () -> channelFactory.openNioServerSocketChannel(address, acceptingSelectorSupplier));

        assertFalse(rawServerChannel.isOpen());
    }

    private static class TestChannelFactory extends ChannelFactory<NioServerSocketChannel, NioSocketChannel> {

        TestChannelFactory(RawChannelFactory rawChannelFactory) {
            super(rawChannelFactory);
        }

        @SuppressWarnings("unchecked")
        @Override
        public NioSocketChannel createChannel(SocketSelector selector, SocketChannel channel) throws IOException {
            NioSocketChannel nioSocketChannel = new NioSocketChannel(channel);
            nioSocketChannel.setContext(mock(SocketChannelContext.class));
            return nioSocketChannel;
        }

        @Override
        public NioServerSocketChannel createServerChannel(AcceptingSelector selector, ServerSocketChannel channel) throws IOException {
            return new NioServerSocketChannel(channel);
        }
    }
}
