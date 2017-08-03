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
package edu.snu.vortex.runtime.executor.data;

import com.google.protobuf.InvalidProtocolBufferException;
import edu.snu.vortex.client.JobConf;
import edu.snu.vortex.common.coder.Coder;
import edu.snu.vortex.compiler.ir.Element;
import edu.snu.vortex.compiler.ir.attribute.Attribute;
import edu.snu.vortex.runtime.common.ReplyFutureMap;
import edu.snu.vortex.runtime.common.comm.ControlMessage;
import edu.snu.vortex.runtime.exception.NodeConnectionException;
import edu.snu.vortex.runtime.exception.UnsupportedPartitionStoreException;
import org.apache.reef.io.network.naming.NameResolver;
import org.apache.reef.tang.InjectionFuture;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.annotations.Parameter;
import org.apache.reef.tang.exceptions.InjectionException;
import org.apache.reef.wake.EventHandler;
import org.apache.reef.wake.Identifier;
import org.apache.reef.wake.impl.SyncStage;
import org.apache.reef.wake.impl.ThreadPoolStage;
import org.apache.reef.wake.remote.Codec;
import org.apache.reef.wake.remote.RemoteConfiguration;
import org.apache.reef.wake.remote.address.LocalAddressProvider;
import org.apache.reef.wake.remote.impl.TransportEvent;
import org.apache.reef.wake.remote.transport.Link;
import org.apache.reef.wake.remote.transport.LinkListener;
import org.apache.reef.wake.remote.transport.Transport;
import org.apache.reef.wake.remote.transport.netty.LoggingLinkListener;
import org.apache.reef.wake.remote.transport.netty.NettyMessagingTransport;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles partition transfer between {@link edu.snu.vortex.runtime.executor.Executor}s.
 */
@ThreadSafe
final class PartitionTransferPeer {
  private static final Logger LOG = Logger.getLogger(PartitionTransferPeer.class.getName());
  private static final RequestPartitionMessageCodec REQUEST_MESSAGE_CODEC = new RequestPartitionMessageCodec();
  private static final LinkListener LINK_LISTENER = new LoggingLinkListener();

  private final Transport transport;
  private final NameResolver nameResolver;
  private final InjectionFuture<PartitionManagerWorker> partitionManagerWorker;

  private final AtomicLong requestIdCounter;
  private final ConcurrentHashMap<Long, Coder> requestIdToCoder;
  private final ReplyFutureMap<Iterable<Element>> replyFutureMap;

  @Inject
  private PartitionTransferPeer(final NameResolver nameResolver,
                                final InjectionFuture<PartitionManagerWorker> partitionManagerWorker,
                                final LocalAddressProvider localAddressProvider,
                                final PartitionClientHandler partitionClientHandler,
                                final PartitionServerHandler partitionServerHandler,
                                final ExceptionHandler exceptionHandler,
                                @Parameter(JobConf.ExecutorId.class) final String executorId) {
    this.nameResolver = nameResolver;
    this.partitionManagerWorker = partitionManagerWorker;
    this.requestIdCounter = new AtomicLong(1);
    this.requestIdToCoder = new ConcurrentHashMap<>();
    this.replyFutureMap = new ReplyFutureMap<>();

    transport = createTransport(localAddressProvider.getLocalAddress(),
        partitionClientHandler, partitionServerHandler, exceptionHandler, 5);

    final InetSocketAddress serverAddress = (InetSocketAddress) transport.getLocalAddress();
    LOG.log(Level.FINE, "PartitionTransferPeer starting, listening at {0}", serverAddress);

    final Identifier serverIdentifier = new PartitionTransferPeerIdentifier(executorId);
    try {
      nameResolver.register(serverIdentifier, serverAddress);
    } catch (final Exception e) {
      LOG.log(Level.SEVERE, "Cannot register PartitionTransferPeer to name server");
      throw new RuntimeException(e);
    }
  }

  /**
   * Create a Wake {@link Transport} for peer-to-peer communication.
   * @param localAddress local address from {@link LocalAddressProvider}
   * @param partitionClientHandler {@link PartitionClientHandler} instance
   * @param partitionServerHandler {@link PartitionServerHandler} instance
   * @param exceptionHandler {@link ExceptionHandler} instance
   * @param clientNumThreads the number of threads for client {@link ThreadPoolStage}
   * @return Wake {@link Transport}
   */
  private Transport createTransport(final String localAddress,
                                    final PartitionClientHandler partitionClientHandler,
                                    final PartitionServerHandler partitionServerHandler,
                                    final ExceptionHandler exceptionHandler,
                                    final int clientNumThreads) {
    final Injector transportInjector = Tang.Factory.getTang().newInjector();
    transportInjector.bindVolatileParameter(RemoteConfiguration.HostAddress.class, localAddress);
    // NettyMessagingTransport randomly assigns a port number when port is 0
    transportInjector.bindVolatileParameter(RemoteConfiguration.Port.class, 0);
    // We need a separate thread pool for reading partition and deserializing it.
    transportInjector.bindVolatileParameter(RemoteConfiguration.RemoteClientStage.class,
        new ThreadPoolStage(partitionClientHandler, clientNumThreads));
    // We rely on asynchronicity provided by PartitionStore
    transportInjector.bindVolatileParameter(RemoteConfiguration.RemoteServerStage.class,
        new SyncStage<>(partitionServerHandler));
    try {
      final Transport messagingTransport = transportInjector.getInstance(NettyMessagingTransport.class);
      messagingTransport.registerErrorHandler(exceptionHandler);
      return messagingTransport;
    } catch (final InjectionException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Fetches a partition asynchronously.
   *
   * @param remoteExecutorId id of the remote executor
   * @param partitionId      id of the partition
   * @param runtimeEdgeId    id of the {@link edu.snu.vortex.runtime.common.plan.RuntimeEdge}
   *                         corresponds to the partition
   * @param partitionStore   type of the partition store
   * @return {@link CompletableFuture} for the partition
   */
  CompletableFuture<Iterable<Element>> fetch(final String remoteExecutorId,
                                             final String partitionId,
                                             final String runtimeEdgeId,
                                             final Attribute partitionStore) {
    final Identifier remotePeerIdentifier = new PartitionTransferPeerIdentifier(remoteExecutorId);
    final InetSocketAddress remoteAddress;
    final Coder coder = partitionManagerWorker.get().getCoder(runtimeEdgeId);
    try {
      remoteAddress = nameResolver.lookup(remotePeerIdentifier);
    } catch (final Exception e) {
      LOG.log(Level.SEVERE, "Cannot lookup PartitionTransferPeer {0}", remotePeerIdentifier);
      throw new NodeConnectionException(e);
    }
    LOG.log(Level.INFO, "Looked up {0}", remoteAddress);

    final Link<ControlMessage.RequestPartitionMsg> link;
    try {
      link = transport.open(remoteAddress, REQUEST_MESSAGE_CODEC, LINK_LISTENER);
    } catch (final IOException e) {
      throw new NodeConnectionException(e);
    }
    final long requestId = requestIdCounter.getAndIncrement();
    requestIdToCoder.put(requestId, coder);
    final CompletableFuture<Iterable<Element>> future = replyFutureMap.beforeRequest(requestId);
    final ControlMessage.RequestPartitionMsg msg = ControlMessage.RequestPartitionMsg.newBuilder()
        .setRequestId(requestId)
        .setPartitionId(partitionId)
        .setRuntimeEdgeId(runtimeEdgeId)
        .setPartitionStore(convertPartitionStore(partitionStore))
        .build();
    link.write(msg);

    LOG.log(Level.INFO, "Wrote request {0}", msg);
    return future;
  }

  /**
   * A {@link Codec} implementation for {@link ControlMessage.RequestPartitionMsg}.
   */
  private static final class RequestPartitionMessageCodec implements Codec<ControlMessage.RequestPartitionMsg> {
    @Override
    public byte[] encode(final ControlMessage.RequestPartitionMsg msg) {
      return msg.toByteArray();
    }

    @Override
    public ControlMessage.RequestPartitionMsg decode(final byte[] bytes) {
      try {
        return ControlMessage.RequestPartitionMsg.parseFrom(bytes);
      } catch (final InvalidProtocolBufferException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * An {@link EventHandler} for incoming requests for {@link PartitionTransferPeer}.
   */
  private static final class PartitionServerHandler implements EventHandler<TransportEvent> {
    private final InjectionFuture<PartitionManagerWorker> partitionManagerWorker;

    @Inject
    private PartitionServerHandler(final InjectionFuture<PartitionManagerWorker> partitionManagerWorker) {
      this.partitionManagerWorker = partitionManagerWorker;
    }

    @Override
    public void onNext(final TransportEvent transportEvent) {
      final PartitionManagerWorker worker = partitionManagerWorker.get();
      final ControlMessage.RequestPartitionMsg request = REQUEST_MESSAGE_CODEC.decode(transportEvent.getData());

      // TODO #299: Separate Serialization from Here
      // At now, we do unneeded deserialization and serialization for already serialized data.
      final Coder coder = worker.getCoder(request.getRuntimeEdgeId());

      // We are getting the partition from local store!
      final CompletableFuture<Iterable<Element>> partitionFuture = worker.getPartition(request.getPartitionId(),
          request.getRuntimeEdgeId(), convertPartitionStoreType(request.getPartitionStore()));

      partitionFuture.thenAccept(partition -> {
        int numOfElements = 0;
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             final ByteArrayOutputStream elementsOutputStream = new ByteArrayOutputStream();
             final DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
          for (final Element element : partition) {
            coder.encode(element, elementsOutputStream);
            numOfElements++;
          }
          dataOutputStream.writeLong(request.getRequestId());
          dataOutputStream.writeInt(numOfElements);
          elementsOutputStream.writeTo(outputStream);
          transportEvent.getLink().write(outputStream.toByteArray());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }

  /**
   * An {@link EventHandler} for response from {@link PartitionServerHandler}.
   */
  private static final class PartitionClientHandler implements EventHandler<TransportEvent> {
    private final InjectionFuture<PartitionTransferPeer> partitionTransferPeer;

    @Inject
    private PartitionClientHandler(final InjectionFuture<PartitionTransferPeer> partitionTransferPeer) {
      this.partitionTransferPeer = partitionTransferPeer;
    }

    @Override
    public void onNext(final TransportEvent transportEvent) {
      final PartitionTransferPeer peer = partitionTransferPeer.get();

      final byte[] bytes = transportEvent.getData();

      try (final InputStream inputStream = new ByteArrayInputStream(bytes);
           final DataInputStream dataInputStream = new DataInputStream(inputStream)) {
        final long requestId = dataInputStream.readLong();
        final Coder coder = peer.requestIdToCoder.remove(requestId);
        final int numOfElements = dataInputStream.readInt();

        final ArrayList<Element> data = new ArrayList<>(numOfElements);
        for (int i = 0; i < numOfElements; i++) {
          data.add(coder.decode(inputStream));
        }

        peer.replyFutureMap.onSuccessMessage(requestId, data);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * An {@link EventHandler} for {@link Exception}s during partition transfer.
   */
  private static final class ExceptionHandler implements EventHandler<Exception> {
    @Inject
    private ExceptionHandler() {
    }

    @Override
    public void onNext(final Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  private static ControlMessage.PartitionStore convertPartitionStore(final Attribute partitionStore) {
    switch (partitionStore) {
      case Memory:
        return ControlMessage.PartitionStore.MEMORY;
      case LocalFile:
        return ControlMessage.PartitionStore.LOCAL_FILE;
      case RemoteFile:
        return ControlMessage.PartitionStore.REMOTE_FILE;
      default:
        throw new UnsupportedPartitionStoreException(new Exception(partitionStore + " is not supported."));
    }
  }

  private static Attribute convertPartitionStoreType(final ControlMessage.PartitionStore partitionStoreType) {
    switch (partitionStoreType) {
      case MEMORY:
        return Attribute.Memory;
      case LOCAL_FILE:
        return Attribute.LocalFile;
      case REMOTE_FILE:
        return Attribute.RemoteFile;
      default:
        throw new UnsupportedPartitionStoreException(new Throwable("This partition store is not yet supported"));
    }
  }
}
