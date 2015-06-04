package com.amlinv.javasched;

/**
 * Created by art on 12/8/14.
 */
public interface SchedulerEngine {
  void start();
  void submit(Step step);
}
