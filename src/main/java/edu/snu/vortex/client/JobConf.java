package edu.snu.vortex.client;

import org.apache.reef.tang.annotations.Name;
import org.apache.reef.tang.annotations.NamedParameter;
import org.apache.reef.tang.formats.ConfigurationModule;
import org.apache.reef.tang.formats.ConfigurationModuleBuilder;
import org.apache.reef.tang.formats.OptionalParameter;
import org.apache.reef.tang.formats.RequiredParameter;

/**
 * Vortex Job Configurations.
 */
public final class JobConf extends ConfigurationModuleBuilder {

  //////////////////////////////// User Configurations

  /**
   * Job id.
   */
  @NamedParameter(doc = "Job id", short_name = "job_id")
  public final class JobId implements Name<String> {
  }

  /**
   * User Main Class Name.
   */
  @NamedParameter(doc = "User Main Class Name", short_name = "user_main")
  public final class UserMainClass implements Name<String> {
  }

  /**
   * User Main Arguments.
   */
  @NamedParameter(doc = "User Main Arguments", short_name = "user_args")
  public final class UserMainArguments implements Name<String> {
  }

  /**
   * Directory to store JSON representation of intermediate DAGs.
   */
  @NamedParameter(doc = "Directory to store intermediate DAGs", short_name = "dag_dir", default_value = "./target/dag")
  public final class DAGDirectory implements Name<String> {
  }

  //////////////////////////////// Compiler Configurations

  /**
   * Name of the optimization policy.
   */
  @NamedParameter(doc = "Name of the optimization policy", short_name = "optimization_policy", default_value = "none")
  public final class OptimizationPolicy implements Name<String> {
  }

  //////////////////////////////// Runtime Configurations

  /**
   * Vortex driver memory.
   */
  @NamedParameter(doc = "Vortex driver memory", short_name = "driver_mem_mb", default_value = "1024")
  public final class DriverMemMb implements Name<Integer> {
  }

  /**
   * Number of vortex executors.
   * TODO #205: Allow for Per-ResourceType Configurations
   */
  @NamedParameter(doc = "Number of vortex executors", short_name = "executor_num", default_value = "1")
  public final class ExecutorNum implements Name<Integer> {
  }

  /**
   * Vortex executor memory.
   * TODO #205: Allow for Per-ResourceType Configurations
   */
  @NamedParameter(doc = "Vortex executor memory", short_name = "executor_mem_mb", default_value = "1024")
  public final class ExecutorMemMb implements Name<Integer> {
  }

  /**
   * Vortex executor cores.
   * TODO #205: Allow for Per-ResourceType Configurations
   */
  @NamedParameter(doc = "Vortex executor cores", short_name = "executor_cores", default_value = "1")
  public final class ExecutorCores implements Name<Integer> {
  }

  /**
   * VortexExecutor capacity.
   * TODO #205: Allow for Per-ResourceType Configurations
   */
  @NamedParameter(doc = "VortexExecutor capacity", short_name = "executor_capacity", default_value = "1")
  public final class ExecutorCapacity implements Name<Integer> {
  }

  /**
   * Scheduler timeout in ms.
   */
  @NamedParameter(doc = "Scheduler timeout in ms", short_name = "scheduler_timeout_ms", default_value = "2000")
  public final class SchedulerTimeoutMs implements Name<Integer> {
  }

  /**
   * VortexExecutor id.
   */
  @NamedParameter(doc = "Executor id", short_name = "executor_id")
  public final class ExecutorId implements Name<String> {
  }

  public static final OptionalParameter<Integer> EXECUTOR_CAPACITY = new OptionalParameter<>();
  public static final RequiredParameter<String> EXECUTOR_ID = new RequiredParameter<>();

  public static final ConfigurationModule EXECUTOR_CONF = new JobConf()
      .bindNamedParameter(ExecutorId.class, EXECUTOR_ID)
      .bindNamedParameter(ExecutorCapacity.class, EXECUTOR_CAPACITY)
      .build();

}
