package com.amlinv.javasched.impl;

import com.amlinv.javasched.BlockingSchedulerEngine;
import com.amlinv.javasched.NonBlockingSchedulerEngine;
import com.amlinv.javasched.SchedulerProcess;
import com.amlinv.javasched.Step;
import com.amlinv.javasched.engine.ProcessStepExecutionListener;
import com.amlinv.javasched.engine.ProcessStepExecutionSlip;
import com.amlinv.javasched.engine.ProcessStepExecutionSlipFactory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class RoundRobinSchedulerTest {

  private static final Logger LOG = LoggerFactory.getLogger(RoundRobinSchedulerTest.class);

  private RoundRobinScheduler roundRobinScheduler;

  private NonBlockingSchedulerEngine mockNonBlockingEngine;
  private BlockingSchedulerEngine mockBlockingEngine;
  private SchedulerProcess mockProcess;
  private Step mockStep;

  private ProcessStepExecutionSlipFactory mockExecutionSlipFactory;

  @Before
  public void setupTest() {
    this.roundRobinScheduler = new RoundRobinScheduler();

    this.mockNonBlockingEngine = Mockito.mock(NonBlockingSchedulerEngine.class);
    this.mockBlockingEngine = Mockito.mock(BlockingSchedulerEngine.class);
    this.mockProcess = Mockito.mock(SchedulerProcess.class);
    this.mockStep = Mockito.mock(Step.class);

    this.mockExecutionSlipFactory = Mockito.mock(ProcessStepExecutionSlipFactory.class);
  }

  @Test
  public void testGetSetNonBlockingEngine() {
    assertNotNull(this.roundRobinScheduler.getNonBlockingEngine());
    assertNotSame(this.mockNonBlockingEngine, this.roundRobinScheduler.getNonBlockingEngine());

    this.roundRobinScheduler.setNonBlockingEngine(this.mockNonBlockingEngine);
    assertSame(this.mockNonBlockingEngine, this.roundRobinScheduler.getNonBlockingEngine());
  }

  @Test
  public void testGetSetBlockingEngine() {
    assertNotNull(this.roundRobinScheduler.getBlockingEngine());
    assertNotSame(this.mockBlockingEngine, this.roundRobinScheduler.getBlockingEngine());

    this.roundRobinScheduler.setBlockingEngine(this.mockBlockingEngine);
    assertSame(this.mockBlockingEngine, this.roundRobinScheduler.getBlockingEngine());
  }

  @Test
  public void testGetSetProcessStepExecutionSlipFactory() {
    assertNotNull(this.roundRobinScheduler.getStepExecutionSlipFactory());

    this.roundRobinScheduler.setStepExecutionSlipFactory(this.mockExecutionSlipFactory);
    assertSame(this.mockExecutionSlipFactory, this.roundRobinScheduler.getStepExecutionSlipFactory());
  }

  @Test
  public void testStart() {
    this.setupStandardScheduler();

    this.roundRobinScheduler.start();

    Mockito.verify(this.mockBlockingEngine).start();
    Mockito.verify(this.mockNonBlockingEngine).start();

    assertEquals(0, this.roundRobinScheduler.getProcessList().size());
  }

  @Test
  public void testRunEmptyProcess() {
    this.setupStandardScheduler();

    Mockito.when(this.mockProcess.getNextStep()).thenReturn(null);

    this.roundRobinScheduler.start();
    this.roundRobinScheduler.startProcess(this.mockProcess);

    assertEquals(0, this.roundRobinScheduler.getProcessList().size());
  }

  @Test
  public void testRun1StepProcess() {
    this.setupStandardScheduler();

    ProcessStepExecutionSlip mockExecutionSlip = Mockito.mock(ProcessStepExecutionSlip.class);

    Mockito.when(this.mockStep.isBlocking()).thenReturn(true);
    Mockito.when(this.mockProcess.getNextStep())
        .thenReturn(this.mockStep)
        .thenReturn(null);

    // Capture the listener for further validation.
    ArgumentCaptor<ProcessStepExecutionListener> executionListenerCaptor =
        ArgumentCaptor.forClass(ProcessStepExecutionListener.class);

    Mockito.when(this.mockExecutionSlipFactory
                     .createProcessStepExecutionSlip(Mockito.same(this.mockStep),
                                                     executionListenerCaptor.capture()))
        .thenReturn(mockExecutionSlip);

    this.roundRobinScheduler.start();
    this.roundRobinScheduler.startProcess(this.mockProcess);

    Mockito.verify(this.mockBlockingEngine).submit(mockExecutionSlip);
    assertEquals(1, this.roundRobinScheduler.getProcessList().size());
    assertTrue(this.roundRobinScheduler.getProcessList().contains(this.mockProcess));

    executionListenerCaptor.getValue().onStepStopped();

    assertEquals(0, this.roundRobinScheduler.getProcessList().size());
  }

  @Test
  public void testRunProcessWithExceptionOnStep() {
    this.setupStandardScheduler();

    ProcessStepExecutionSlip mockExecutionSlip = Mockito.mock(ProcessStepExecutionSlip.class);

    Mockito.when(this.mockStep.isBlocking()).thenReturn(true);
    Mockito.when(this.mockProcess.getNextStep())
        .thenReturn(this.mockStep)
        .thenReturn(null);

    // Capture the listener for further validation.
    ArgumentCaptor<ProcessStepExecutionListener> executionListenerCaptor =
        ArgumentCaptor.forClass(ProcessStepExecutionListener.class);

    Mockito.when(this.mockExecutionSlipFactory
                     .createProcessStepExecutionSlip(Mockito.same(this.mockStep),
                                                     executionListenerCaptor.capture()))
        .thenReturn(mockExecutionSlip);

    this.roundRobinScheduler.start();
    this.roundRobinScheduler.startProcess(this.mockProcess);

    Mockito.verify(this.mockBlockingEngine).submit(mockExecutionSlip);
    assertEquals(1, this.roundRobinScheduler.getProcessList().size());
    assertTrue(this.roundRobinScheduler.getProcessList().contains(this.mockProcess));

    RuntimeException rtExc = new RuntimeException("X-test-runtime-exception-X");
    executionListenerCaptor.getValue().onStepException(rtExc);

    assertEquals(0, this.roundRobinScheduler.getProcessList().size());
  }

  @Test
  public void testStartWhenAlreadyStarted() {
    this.roundRobinScheduler.start();

    try {
      this.roundRobinScheduler.start();
      fail("missing expected exception");
    } catch ( IllegalStateException isExc ) {
      assertEquals("already started", isExc.getMessage());
    }
  }

  @Test
  public void testStartProcessWhenSchedulerNotStarted() {
    try {
      this.roundRobinScheduler.startProcess(this.mockProcess);
      fail("missing expected exception");
    } catch ( IllegalStateException isExc ) {
      assertEquals("not yet started", isExc.getMessage());
    }
  }

  @Test
  public void testStartProcessWhenAlreadyStarted() {
    Mockito.when(this.mockProcess.getNextStep()).thenReturn(this.mockStep);

    this.roundRobinScheduler.start();
    this.roundRobinScheduler.startProcess(this.mockProcess);

    try {
      this.roundRobinScheduler.startProcess(this.mockProcess);
      fail("missing expected exception");
    } catch ( IllegalStateException isExc ) {
      assertEquals("process is already active", isExc.getMessage());
    }
  }

  protected void setupStandardScheduler() {
    this.roundRobinScheduler.setBlockingEngine(this.mockBlockingEngine);
    this.roundRobinScheduler.setNonBlockingEngine(this.mockNonBlockingEngine);

    this.roundRobinScheduler.setStepExecutionSlipFactory(this.mockExecutionSlipFactory);
  }
}
