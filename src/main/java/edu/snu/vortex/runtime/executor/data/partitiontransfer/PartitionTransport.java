/*
 * Copyright (C) 2017 Seoul National University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.snu.vortex.runtime.executor.data.partitiontransfer;

import edu.snu.vortex.client.JobConf;
import edu.snu.vortex.runtime.common.NettyChannelImplementationSelector;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.reef.tang.annotations.Parameter;
import org.apache.reef.wake.remote.address.LocalAddressProvider;
import org.apache.reef.wake.remote.ports.TcpPortProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Transport implementation for peer-to-peer {@link edu.snu.vortex.runtime.executor.data.partition.Partition} transfer.
 */
final class PartitionTransport implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(PartitionTransport.class);

  private final SocketAddress serverListeningAddress;
  private final EventLoopGroup serverListeningGroup;
  private final EventLoopGroup serverWorkingGroup;
  private final EventLoopGroup clientGroup;
  private final Bootstrap clientBootstrap;
  private final ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
  private final ConcurrentMap<SocketAddress, Channel> channelMap = new ConcurrentHashMap<>();

  /**
   * Constructs a partition transport and starts listening.
   *
   * @param tcpPortProvider       provides an iterator of random tcp ports
   * @param localAddressProvider  provides the local address of the node to bind to
   * @param port                  the listening port of the server; 0 means random assign using {@code tcpPortProvider}
   * @param serverBacklog         the maximum number of pending connections to the server
   * @param numListeningThreads   the number of listening threads of the server
   * @param numWorkingThreads     the number of working threads of the server
   * @param numClientThreads      the number of client threads
   */
  @Inject
  private PartitionTransport(
      final TcpPortProvider tcpPortProvider,
      final LocalAddressProvider localAddressProvider,
      @Parameter(JobConf.PartitionTransportServerPort.class) final int port,
      @Parameter(JobConf.PartitionTransportServerBacklog.class) final int serverBacklog,
      @Parameter(JobConf.PartitionTransportServerNumListeningThreads.class) final int numListeningThreads,
      @Parameter(JobConf.PartitionTransportServerNumWorkingThreads.class) final int numWorkingThreads,
      @Parameter(JobConf.PartitionTransportClientNumThreads.class) final int numClientThreads) {

    if (port < 0) {
      throw new IllegalArgumentException(String.format("Invalid PartitionTransportPort: %d", port));
    }

    final String host = localAddressProvider.getLocalAddress();

    serverListeningGroup = NettyChannelImplementationSelector.EVENT_LOOP_GROUP_FUNCTION.apply(numListeningThreads);
    serverWorkingGroup = NettyChannelImplementationSelector.EVENT_LOOP_GROUP_FUNCTION.apply(numWorkingThreads);
    clientGroup = NettyChannelImplementationSelector.EVENT_LOOP_GROUP_FUNCTION.apply(numClientThreads);

    final ChannelInitializer channelInitializer = new ChannelInitializer(channelGroup, channelMap);

    clientBootstrap = new Bootstrap();
    clientBootstrap
        .group(clientGroup)
        .channel(NettyChannelImplementationSelector.CHANNEL_CLASS)
        .handler(channelInitializer)
        .option(ChannelOption.SO_REUSEADDR, true);

    final ServerBootstrap serverBootstrap = new ServerBootstrap();
    serverBootstrap
        .group(serverListeningGroup, serverWorkingGroup)
        .channel(NettyChannelImplementationSelector.SERVER_CHANNEL_CLASS)
        .childHandler(channelInitializer)
        .option(ChannelOption.SO_BACKLOG, serverBacklog)
        .option(ChannelOption.SO_REUSEADDR, true);

    int boundPort = 0;
    if (port == 0) {
      for (final int candidatePort : tcpPortProvider) {
        try {
          final Channel channel = serverBootstrap.bind(host, candidatePort).sync().channel();
          channelGroup.add(channel);
          boundPort = candidatePort;
        } catch (final InterruptedException e) {
          LOG.debug(String.format("Cannot bind to %s:%d", host, candidatePort), e);
        }
      }
      if (boundPort == 0) {
        serverListeningGroup.shutdownGracefully();
        serverWorkingGroup.shutdownGracefully();
        clientGroup.shutdownGracefully();
        throw new RuntimeException(String.format("Cannot bind to %s with tcpPortProvider", host));
      }
    } else {
      try {
        final Channel channel = serverBootstrap.bind(host, port).sync().channel();
        channelGroup.add(channel);
        boundPort = port;
      } catch (final InterruptedException e) {
        serverListeningGroup.shutdownGracefully();
        serverWorkingGroup.shutdownGracefully();
        clientGroup.shutdownGracefully();
        throw new RuntimeException(String.format("Cannot bind to %s:%d", host, port), e);
      }
    }

    serverListeningAddress = new InetSocketAddress(host, boundPort);
    LOG.info("Server listening at {}", serverListeningAddress);
  }

  /**
   * Closes all channels and releases all resources.
   */
  @Override
  public void close() {
    LOG.info("Stopping listening at {} and closing", serverListeningAddress);

    final ChannelGroupFuture channelGroupCloseFuture = channelGroup.close();
    final Future serverListeningGroupCloseFuture = serverListeningGroup.shutdownGracefully();
    final Future serverWorkingGroupCloseFuture = serverWorkingGroup.shutdownGracefully();
    final Future clientGroupCloseFuture = clientGroup.shutdownGracefully();

    channelGroupCloseFuture.awaitUninterruptibly();
    serverListeningGroupCloseFuture.awaitUninterruptibly();
    serverWorkingGroupCloseFuture.awaitUninterruptibly();
    clientGroupCloseFuture.awaitUninterruptibly();
  }

  /**
   * Synchronously connects to a remote transport, or returns a cached channel.
   *
   * @param remoteAddress the socket address to connect to
   * @return a {@link Channel} to {@code remoteAddress}
   */
  public Channel getChannelTo(final SocketAddress remoteAddress) {
    final Channel cachedChannel = channelMap.get(remoteAddress);
    if (cachedChannel == null) {
      return clientBootstrap.connect(remoteAddress).syncUninterruptibly().channel();
    } else {
      return cachedChannel;
    }
  }
}