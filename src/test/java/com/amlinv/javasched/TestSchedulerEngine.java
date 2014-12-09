package com.amlinv.javasched;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Created by art on 12/7/14.
 */
public class TestSchedulerEngine {
  private SchedulerEngine schedulerEngine;

  @Before
  public void setupTest () {
    this.schedulerEngine = Mockito.mock(SchedulerEngine.class);
  }

  @Test
  public void testInterface () {
    this.schedulerEngine.getProcessorCount();
    this.schedulerEngine.setProcessorCount(13);
  }
}
