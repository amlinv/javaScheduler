package com.amlinv.javasched.process;

import com.amlinv.javasched.Step;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Created by art on 6/9/15.
 */
public class StepListSchedulerProcessTest {

  private static final Logger log = LoggerFactory.getLogger(StepListSchedulerProcessTest.class);

  private StepListSchedulerProcess process;
  private Step mockStep;
  private Logger mockLogger;

  private StepListSchedulerProcess.ValidationHook validationHook;

  @Before
  public void setupTest() throws Exception {
    this.process = new StepListSchedulerProcess();

    this.mockStep = Mockito.mock(Step.class);
    this.validationHook = Mockito.mock(StepListSchedulerProcess.ValidationHook.class);
    this.mockLogger = Mockito.mock(Logger.class);

    this.process.setValidationHook(this.validationHook);
  }

  @Test
  public void testGetStepCount() {
    assertEquals(0, this.process.getPendingStepCount());

    this.process.addStep(this.mockStep);
    assertEquals(1, this.process.getPendingStepCount());

    this.process.addStep(Mockito.mock(Step.class));
    assertEquals(2, this.process.getPendingStepCount());

    this.process.getNextStep();
    assertEquals(1, this.process.getPendingStepCount());

    this.process.getNextStep();
    assertEquals(0, this.process.getPendingStepCount());
  }

  @Test
  public void testIsAutoStop() throws Exception {
    assertFalse(this.process.isAutoStop());

    this.process.setAutoStop(true);
    assertTrue(this.process.isAutoStop());
  }

  @Test
  public void testGetNextStep() throws Exception {
    this.process.setAutoStop(true);
    this.process.addStep(this.mockStep);

    Step result = this.process.getNextStep();
    assertSame(this.mockStep, result);

    result = this.process.getNextStep();
    assertNull(result);
  }

  @Test
  public void testGetNextStepAfterStopped() throws Exception {
    Step result;

    // Add a step, shutdown the process, then verify the added step is not returned.
    this.process.addStep(this.mockStep);
    this.process.shutdown();

    result = this.process.getNextStep();
    assertNull(result);
  }

  @Test
  public void testGetNextStepNoMoreStepsWithAutoStop() throws Exception {
    this.process.setAutoStop(true);
    Step result = this.process.getNextStep();

    assertNull(result);
    assertTrue(this.process.isStopped());
  }

  @Test
  public void testGetNextStepNoMoreStepsWithoutAutoStop() throws Exception {
    this.process.setAutoStop(false);
    Step result = this.process.getNextStep();

    assertTrue(result instanceof StepListSchedulerProcess.WaitForMoreWorkStep);
    assertFalse(this.process.isStopped());
  }

  @Test
  public void testWaitUntilFinished() throws Exception {
    final CountDownLatch finishedLatch = new CountDownLatch(1);

    Thread waitThread = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          process.waitUntilFinished();
          finishedLatch.countDown();
        } catch (InterruptedException intExc) {
          log.error("wait until finished interrupted", intExc);
        }
      }
    });

    waitThread.start();

    // Wait for the thread to reach the wait on the queue.
    Mockito.verify(this.validationHook, Mockito.timeout(100)).onWaitOnQueue();

    // Now verify the process is still active and then initiate the shutdown.
    assertFalse(this.process.isStopped());
    this.process.shutdown();

    // Give it a little time to shutdown.
    assertTrue(finishedLatch.await(1000, TimeUnit.MILLISECONDS));
    assertTrue(this.process.isStopped());
  }

  /**
   * Test the wait for a step when the step queue is not empty.
   *
   * Warning: this sounds counter-intuitive because it's testing a race condition.
   */
  @Test(timeout = 1000)
  public void testWaitForStepOnQueueNotEmpty() {
    Step waitWorkStep = this.process.getNextStep();

    this.process.addStep(this.mockStep);
    waitWorkStep.execute();
  }

  @Test(timeout = 5000)
  public void testWaitForStep() throws Exception {
    this.doWaitForStepTest(true, false);
  }

  @Test
  public void testWaitForStepOnShutdown() throws Exception {
    this.doWaitForStepTest(false, false);
  }

  @Test
  public void testWaitForStepOnShutdownWithInterrupt() throws Exception {
    this.doWaitForStepTest(false, true);
  }

  @Test
  public void testWaitForStepIsBlocking() {
    this.process.setAutoStop(false);
    Step result = this.process.getNextStep();
    assertTrue(result.isBlocking());
  }

  @Test
  public void testCoverageOnValidationHooks() throws InterruptedException {
    StepListSchedulerProcess.ValidationHook hook = new StepListSchedulerProcess.ValidationHook();
    hook.onWaitOnQueue();
  }

  protected void doWaitForStepTest(boolean onAdd, boolean withInterrupt) throws Exception {
    final CountDownLatch finishedLatch = new CountDownLatch(1);
    this.process.setAutoStop(false);
    final Step waitWorkStep = this.process.getNextStep();

    Thread waitThread = new Thread(new Runnable() {
      @Override
      public void run() {
        waitWorkStep.execute();
        finishedLatch.countDown();
      }
    });

    int expectedHookCount = 1;
    if (withInterrupt) {
      this.process.setLog(this.mockLogger);

      // Throw the exception on the first call only; otherwise, the loop will iterate and repeat
      // the log line many times.
      Mockito
          .doThrow(new InterruptedException())
          .doNothing()
          .when(this.validationHook).onWaitOnQueue();

      expectedHookCount++;
    }

    waitThread.start();

    // Wait a little while to reach the wait call.
    Mockito.verify(this.validationHook, Mockito.timeout(100).times(expectedHookCount))
        .onWaitOnQueue();

    if (onAdd) {
      // Add a step, verify the wait completes, and the next step returned is the one added.
      this.process.addStep(this.mockStep);
      assertTrue(finishedLatch.await(1000, TimeUnit.MILLISECONDS));
      assertSame(this.mockStep, this.process.getNextStep());
    } else {
      // Initiate shutdown and verify the wait finishes.
      this.process.shutdown();
      assertTrue(finishedLatch.await(1000, TimeUnit.MILLISECONDS));
      assertTrue(this.process.isStopped());
    }

    if (withInterrupt) {
      Mockito.verify(this.mockLogger, Mockito.times(1))
          .info("wait for more work interrupted; checking for more work and possible shutdown now");
    }
  }
}
