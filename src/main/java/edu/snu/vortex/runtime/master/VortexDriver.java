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
package edu.snu.vortex.runtime.master;

import edu.snu.vortex.client.JobConf;
import edu.snu.vortex.runtime.common.RuntimeIdGenerator;
import edu.snu.vortex.runtime.common.message.MessageEnvironment;
import edu.snu.vortex.runtime.common.message.ncs.NcsMessageEnvironment;
import edu.snu.vortex.runtime.common.message.ncs.NcsParameters;
import edu.snu.vortex.runtime.executor.VortexContext;
import edu.snu.vortex.runtime.master.resource.ContainerManager;
import edu.snu.vortex.runtime.master.resource.ResourceSpecification;
import edu.snu.vortex.runtime.master.scheduler.Scheduler;
import org.apache.reef.annotations.audience.DriverSide;
import org.apache.reef.driver.context.ActiveContext;
import org.apache.reef.driver.context.ContextConfiguration;
import org.apache.reef.driver.context.FailedContext;
import org.apache.reef.driver.evaluator.AllocatedEvaluator;
import org.apache.reef.driver.evaluator.FailedEvaluator;
import org.apache.reef.io.network.naming.NameServer;
import org.apache.reef.io.network.naming.parameters.NameResolverNameServerAddr;
import org.apache.reef.io.network.naming.parameters.NameResolverNameServerPort;
import org.apache.reef.io.network.util.StringIdentifierFactory;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.Configurations;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.annotations.Parameter;
import org.apache.reef.tang.annotations.Unit;
import org.apache.reef.wake.EventHandler;
import org.apache.reef.wake.IdentifierFactory;
import org.apache.reef.wake.remote.address.LocalAddressProvider;
import org.apache.reef.wake.time.event.StartTime;
import org.apache.reef.wake.time.event.StopTime;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonParser;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static edu.snu.vortex.compiler.ir.attribute.Attribute.*;

/**
 * REEF Driver for Vortex.
 */
@Unit
@DriverSide
public final class VortexDriver {
  private static final Logger LOG = Logger.getLogger(VortexDriver.class.getName());

  private final NameServer nameServer;
  private final LocalAddressProvider localAddressProvider;

  private final String resourceSpecificationString;

  private final UserApplicationRunner userApplicationRunner;
  private final ContainerManager containerManager;
  private final Scheduler scheduler;

  @Inject
  private VortexDriver(final ContainerManager containerManager,
                       final Scheduler scheduler,
                       final UserApplicationRunner userApplicationRunner,
                       final NameServer nameServer,
                       final LocalAddressProvider localAddressProvider,
                       @Parameter(JobConf.ExecutorJsonContents.class) final String resourceSpecificationString) {
    this.userApplicationRunner = userApplicationRunner;
    this.containerManager = containerManager;
    this.scheduler = scheduler;
    this.nameServer = nameServer;
    this.localAddressProvider = localAddressProvider;
    this.resourceSpecificationString = resourceSpecificationString;
  }

  /**
   * Driver started.
   */
  public final class StartHandler implements EventHandler<StartTime> {
    @Override
    public void onNext(final StartTime startTime) {
      final JsonParser parser = Json.createParser(new StringReader(resourceSpecificationString));
      Integer executorNum = null;
      ResourceSpecification.Builder builder = null;

      while (parser.hasNext()) {
        final JsonParser.Event event = parser.next();
        switch (event) {
          case START_OBJECT:
            executorNum = 1;
            builder = ResourceSpecification.newBuilder();
            break;

          case KEY_NAME:
            final String keyName = parser.getString();
            parser.next();
            switch (keyName) {
              case "num":
                executorNum = parser.getInt();
                break;
              case "type":
                builder.setContainerType(parser.getString());
                break;
              case "memory_mb":
                builder.setMemory(parser.getInt());
                break;
              case "capacity":
                builder.setCapacity(parser.getInt());
                break;
              default:
                throw new IllegalArgumentException("Unknown key for resource specification: " + keyName);
            }
            break;

          case END_OBJECT:
            // Launch resource(s)
            containerManager.requestContainer(executorNum, builder.build());
            break;
          default:
            break;
        }
      }

      // Launch user application (with a new thread)
      final ExecutorService userApplicationRunnerThread = Executors.newSingleThreadExecutor();
      userApplicationRunnerThread.execute(userApplicationRunner);
      userApplicationRunnerThread.shutdown();
    }
  }

  /**
   * Container allocated.
   */
  public final class AllocatedEvaluatorHandler implements EventHandler<AllocatedEvaluator> {
    @Override
    public void onNext(final AllocatedEvaluator allocatedEvaluator) {
      final String executorId = RuntimeIdGenerator.generateExecutorId();
      final int numOfCores = allocatedEvaluator.getEvaluatorDescriptor().getNumberOfCores();
      containerManager.onContainerAllocated(executorId, allocatedEvaluator,
          getExecutorConfiguration(executorId, numOfCores));
    }
  }

  /**
   * Context active.
   */
  public final class ActiveContextHandler implements EventHandler<ActiveContext> {
    @Override
    public void onNext(final ActiveContext activeContext) {
      containerManager.onExecutorLaunched(activeContext);
      scheduler.onExecutorAdded(activeContext.getId());
    }
  }

  /**
   * Evaluator failed.
   */
  public final class FailedEvaluatorHandler implements EventHandler<FailedEvaluator> {
    @Override
    public void onNext(final FailedEvaluator failedEvaluator) {
      // The list size is 0 if the evaluator failed before an executor started. For now, the size is 1 otherwise.
      failedEvaluator.getFailedContextList().forEach(failedContext -> {
        final String failedExecutorId = failedContext.getId();
        containerManager.onExecutorRemoved(failedExecutorId);
        scheduler.onExecutorRemoved(failedExecutorId);
      });
      throw new RuntimeException(failedEvaluator.getId()
          + " failed. See driver's log for the stack trace in executor.");
    }
  }

  /**
   * Context failed.
   */
  public final class FailedContextHandler implements EventHandler<FailedContext> {
    @Override
    public void onNext(final FailedContext failedContext) {
      throw new RuntimeException(failedContext.getId() + " failed. See driver's log for the stack trace in executor.",
          failedContext.asError());
    }
  }

  /**
   * Driver stopped.
   */
  public final class DriverStopHandler implements EventHandler<StopTime> {
    @Override
    public void onNext(final StopTime stopTime) {
    }
  }

  private Configuration getExecutorConfiguration(final String executorId, final int executorCapacity) {
    final Configuration executorConfiguration = JobConf.EXECUTOR_CONF
        .set(JobConf.EXECUTOR_ID, executorId)
        .set(JobConf.EXECUTOR_CAPACITY, executorCapacity)
        .build();

    final Configuration contextConfiguration = ContextConfiguration.CONF
        .set(ContextConfiguration.IDENTIFIER, executorId) // We set: contextId = executorId
        .set(ContextConfiguration.ON_CONTEXT_STARTED, VortexContext.ContextStartHandler.class)
        .build();

    final Configuration ncsConfiguration =  getExecutorNcsConfiguration();
    final Configuration messageConfiguration = getExecutorMessageConfiguration(executorId);

    return Configurations.merge(executorConfiguration, contextConfiguration, ncsConfiguration, messageConfiguration);
  }

  private Configuration getExecutorNcsConfiguration() {
    return Tang.Factory.getTang().newConfigurationBuilder()
        .bindNamedParameter(NameResolverNameServerPort.class, Integer.toString(nameServer.getPort()))
        .bindNamedParameter(NameResolverNameServerAddr.class, localAddressProvider.getLocalAddress())
        .bindImplementation(IdentifierFactory.class, StringIdentifierFactory.class)
        .build();
  }

  private Configuration getExecutorMessageConfiguration(final String executorId) {
    return Tang.Factory.getTang().newConfigurationBuilder()
        .bindImplementation(MessageEnvironment.class, NcsMessageEnvironment.class)
        .bindNamedParameter(NcsParameters.SenderId.class, executorId)
        .build();
  }
}
