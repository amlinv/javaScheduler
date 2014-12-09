package com.amlinv.javasched;

import java.util.List;

/**
 * Created by art on 12/8/14.
 */
public interface Scheduler {
  void                    setEngine(SchedulerEngine newEngine);
  void                    startProcess(SchedulerProcess newProcess);
  List<SchedulerProcess> getProcessList();
}
