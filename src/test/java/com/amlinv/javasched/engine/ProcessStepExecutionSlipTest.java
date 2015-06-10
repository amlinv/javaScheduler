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

package com.amlinv.javasched.engine;

import com.amlinv.javasched.Step;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class ProcessStepExecutionSlipTest {

  private Step mockStep;
  private ProcessStepExecutionListener mockListener;

  @Before
  public void setupTest() throws Exception {
    this.mockStep = Mockito.mock(Step.class);
    this.mockListener = Mockito.mock(ProcessStepExecutionListener.class);
  }

  @Test
  public void testExecute() throws Exception {
    ProcessStepExecutionSlip slip = new ProcessStepExecutionSlip(this.mockStep, this.mockListener);

    slip.execute();

    InOrder inOrder = Mockito.inOrder(this.mockStep, this.mockListener);
    inOrder.verify(this.mockListener).onStepStarted();
    inOrder.verify(this.mockStep).execute();
    inOrder.verify(this.mockListener).onStepStopped();
    inOrder.verify(this.mockListener, Mockito.times(0)).onStepException(
        Mockito.any(Throwable.class));
  }

  @Test
  public void testExceptionOnExecute() throws Exception {
    RuntimeException rtExc = new RuntimeException("X-runtime-exception-X");
    Mockito.doThrow(rtExc).when(this.mockStep).execute();

    ProcessStepExecutionSlip slip = new ProcessStepExecutionSlip(this.mockStep, this.mockListener);

    try {
      slip.execute();
      fail("missing expected exception");
    } catch ( RuntimeException caughtRtExc ) {
      assertSame(rtExc, caughtRtExc);
    }

    InOrder inOrder = Mockito.inOrder(this.mockStep, this.mockListener);
    inOrder.verify(this.mockListener).onStepStarted();
    inOrder.verify(this.mockStep).execute();
    inOrder.verify(this.mockListener, Mockito.times(0)).onStepStopped();
    inOrder.verify(this.mockListener).onStepException(rtExc);
  }

  @Test
  public void testIsBlocking() throws Exception {
    Mockito.when(this.mockStep.isBlocking()).thenReturn(true);

    ProcessStepExecutionSlip slip = new ProcessStepExecutionSlip(this.mockStep, this.mockListener);

    assertTrue(slip.isBlocking());
  }

  @Test
  public void testIsNonBlocking() throws Exception {
    Mockito.when(this.mockStep.isBlocking()).thenReturn(false);

    ProcessStepExecutionSlip slip = new ProcessStepExecutionSlip(this.mockStep, this.mockListener);

    assertFalse(slip.isBlocking());
  }
}