package com.amlinv.javasched;

import com.amlinv.javasched.impl.StandardSchedulerEngine;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by art on 12/7/14.
 */
public class TestStandardScheduleEngine {
  private StandardSchedulerEngine standardSchedulerEngine;

  @Before
  public void setupTest () {
    this.standardSchedulerEngine = new StandardSchedulerEngine();
  }

  @Test
  public void testGetSetProcessorCount () {
    assertEquals(1, this.standardSchedulerEngine.getProcessorCount());

    this.standardSchedulerEngine.setProcessorCount(3);
    assertEquals(3, this.standardSchedulerEngine.getProcessorCount());
  }
}
