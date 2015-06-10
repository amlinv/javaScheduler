/*
 *   Copyright 2015 AML Innovation & Consulting LLC
 *
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.amlinv.javasched.process;

import com.amlinv.javasched.Step;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RateLimitedSingleRepeatingStepProcessTest {
  private RateLimitedSingleRepeatingStepProcess process;

  private Step mockStep;

  @Before
  public void setupTest() throws Exception {
    this.mockStep = Mockito.mock(Step.class);

    this.process = new RateLimitedSingleRepeatingStepProcess(this.mockStep, false, 100, 0);
  }

  @Test
  public void testAlternateConstructor() {
    this.process = new RateLimitedSingleRepeatingStepProcess(this.mockStep, 100, 0);
    assertTrue(this.process.isRepeatOnlyWhenSignalled());
  }

  @Test
  public void testGetNextStep() throws Exception {
    Step result = this.process.getNextStep();
    assertSame(this.mockStep, result);

    result = this.process.getNextStep();
    assertNotSame(this.mockStep, result); // false negative warning: this can fail due to timing
    assertTrue(result instanceof RateLimitedSingleRepeatingStepProcess.WaitRatePeriodCompleteStep);
  }

  @Test
  public void testWaitStep() throws Exception {
    long startMark = System.nanoTime() / 1000000L;

    // Setup the process to wait immediately.
    this.process = new RateLimitedSingleRepeatingStepProcess(this.mockStep, false, 100, 100);

    Step result = this.process.getNextStep();

    assertTrue(result instanceof RateLimitedSingleRepeatingStepProcess.WaitRatePeriodCompleteStep);
    assertTrue(result.isBlocking());

    result.execute();

    long endMark = System.nanoTime() / 1000000L;
    assertTrue((endMark - startMark) >= 100);
  }

  @Test
  public void testWaitStepInterrupted() throws Exception {
    // Setup the process to wait immediately.
    this.process = new RateLimitedSingleRepeatingStepProcess(this.mockStep, false, 100, 100);

    final Step waitStep = this.process.getNextStep();
    final CountDownLatch latch = new CountDownLatch(1);
    final Exception[] exceptionSite = new Exception[1];

    Thread execThread = new Thread() {
      @Override
      public void run() {
        try {
          waitStep.execute();
        } catch (Exception exc) {
          exceptionSite[0] = exc;
        }
        latch.countDown();
      }
    };
    execThread.start();

    execThread.interrupt();

    assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
    assertTrue(exceptionSite[0] instanceof RuntimeException);
    assertTrue(exceptionSite[0].getCause() instanceof InterruptedException);

  }
}
