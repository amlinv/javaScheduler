package com.amlinv.javasched.impl;

import com.amlinv.javasched.SchedulerProcess;
import com.amlinv.javasched.Step;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by art on 12/8/14.
 */
public class StandardSchedulerProcess implements SchedulerProcess {
  private Deque<Step> steps = new LinkedList<Step>();

  @Override
  public void addStep (Step newStep) {
    this.steps.add(newStep);
  }
}
