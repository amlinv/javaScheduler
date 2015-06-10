package com.amlinv.javasched.process;

import com.amlinv.javasched.Step;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class SingleRepeatingStepProcessTest {
  private SingleRepeatingStepProcess process;

  private Step mockStep;

  @Before
  public void setupTest() throws Exception {
    this.mockStep = Mockito.mock(Step.class);

    this.process = new SingleRepeatingStepProcess(this.mockStep);
  }

  @Test
  public void testSetGetRepeatOnlyWhenSignalled() throws Exception {
    assertTrue(this.process.isRepeatOnlyWhenSignalled());

    this.process.setRepeatOnlyWhenSignalled(false);
    assertFalse(this.process.isRepeatOnlyWhenSignalled());
  }

  @Test
  public void testGetNextStepBeforeSignalled() throws Exception {
    Step result = this.process.getNextStep();
    assertNotSame(this.mockStep, result);
    assertTrue(result instanceof SingleRepeatingStepProcess.WaitForSignalStep);
  }

  @Test
  public void testGetNextStepAfterSignalled() throws Exception {
    this.process.signal();
    Step result = this.process.getNextStep();
    assertSame(this.mockStep, result);

    result = this.process.getNextStep();
    assertNotSame(this.mockStep, result);
    assertTrue(result instanceof SingleRepeatingStepProcess.WaitForSignalStep);
  }

  @Test
  public void testGetNextStepAfterDuplicateSignal() throws Exception {
    this.process.signal();
    this.process.signal();
    Step result = this.process.getNextStep();
    assertSame(this.mockStep, result);

    result = this.process.getNextStep();
    assertNotSame(this.mockStep, result);
    assertTrue(result instanceof SingleRepeatingStepProcess.WaitForSignalStep);
  }

  @Test
  public void testGetNextStepTwice() throws Exception {
    this.process.signal();
    Step result = this.process.getNextStep();
    assertSame(this.mockStep, result);

    this.process.signal();
    result = this.process.getNextStep();
    assertSame(this.mockStep, result);

    result = this.process.getNextStep();
    assertNotSame(this.mockStep, result);
    assertTrue(result instanceof SingleRepeatingStepProcess.WaitForSignalStep);
  }

  @Test
  public void testRepeatOnlyWhenSignalledFalse() {
    this.process = new SingleRepeatingStepProcess(this.mockStep, false);

    Step result = this.process.getNextStep();
    assertSame(this.mockStep, result);

    result = this.process.getNextStep();
    assertSame(this.mockStep, result);

    result = this.process.getNextStep();
    assertSame(this.mockStep, result);
  }

  @Test
  public void testWaitUntilSignalledStep() throws Exception {
    final Step waitStep = this.process.getNextStep();
    final CountDownLatch latch = new CountDownLatch(1);

    assertTrue(waitStep.isBlocking());
    Thread execThread = new Thread() {
      @Override
      public void run() {
        waitStep.execute();
        latch.countDown();
      }
    };
    execThread.start();

    assertFalse(latch.await(100, TimeUnit.MILLISECONDS));
    this.process.signal();

    assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testWaitUntilSignalledStepWaitInterrupted() throws Exception {
    final Step waitStep = this.process.getNextStep();
    final CountDownLatch latch = new CountDownLatch(1);
    final Exception[] exceptionSite = new Exception[1];

    Thread execThread = new Thread() {
      @Override
      public void run() {
        try {
          waitStep.execute();
        } catch (Exception exc) {
          exceptionSite[0] = exc;
        }
        latch.countDown();
      }
    };
    execThread.start();

    execThread.interrupt();

    assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
    assertTrue(exceptionSite[0] instanceof RuntimeException);
    assertTrue(exceptionSite[0].getCause() instanceof InterruptedException);
  }

  @Test(timeout = 1000L)
  public void testShutdownWhileWaitingForSignal() {
    Step waitStep = this.process.getNextStep();

    this.process.shutdown();

    waitStep.execute();
  }

  @Test
  public void testGetNextStepAfterShutdown() {
    this.process.shutdown();

    assertNull(this.process.getNextStep());
  }
}
