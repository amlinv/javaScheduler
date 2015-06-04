package com.amlinv.javasched;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

/**
 * Created by art on 12/7/14.
 */
public class SchedulerTest {
  private Scheduler scheduler;

  private SchedulerProcess mockProcess;

  @Before
  public void setupTest () {
    this.scheduler = Mockito.mock(Scheduler.class);

    this.mockProcess = Mockito.mock(SchedulerProcess.class);
  }

  @Test
  public void testInterface () {
    this.scheduler.startProcess(this.mockProcess);
    List<SchedulerProcess> processList = this.scheduler.getProcessList();
    this.scheduler.start();
  }
}
