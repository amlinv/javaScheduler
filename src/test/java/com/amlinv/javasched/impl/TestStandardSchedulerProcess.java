package com.amlinv.javasched.impl;

import com.amlinv.javasched.Step;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class TestStandardSchedulerProcess {
  private StandardSchedulerProcess  standardSchedulerProcess;

  private Step                      mockStep;

  @Before
  public void setupTest () {
    this.standardSchedulerProcess = new StandardSchedulerProcess();

    this.mockStep = Mockito.mock(Step.class);
  }

  @Test
  public void testAddStep () {
    this.standardSchedulerProcess.addStep(this.mockStep);
  }
}