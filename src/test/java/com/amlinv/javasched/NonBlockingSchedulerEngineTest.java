package com.amlinv.javasched;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Created by art on 12/7/14.
 */
public class NonBlockingSchedulerEngineTest {
  private NonBlockingSchedulerEngine schedulerEngine;

  private Step mockStep;

  @Before
  public void setupTest () {
    this.schedulerEngine = Mockito.mock(NonBlockingSchedulerEngine.class);

    this.mockStep = Mockito.mock(Step.class);
  }

  @Test
  public void testInterface () {
    this.schedulerEngine.submit(this.mockStep);
  }
}
