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

/**
 * A step wrapped for the scheduler so that it can detect when step execution has completed in
 * order to iterate the original process onto its next step.
 *
 * Created by art on 6/3/15.
 */
public class ProcessStepExecutionSlip implements Step {
  private final Step sourceStep;
  private final ProcessStepExecutionListener listener;

  public ProcessStepExecutionSlip(Step initSourceStep, ProcessStepExecutionListener initListener) {
    this.sourceStep = initSourceStep;
    this.listener = initListener;
  }

  @Override
  public void execute() {
    this.listener.onStepStarted();
    try {
      this.sourceStep.execute();
      this.listener.onStepStopped();
    } catch ( RuntimeException rtExc ) {
      this.listener.onStepException(rtExc);
      throw rtExc;
    }
  }

  @Override
  public boolean isBlocking() {
    return this.sourceStep.isBlocking();
  }
}
