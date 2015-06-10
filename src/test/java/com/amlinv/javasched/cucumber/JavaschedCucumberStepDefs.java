package com.amlinv.javasched.cucumber;

import com.amlinv.javasched.SchedulerProcess;
import com.amlinv.javasched.Step;
import com.amlinv.javasched.impl.StandardBlockingSchedulerEngine;
import com.amlinv.javasched.impl.StandardNonBlockingSchedulerEngine;
import com.amlinv.javasched.impl.RoundRobinScheduler;
import com.amlinv.javasched.process.StepListSchedulerProcess;
import com.amlinv.javasched.testtool.TestFixedStepCountSchedulerProcess;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * NOTES on validating order of step execution:
 * <ul>
 *   <li>
 *     each step execution blocks until signaled by the test
 *   </li>
 *   <li>
 *     the test signals steps in order, and only signals each step after the prior step has
 *     completed
 *   </li>
 *   <li>
 *     by timing out the test, order of execution is guaranteed validated as long as steps execute
 *     one-at-a-time
 *   </li>
 *   <li>
 *     adding notification of the start of each step, before blocking for the test signal, it is
 *     possible to detect multiple simultaneous executions.  Using a process with a good number of
 *     steps (e.g. 10), there should be sufficient opportunity for the test to catch simultaneous
 *     step execution as the first step needs to go through a full cycle (1 - notify start,
 *     2 - wait for test signal, 3 - notify complete) while the others only need to get to the
 *     initial notification.
 *   </li>
 * </ul>
 *
 * Created by art on 12/9/14.
 */
public class JavaschedCucumberStepDefs {

  private static final Logger LOG = LoggerFactory.getLogger(JavaschedCucumberStepDefs.class);

  private RoundRobinScheduler scheduler;
  private List<TestFixedStepCountSchedulerProcess> testProcessList = new LinkedList<>();

  private Map<String, SchedulerProcess> namedProcesses = new TreeMap<>();

  @Given("^a scheduler$")
  public void a_scheduler() throws Throwable {
    this.scheduler = new RoundRobinScheduler();
  }

  @Then("^the scheduler should be created$")
  public void the_scheduler_should_be_created() throws Throwable {
    assertNotNull(this.scheduler);
  }

  @When(value = "^all processes are executed by the scheduler$", timeout = 1000L)
  public void the_process_is_executed_by_the_scheduler() throws Throwable {
    for (SchedulerProcess process : this.testProcessList) {
      this.scheduler.startProcess(process);
    }

    //
    // Start execution of all the processes.
    //
    for (TestFixedStepCountSchedulerProcess oneTestProcess : this.testProcessList) {
      oneTestProcess.startAutoExecuteTest();
    }
  }

  @Then("^all steps are completed in order$")
  public void the_steps_are_completed_in_order() throws Throwable {
    for (TestFixedStepCountSchedulerProcess process : this.testProcessList) {
      assertFalse(process.isFailure());
    }
  }

  @When("^I add a process with (\\d+) steps$")
  public void I_add_a_process_with_steps(final int numSteps) throws Throwable {
    TestFixedStepCountSchedulerProcess process = new TestFixedStepCountSchedulerProcess(numSteps);

    this.testProcessList.add(process);
  }

  @Then("^the scheduler is started$")
  public void the_scheduler_is_started() throws Throwable {
    this.scheduler.start();
  }

  @And("^all processes complete within (\\d+) milliseconds$")
  public void all_processes_complete_within_milliseconds(final int timeout) throws Throwable {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        for (TestFixedStepCountSchedulerProcess process : testProcessList) {
          try {
            LOG.debug("waiting for completion of process: {}", process);
            boolean finished = process.waitUntilFinished(timeout);
            assertTrue(finished);
          } catch (InterruptedException intExc) {
            throw new RuntimeException("wait interrupted", intExc);
          }
        }
      }
    };

    this.runTaskWithTimeoutCheck(timeout, runnable);
  }

  //////////////////////////////
  // BLOCKING AND NON-BLOCKING
  //////////////////////////////

  @Given("^a scheduler with (\\d+) processing threads? and (\\d+) blocking processing threads?$")
  public void a_scheduler_with_processing_thread_and_blocking_processing_thread(
      int numNonBlockingThread, int numBlockingThread)
      throws Throwable {

    StandardBlockingSchedulerEngine blockingEngine = new StandardBlockingSchedulerEngine();
    blockingEngine.setMaximumProcessors(numBlockingThread);

    StandardNonBlockingSchedulerEngine nonBlockingEngine = new StandardNonBlockingSchedulerEngine();
    nonBlockingEngine.setProcessorCount(numNonBlockingThread);

    this.scheduler = new RoundRobinScheduler();
    this.scheduler.setBlockingEngine(blockingEngine);
    this.scheduler.setNonBlockingEngine(nonBlockingEngine);
  }

  @And("^process \"([^\"]*)\" with a (\\d+) millisecond blocking step$")
  public void process_with_a_millisecond_blocking_step(String processName, int blockingPeriod)
      throws Throwable {

    StepListSchedulerProcess process = new StepListSchedulerProcess();
    process.addStep(this.createMillisecondBlockingStep(blockingPeriod));
    process.setAutoStop(true);

    this.namedProcesses.put(processName, process);
  }

  @And("^process \"([^\"]*)\" with a (\\d+) millisecond non-blocking step$")
  public void process_with_a_millisecond_non_blocking_step(String processName, int duration)
      throws Throwable {

    StepListSchedulerProcess process = new StepListSchedulerProcess();
    process.addStep(this.createMillisecondNonBlockingStep(duration));
    process.setAutoStop(true);

    this.namedProcesses.put(processName, process);
  }

  @Then("^start process \"([^\"]*)\"$")
  public void start_process(String processName) throws Throwable {
    this.scheduler.startProcess(this.namedProcesses.get(processName));
  }

  @When(value = "^the process \"([^\"]*)\" completes, process \"([^\"]*)\" is still running with timeout (\\d+)$")
  public void the_process_completes_process_is_still_running(String firstProcessName,
                                                             String secondProcessName,
                                                             long timeout)
      throws Throwable {

    final StepListSchedulerProcess firstProcess =
        (StepListSchedulerProcess) this.namedProcesses.get(firstProcessName);
    final StepListSchedulerProcess secondProcess =
        (StepListSchedulerProcess) this.namedProcesses.get(secondProcessName);

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        //
        // Verify the first finished and then the second is still active.
        //
        try {
          firstProcess.waitUntilFinished();
        } catch (InterruptedException intExc) {
          throw new RuntimeException("interrupted", intExc);
        }
        assertFalse(secondProcess.isStopped());
      }
    };

    this.runTaskWithTimeoutCheck(timeout, runnable);
  }

  ////////////////////
  // INTERNAL METHODS
  ////////////////////

  protected Step createMillisecondBlockingStep(final long delay) {
    return new Step() {
      @Override
      public void execute() {
        try {
          Thread.sleep(delay);
        } catch (InterruptedException intExc) {
          throw new RuntimeException("unexpectedly interrupted", intExc);
        }
      }

      @Override
      public boolean isBlocking() {
        return true;
      }
    };
  }

  protected Step createMillisecondNonBlockingStep(final long delay) {
    return new Step() {
      @Override
      public void execute() {
        try {
          // NOTE: YES, this does actually block.  It's a test depicting processing that takes
          //  some time to complete.  This is NOT a good role-model.
          Thread.sleep(delay);
        } catch (InterruptedException intExc) {
          throw new RuntimeException("unexpectedly interrupted", intExc);
        }
      }

      @Override
      public boolean isBlocking() {
        return false;
      }
    };
  }

  protected FutureTask<Void> startTaskWithTimeout(final long timeout, Runnable runnable) {
    FutureTask<Void> waitTask = new FutureTask<Void>(runnable, null);

    Thread waitThread = new Thread(waitTask);
    waitThread.start();

    return waitTask;
  }

  /**
   * Run the task in the given runnable with the timeout specified, throwing an exception on
   * timeout or task failure.
   *
   * @param timeout time, in milliseconds, to expire the task.
   * @param runnable definition of the steps to execute.
   * @throws Exception
   */
  protected void runTaskWithTimeoutCheck(long timeout, Runnable runnable) throws Exception {
    FutureTask<Void> waitTask = this.startTaskWithTimeout(timeout, runnable);

    waitTask.get(timeout, TimeUnit.MILLISECONDS);
  }
}
