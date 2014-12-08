package com.amlinv.javasched;

import org.junit.Before;
import org.junit.Test;

/**
 * Created by art on 12/7/14.
 */
public class TestSchedulerProcess {
  private SchedulerProcess  schedulerProcess;

  private Step              mockStep;

  @Before
  public void setupTest () {
    this.schedulerProcess = Mockito.mock(SchedulerProcess.class);

    this.mockStep = Mockito.mock(Step.class);
  }

  @Test
  public void testInterface () {
    this.schedulerProcess.addStep(this.mockStep);
  }
}
