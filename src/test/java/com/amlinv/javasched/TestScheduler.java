package com.amlinv.javasched;

import org.junit.Before;
import org.junit.Test;

/**
 * Created by art on 12/7/14.
 */
public class TestScheduler {
  private Scheduler scheduler;

  @Before
  public void setupTest () {
    this.scheduler = Mockito.mock(Scheduler.class);
  }

  @Test
  public void testInterface () {
    this.scheduler.setEngine(new StandardSchedulerEngine());
    this.scheduler.startProcess(new SchedulerProcess());
    List<SchedulerProcess> processList = this.scheduler.getProcessList();
  }
}
