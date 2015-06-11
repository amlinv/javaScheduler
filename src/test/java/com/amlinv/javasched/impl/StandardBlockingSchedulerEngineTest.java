/*
 *   Copyright 2015 AML Innovation & Consulting LLC
 *
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.amlinv.javasched.impl;

import com.amlinv.javasched.Step;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 *
 */
public class StandardBlockingSchedulerEngineTest {

  private static final Logger
      LOG =
      LoggerFactory.getLogger(StandardBlockingSchedulerEngineTest.class);

  private StandardBlockingSchedulerEngine engine;

  private Logger mockLogger;
  private Step mockStep;
  private StandardBlockingSchedulerEngine.ConcurrencyValidationHooks validationHooks;

  @Before
  public void setupTest() throws Exception {
    this.engine = new StandardBlockingSchedulerEngine();

    this.mockLogger = Mockito.mock(Logger.class);
    this.mockStep = Mockito.mock(Step.class);

    this.validationHooks =
        Mockito.mock(StandardBlockingSchedulerEngine.ConcurrencyValidationHooks.class);

    this.engine.hooks = this.validationHooks;
  }

  @Test
  public void testGetSetMaximumProcessors() throws Exception {
    assertEquals(StandardBlockingSchedulerEngine.DEFAULT_MAXIMUM_PROCESSORS,
                 this.engine.getMaximumProcessors());

    this.engine.setMaximumProcessors(1313);
    assertEquals(1313, this.engine.getMaximumProcessors());
  }

  @Test
  public void testGetSetMaxThreadIdleTime() throws Exception {
    assertEquals(0, this.engine.getMaxThreadIdleTime());

    this.engine.setMaxThreadIdleTime(3131, TimeUnit.MILLISECONDS);
    assertEquals(3131, this.engine.getMaxThreadIdleTime());
  }

  @Test
  public void testGetSetLog() throws Exception {
    assertNotNull(this.engine.getLog());
    assertNotSame(this.mockLogger, this.engine.getLog());

    this.engine.setLog(this.mockLogger);
    assertSame(this.mockLogger, this.engine.getLog());
  }

  @Test
  public void testSimpleExecution() throws Exception {
    assertEquals(0, this.engine.getTotalStepsStarted());

    this.engine.start();
    assertEquals(0, this.engine.getTotalStepsStarted());
    assertEquals(0, this.engine.getNumStartedThread());

    this.syncExecSimpleStep();
    assertEquals(1, this.engine.getTotalStepsStarted());
    assertEquals(1, this.engine.getNumStartedThread());

    this.syncExecSimpleStep();
    assertEquals(2, this.engine.getTotalStepsStarted());
    assertEquals(1, this.engine.getNumStartedThread());
  }

  @Test
  public void testBacklog() throws Exception {
    this.engine.setMaximumProcessors(1);

    this.engine.start();

    CyclicBarrier startBarrier = new CyclicBarrier(2);
    CountDownLatch latch1 = this.startSimultaneousStep(startBarrier);
    CountDownLatch latch2 = this.startSleepStep(10);

    Mockito.verify(this.validationHooks, Mockito.timeout(100).times(1)).onTaskAddedToBacklog();

    startBarrier.await(1, TimeUnit.MILLISECONDS);

    assertTrue(latch1.await(1500, TimeUnit.MILLISECONDS));
    assertTrue(latch2.await(1500, TimeUnit.MILLISECONDS));

    Mockito.verify(this.validationHooks, Mockito.timeout(100)).onThreadNowIdle();

    assertEquals(1, this.engine.getNumStartedThread());
    assertEquals(2, this.engine.getTotalStepsStarted());
    assertEquals(1, this.engine.getNumIdleThread());
  }

  @Test
  public void testFailedStepExecution() throws Exception {
    RuntimeException rtExc = new RuntimeException("X-test-runtime-exception-X");

    Mockito.doThrow(rtExc).when(this.mockStep).execute();

    this.engine.setLog(this.mockLogger);
    this.engine.start();

    this.engine.submit(this.mockStep);

    Mockito.verify(this.mockLogger, Mockito.timeout(1000))
        .warn("failed step execution", rtExc);

    assertEquals(1, this.engine.getNumStartedThread());
    assertEquals(1, this.engine.getTotalStepsStarted());
  }

  @Test
  public void testThreadIdleExpiration() throws Exception {
    this.engine.setMaxThreadIdleTime(100, TimeUnit.MILLISECONDS);

    this.engine.start();

    this.syncExecSimpleStep();

    Mockito.verify(this.validationHooks, Mockito.timeout(100)).onThreadNowIdle();
    assertEquals(1, this.engine.getNumStartedThread());
    assertEquals(1, this.engine.getNumIdleThread());

    Mockito.verify(this.validationHooks, Mockito.timeout(100)).onIdleThreadRemoved();

    assertEquals(0, this.engine.getNumStartedThread());
    assertEquals(0, this.engine.getNumIdleThread());
  }

  @Test
  public void testNumIdleThread() throws Exception {
    this.engine.start();

    assertEquals(0, this.engine.getNumIdleThread());

    // These will run concurrently.
    CyclicBarrier startBarrier = new CyclicBarrier(3);
    CountDownLatch latch1 = this.startSimultaneousStep(startBarrier);
    CountDownLatch latch2 = this.startSimultaneousStep(startBarrier);
    CountDownLatch latch3 = this.startSimultaneousStep(startBarrier);

    assertTrue(latch1.await(100, TimeUnit.MILLISECONDS));
    assertTrue(latch2.await(100, TimeUnit.MILLISECONDS));
    assertTrue(latch3.await(100, TimeUnit.MILLISECONDS));

    // Wait for the threads to return to the idle state.
    Mockito.verify(this.validationHooks, Mockito.timeout(100).times(3)).onThreadNowIdle();

    //
    // REPEAT
    //
    startBarrier = new CyclicBarrier(3);
    latch1 = this.startSimultaneousStep(startBarrier);
    latch2 = this.startSimultaneousStep(startBarrier);
    latch3 = this.startSimultaneousStep(startBarrier);

    assertTrue(latch1.await(100, TimeUnit.MILLISECONDS));
    assertTrue(latch2.await(100, TimeUnit.MILLISECONDS));
    assertTrue(latch3.await(100, TimeUnit.MILLISECONDS));

    // Wait for the threads to return to the idle state.
    Mockito.verify(this.validationHooks, Mockito.timeout(100).times(6)).onThreadNowIdle();

    assertEquals(3, this.engine.getNumIdleThread());
  }

  @Test
  public void testSubmitWhenNotYetRunning() throws Exception {
    try {
      this.engine.submit(this.mockStep);
      fail("did not catch expected exception");
    } catch (Exception exc) {
      assertEquals("not yet started", exc.getMessage());
    }
  }

  @Test
  public void testStartBadState() throws Exception {
    this.engine.setMaximumProcessors(-1);
    try {
      this.engine.start();
      fail("did not catch expected exception");
    } catch (Exception exc) {
      assertEquals("scheduler engine needs a positive number of processors to start",
                   exc.getMessage());
    }

    this.engine.setMaximumProcessors(3);
    this.engine.start();

    try {
      this.engine.start();
      fail("did not catch expected exception");
    } catch (Exception exc) {
      assertEquals("already started", exc.getMessage());
    }
  }

  @Test
  public void testIdleThreadTimeoutInterrupted() throws Exception {
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        engine.initiateShutdown(true);
        return null;
      }
    }).when(this.validationHooks).onThreadNowIdle();

    this.engine.setLog(this.mockLogger);

    this.engine.start();

    // Execute one step and wait for the thread to reach the idle state
    this.syncExecSimpleStep();

    Mockito.verify(this.mockLogger, Mockito.timeout(100))
        .debug("idle thread interrupted waiting for step; processing as timeout");
  }

  @Test
  public void testSubmitAfterCreatingNewThread() throws Exception {
    // Initiate a shutdown when a new processor thread is created.
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        engine.initiateShutdown(false);
        return null;
      }
    }).when(this.validationHooks).onNewProcessorThread();

    this.engine.start();

    try {
      this.syncExecSimpleStep();
      fail("missing expected exception");
    } catch ( IllegalStateException isExc ) {
      assertEquals("scheduler has already stopped", isExc.getMessage());
    }
  }

  @Test
  public void testShutdownOnStartingStep() throws Exception {
    // Initiate a shutdown when a new processor thread is created.
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        engine.initiateShutdown(false);
        return null;
      }
    }).when(this.validationHooks).onThreadStartingNewStep();

    this.engine.start();

    CountDownLatch latch = this.startSleepStep(10);

    assertEquals(false, latch.await(10, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testSubmitAfterStopped() throws Exception {
    this.engine.start();
    this.engine.initiateShutdown(false);

    try {
      this.syncExecSimpleStep();
    } catch ( IllegalStateException isExc ) {
      assertEquals("scheduler has already stopped", isExc.getMessage());
    }
  }

  /**
   * Hit the default test hooks for code coverage purposes, and to ensure they don't break
   * anything.
   */
  @Test
  public void testDefaultHooks() throws Exception {
    this.engine = new StandardBlockingSchedulerEngine();

    this.engine.setMaximumProcessors(1);
    this.engine.setMaxThreadIdleTime(1, TimeUnit.MILLISECONDS);
    this.engine.start();

    this.startSleepStep(50);
    this.syncExecSimpleStep();

    Thread.sleep(50);
  }

  //////////////
  ////
  //// STEPS
  ////
  //////////////

  protected void syncExecSimpleStep() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        latch.countDown();
        return null;
      }
    }).when(this.mockStep).execute();

    //
    // Submit the step for execution.
    //
    this.engine.submit(this.mockStep);

    //
    // Give it up to 1 second to finish.
    //
    boolean successful = latch.await(1, TimeUnit.SECONDS);

    assertTrue(successful);
  }

  protected CountDownLatch startSleepStep(final int sleepTime) throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    Step anotherStep = Mockito.mock(Step.class);

    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Thread.sleep(sleepTime);
        latch.countDown();
        return null;
      }
    }).when(anotherStep).execute();

    //
    // Submit the step for execution.
    //
    this.engine.submit(anotherStep);

    return latch;
  }

  /**
   * Start a step that runs simultaneously with a number of other steps using the given count-down
   * latch to know when all are ready to start.
   *
   * @param startBarrier latch on which all of the steps running concurrently can synchronize.
   * @return Count Down Latch that can be used to confirm when this step has actually passed the
   * initial start latch.
   */
  protected CountDownLatch startSimultaneousStep(final CyclicBarrier startBarrier)
      throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);

    Step anotherStep = Mockito.mock(Step.class);

    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        startBarrier.await();

        latch.countDown();
        return null;
      }
    }).when(anotherStep).execute();

    //
    // Submit the step for execution.
    //
    this.engine.submit(anotherStep);

    return latch;
  }
}
