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
package edu.snu.vortex.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.snu.vortex.compiler.backend.Backend;
import edu.snu.vortex.compiler.backend.vortex.VortexBackend;
import edu.snu.vortex.compiler.frontend.Frontend;
import edu.snu.vortex.compiler.frontend.beam.BeamFrontend;
import edu.snu.vortex.compiler.optimizer.Optimizer;
import edu.snu.vortex.runtime.common.RuntimeAttribute;
import edu.snu.vortex.runtime.common.message.MessageEnvironment;
import edu.snu.vortex.runtime.common.message.local.LocalMessageDispatcher;
import edu.snu.vortex.runtime.common.message.local.LocalMessageEnvironment;
import edu.snu.vortex.runtime.common.plan.logical.ExecutionPlan;
import edu.snu.vortex.runtime.master.BlockManagerMaster;
import edu.snu.vortex.runtime.master.RuntimeConfiguration;
import edu.snu.vortex.runtime.master.resourcemanager.LocalResourceManager;
import edu.snu.vortex.runtime.master.resourcemanager.ResourceManager;
import edu.snu.vortex.runtime.master.scheduler.BatchScheduler;
import edu.snu.vortex.runtime.master.scheduler.Scheduler;
import edu.snu.vortex.utils.dag.DAG;
import org.apache.reef.client.DriverConfiguration;
import org.apache.reef.client.DriverLauncher;
import org.apache.reef.client.LauncherStatus;
import org.apache.reef.tang.*;
import org.apache.reef.tang.exceptions.InjectionException;
import org.apache.reef.tang.formats.CommandLine;
import org.apache.reef.util.EnvironmentUtils;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.snu.vortex.compiler.optimizer.Optimizer.POLICY_NAME;

/**
 * Job launcher.
 */
public final class JobLauncher {
  private static final Logger LOG = Logger.getLogger(JobLauncher.class.getName());

  /**
   * private constructor.
   */
  private JobLauncher() {
  }

  /**
   * Main JobLauncher method.
   * @param args arguments.
   * @throws Exception exception on the way.
   */
  public static void main(final String[] args) throws Exception {
    final Configuration configuration = getJobConf(args);
    final Injector injector = Tang.Factory.getTang().newInjector(configuration);

    final Frontend frontend = new BeamFrontend();
    final Optimizer optimizer = new Optimizer();
    final Backend<ExecutionPlan> backend = new VortexBackend();

    final String dagDirectory = injector.getNamedInstance(JobConf.DAGDirectory.class);

    /**
     * Step 1: Compile
     */
    LOG.log(Level.INFO, "##### VORTEX Compiler #####");
    final String className = injector.getNamedInstance(JobConf.UserMainClass.class);
    final String[] arguments = injector.getNamedInstance(JobConf.UserMainArguments.class).split(" ");
    final DAG dag = frontend.compile(className, arguments);
    dag.storeJSON(dagDirectory, "ir", "IR before optimization");

    final String policyName = injector.getNamedInstance(JobConf.OptimizationPolicy.class);
    final Optimizer.PolicyType optimizationPolicy = POLICY_NAME.get(policyName);
    final DAG optimizedDAG = optimizer.optimize(dag, optimizationPolicy);
    optimizedDAG.storeJSON(dagDirectory, "ir-" + optimizationPolicy, "IR optimized for " + optimizationPolicy);

    final ExecutionPlan executionPlan = backend.compile(optimizedDAG);
    executionPlan.getRuntimeStageDAG().storeJSON(dagDirectory, "plan", "execution plan by compiler");

    /**
     * Step 2: Execute
     */
    LOG.log(Level.INFO, "##### VORTEX Runtime #####");
    // Initialize Runtime Components
    final RuntimeConfiguration runtimeConfiguration = readConfiguration();
    final Scheduler scheduler = new BatchScheduler(RuntimeAttribute.RoundRobin,
        runtimeConfiguration.getDefaultScheduleTimeout());
    final LocalMessageDispatcher localMessageDispatcher = new LocalMessageDispatcher();
    final MessageEnvironment masterMessageEnvironment =
        new LocalMessageEnvironment(MessageEnvironment.MASTER_COMMUNICATION_ID, localMessageDispatcher);
    final BlockManagerMaster blockManagerMaster = new BlockManagerMaster();
    final ResourceManager resourceManager = new LocalResourceManager(localMessageDispatcher);

    // Initialize RuntimeMaster and Execute!
    launchREEFJob()
        /*
    new RuntimeMaster(
        runtimeConfiguration,
        scheduler,
        localMessageDispatcher,
        masterMessageEnvironment,
        blockManagerMaster,
        resourceManager).execute(executionPlan, dagDirectory);
        */
  }

  /**
   * Retrieves job configuration using Tang.
   * @param args arguments.
   * @return Configuration.
   * @throws IOException IOException.
   * @throws InjectionException InjectionException.
   */
  public static Configuration getJobConf(final String[] args) throws IOException, InjectionException {
    final JavaConfigurationBuilder confBuilder = Tang.Factory.getTang().newConfigurationBuilder();
    final CommandLine cl = new CommandLine(confBuilder);
    cl.registerShortNameOfClass(JobConf.UserMainClass.class);
    cl.registerShortNameOfClass(JobConf.UserMainArguments.class);
    cl.registerShortNameOfClass(JobConf.DAGDirectory.class);
    cl.registerShortNameOfClass(JobConf.OptimizationPolicy.class);
    cl.processCommandLine(args);
    return confBuilder.build();
  }

  private static RuntimeConfiguration readConfiguration() {
    final ObjectMapper objectMapper = new ObjectMapper();
    final File configurationFile = new File("src/main/resources/configuration/RuntimeConfiguration.json");
    final RuntimeConfiguration configuration;
    try {
      configuration = objectMapper.readValue(configurationFile, RuntimeConfiguration.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read configuration file", e);
    }
    return configuration;
  }


  public static LauncherStatus launchREEFJob(final Configuration jobConf) throws InjectionException {
    // Get Driver Conf
    final Injector injector = Tang.Factory.getTang().newInjector(jobConf);
    final String className = injector.getNamedInstance(VortexJobConf.UserMainClass.class);
    final String[] classNameArray = className.split("\\.");
    final int driverMemory = injector.getNamedInstance(VortexJobConf.DriverMem.class);
    final Configuration driverConf = getDriverConf(classNameArray[classNameArray.length - 1], driverMemory);

    // Merge Job Conf and Driver Conf
    final Configuration jobAndDriverConf = Configurations.merge(jobConf, driverConf);

    // Get Runtime Conf
    final String runtime = injector.getNamedInstance(VortexJobConf.Runtime.class);
    final Configuration runtimeConf;
    switch (runtime) {
      case "local":
        runtimeConf = LocalRuntimeConfiguration.CONF
            .set(LocalRuntimeConfiguration.MAX_NUMBER_OF_EVALUATORS, 20)
            .build();
        break;
      case "yarn":
        runtimeConf = YarnClientConfiguration.CONF
            .set(YarnClientConfiguration.JVM_HEAP_SLACK, 0.2)
            .build();
        break;
      default:
        throw new RuntimeException("Unknown runtime: " + runtime);
    }

    // Launch!
    return DriverLauncher.getLauncher(runtimeConf).run(jobAndDriverConf);
  }

  public static Configuration getDriverConf(final String jobName, final int driverMemory) {
    return DriverConfiguration.CONF
        .set(DriverConfiguration.GLOBAL_LIBRARIES, EnvironmentUtils.getClassLocation(VortexDriver.class))
        .set(DriverConfiguration.ON_DRIVER_STARTED, VortexDriver.StartHandler.class)
        .set(DriverConfiguration.ON_EVALUATOR_ALLOCATED, VortexDriver.AllocatedEvaluatorHandler.class)
        .set(DriverConfiguration.ON_CONTEXT_ACTIVE, VortexDriver.ActiveContextHandler.class)
        .set(DriverConfiguration.ON_CONTEXT_MESSAGE, VortexDriver.ContextMessageHandler.class)
        .set(DriverConfiguration.ON_TASK_RUNNING, VortexDriver.RunningTaskHandler.class)
        .set(DriverConfiguration.ON_TASK_MESSAGE, VortexDriver.TaskMessageHandler.class)
        .set(DriverConfiguration.ON_EVALUATOR_FAILED, VortexDriver.FailedEvaluatorHandler.class)
        .set(DriverConfiguration.ON_DRIVER_STOP, VortexDriver.DriverStopHandler.class)
        .set(DriverConfiguration.DRIVER_IDENTIFIER, jobName)
        .set(DriverConfiguration.DRIVER_MEMORY, driverMemory)
        .build();
  }
}


}
