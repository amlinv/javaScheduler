package com.amlinv.javasched.engine;

import com.amlinv.javasched.SchedulerProcess;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class StandardProcessExecutionSlipTest {
  private static final Logger LOG = LoggerFactory.getLogger(StandardProcessExecutionSlipTest.class);

  private StandardProcessExecutionSlip executionSlip;

  private SchedulerProcess mockProcess;
  private StandardProcessExecutionSlip.ValidationHooks validationHooks;

  @Before
  public void setupTest() throws Exception {
    this.mockProcess = Mockito.mock(SchedulerProcess.class);
    this.validationHooks = Mockito.mock(StandardProcessExecutionSlip.ValidationHooks.class);

    this.executionSlip = new StandardProcessExecutionSlip(this.mockProcess);
    this.executionSlip.setValidationHook(this.validationHooks);
  }

  @Test
  public void testGetSchedulerProcess() throws Exception {
    assertEquals(this.mockProcess, this.executionSlip.getSchedulerProcess());
  }

  @Test
  public void testWaitUntilCompleteAfterStopped() throws Exception {
    this.doTestWaitUntilComplete(false, new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        executionSlip.waitUntilComplete();
        return null;
      }
    });
  }

  @Test
  public void testWaitUntilCompleteWhileActive() throws Exception {
    this.doTestWaitUntilComplete(true, new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        executionSlip.waitUntilComplete();
        return null;
      }
    });
  }

  @Test
  public void testWaitUntilCompleteWithTimeout() throws Exception {
    this.doTestWaitUntilComplete(true, new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        executionSlip.waitUntilComplete(1000, 0);
        return null;
      }
    });
  }

  @Test
  public void testTimeoutOnWaitUntilComplete() throws Exception {
    final CountDownLatch waitDoneLatch = new CountDownLatch(1);

    Runnable waitRunnable = new Runnable() {
      @Override
      public void run() {
        try {
          executionSlip.waitUntilComplete(100, 0);
          waitDoneLatch.countDown();
        } catch ( Exception exc ) {
          LOG.warn("wait until complete exception on wait", exc);
        }
      }
    };
    Thread waitThread = new Thread(waitRunnable);

    this.executionSlip.processStarted();

    waitThread.start();
    Mockito.verify(this.validationHooks, Mockito.timeout(250)).onWaitForCompleteTimeoutExpired();

    assertTrue(waitDoneLatch.await(1000, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testIsRunnable() throws Exception {
    assertTrue(this.executionSlip.isRunnable());

    this.executionSlip.processStarted();
    assertTrue(this.executionSlip.isRunnable());

    this.executionSlip.processStopped();
    assertFalse(this.executionSlip.isRunnable());
  }

  @Test
  public void testCoverStandardValidationHooks() throws Exception {
    this.executionSlip = new StandardProcessExecutionSlip(this.mockProcess);
    this.executionSlip.waitUntilComplete(10, 0);
  }

  /**
   * Test a scenario of waiting for the process to complete.  Does not handle timeout-on-wait
   * scenarios.
   *
   * @param activeInd whether the waitCallable should execute while the process is active or not.
   * @param waitCallable callable that executes the waitUntilComplete() call, with or without
   *                     timeout.
   * @throws Exception
   */
  protected void doTestWaitUntilComplete (boolean activeInd, final Callable<Void> waitCallable)
      throws Exception {

    final CountDownLatch waitDoneLatch = new CountDownLatch(1);

    Runnable waitRunnable = new Runnable() {
      @Override
      public void run() {
        try {
          waitCallable.call();
          waitDoneLatch.countDown();
        } catch ( Exception exc ) {
          LOG.warn("wait until complete exception on wait", exc);
        }
      }
    };
    Thread waitThread = new Thread(waitRunnable);

    this.executionSlip.processStarted();

    // If waiting while inactive, indicate the stop now.
    if (! activeInd) {
      this.executionSlip.processStopped();
    }

    waitThread.start();

    // If waiting while active, verify the wait hook so we know the code properly executed the wait,
    // and then indicate the stop.
    if (activeInd) {
      Mockito.verify(this.validationHooks, Mockito.timeout(1000)).onStartWaitForCompletion();
      this.executionSlip.processStopped();
    }

    assertTrue(waitDoneLatch.await(1000, TimeUnit.MILLISECONDS));
  }
}