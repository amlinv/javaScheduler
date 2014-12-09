package com.amlinv.javasched;

import com.amlinv.javasched.impl.StandardSchedulerEngine;
import com.amlinv.javasched.impl.StandardSchedulerProcess;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

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
    this.scheduler.startProcess(new StandardSchedulerProcess());
    List<SchedulerProcess> processList = this.scheduler.getProcessList();
  }
}
