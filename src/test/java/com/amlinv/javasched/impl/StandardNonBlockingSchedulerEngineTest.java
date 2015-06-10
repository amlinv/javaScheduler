package com.amlinv.javasched.impl;

import com.amlinv.javasched.Step;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.*;

public class StandardNonBlockingSchedulerEngineTest {

  private StandardNonBlockingSchedulerEngine engine;

  private Step mockStep;
  private Logger mockLogger;

  @Before
  public void setupTest() {
    this.engine = new StandardNonBlockingSchedulerEngine();

    this.mockStep = Mockito.mock(Step.class);
    this.mockLogger = Mockito.mock(Logger.class);
  }

  @After
  public void cleanup() {
    try {
      this.engine.initiateShutdown();
    } catch (Exception exc) {
    }
  }

  @Test
  public void testConstructorWithProcessorCount() {
    StandardNonBlockingSchedulerEngine engine1 = new StandardNonBlockingSchedulerEngine(3131);
    assertEquals(3131, engine1.getProcessorCount());
  }

  @Test
  public void testGetSetProcessorCount() throws Exception {
    if ( Runtime.getRuntime().availableProcessors() > 1 ) {
      assertEquals(Runtime.getRuntime().availableProcessors() - 1, this.engine.getProcessorCount());
    } else {
      assertEquals(1, this.engine.getProcessorCount());
    }

    this.engine.setProcessorCount(1313);
    assertEquals(1313, this.engine.getProcessorCount());
  }

  @Test
  public void testGetSetLog() throws Exception {
    assertNotNull(this.engine.getLog());
    assertNotSame(this.mockLogger, this.engine.getLog());

    this.engine.setLog(this.mockLogger);
    assertSame(this.mockLogger, this.engine.getLog());
  }

  @Test
  public void testStartStep() throws Exception {
    this.engine.start();

    this.engine.submit(this.mockStep);

    Mockito.verify(this.mockStep, Mockito.timeout(1000)).execute();
  }

  @Test
  public void testExceptionOnStep() throws Exception {
    RuntimeException rtExc = new RuntimeException("X-runtime-exception-X");
    Mockito.doThrow(rtExc).when(this.mockStep).execute();

    this.engine.setLog(this.mockLogger);

    this.engine.start();

    this.engine.submit(this.mockStep);

    Mockito.verify(this.mockLogger, Mockito.timeout(1000)).warn("failed step execution", rtExc);
  }

  @Test
  public void initiateShutdownOnIdleThread() {
    InterruptedException intExc = new InterruptedException("X-interrupted-X");

    //
    // EXECUTE
    //
    this.engine.setLog(this.mockLogger);
    this.engine.start();

    // Run one step through to ensure at least one processor thread is active.
    this.engine.submit(this.mockStep);
    Mockito.verify(this.mockStep, Mockito.timeout(1000)).execute();

    this.engine.initiateShutdown();

    Mockito.verify(this.mockLogger, Mockito.timeout(1000).times(this.engine.getProcessorCount()))
        .info(Mockito.eq("stopping processor thread #{} on interrupt"), Mockito.anyInt());
  }

  @Test
  public void testStartWhenAlreadyStarted() {
    this.engine.start();

    try {
      this.engine.start();
      fail("missing expected exception");
    } catch (IllegalStateException isExc) {
      assertEquals("already started", isExc.getMessage());
    }
  }

  @Test
  public void testStartWithBadProcessorCount() {
    try {
      this.engine.setProcessorCount(0);
      this.engine.start();
      fail("missing expected exception");
    } catch (IllegalStateException isExc) {
      assertEquals("processor count must be positive", isExc.getMessage());
    }

    try {
      this.engine.setProcessorCount(-1);
      this.engine.start();
      fail("missing expected exception");
    } catch (IllegalStateException isExc) {
      assertEquals("processor count must be positive", isExc.getMessage());
    }
  }

  @Test
  public void testShutdownBeforeStartup() {
    try {
      this.engine.initiateShutdown();
    } catch (IllegalStateException isExc) {
      assertEquals("not yet started", isExc.getMessage());
    }
  }

  @Test
  public void testShutdownAfterShutdown() {
    this.engine.start();
    this.engine.initiateShutdown();

    try {
      this.engine.initiateShutdown();
    } catch (IllegalStateException isExc) {
      assertEquals("already stopped", isExc.getMessage());
    }
  }

  @Test
  public void testSubmitBeforeStarted() {
    try {
      this.engine.submit(this.mockStep);
    } catch (IllegalStateException isExc) {
      assertEquals("not yet started", isExc.getMessage());
    }
  }

  @Test
  public void testSubmitAfterStopped() {
    this.engine.start();
    this.engine.initiateShutdown();

    try {
      this.engine.submit(this.mockStep);
    } catch (IllegalStateException isExc) {
      assertEquals("scheduler already stopped", isExc.getMessage());
    }
  }

  @Test
  public void testSubmitNullProcess() {
    this.engine.start();

    try {
      this.engine.submit(null);
      fail("missing expected exception");
    } catch ( NullPointerException npe ) {
    }
  }

  @Test
  public void testUnexpectedQueueFailure() throws Exception {
    LinkedBlockingQueue mockQueue = Mockito.mock(LinkedBlockingQueue.class);
    Mockito.when(mockQueue.offer(this.mockStep)).thenReturn(false);
    Mockito.when(mockQueue.take()).thenAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        synchronized (this) {
          this.wait();
        }
        return null;
      }
    });

    this.engine.injectQueueForTest(mockQueue);

    this.engine.start();

    try {
      this.engine.submit(this.mockStep);
      fail("missing expected exception");
    } catch (RuntimeException rtExc) {
      assertEquals("internal error: unbounded queue of steps rejected offered step",
                   rtExc.getMessage());
    }

    this.engine.initiateShutdown();
  }
}
