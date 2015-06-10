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

package com.amlinv.javasched;

import java.util.concurrent.TimeUnit;

/**
 * A process submitted to a scheduler for execution.  Each time the scheduler is ready to queue the
 * process, the getNextStep() method is called.  If the result is a non-blocking step, it is queued
 * for execution on one of the non-blocking step threads.  However, if the result is a blocking
 * step, it is queue for execution on one of the blocking step threads.
 *
 * It is critical that implementations obey the definitions of blocking and non-blocking here,
 * otherwise the efficiency of the scheduler will be thwarted, and the result will likely be worse
 * than without using the scheduler.  Operations that may block for very short periods, specifically
 * those with very fast critical sections, should be treated as non-blocking steps due to the
 * overhead involved in scheduling, but anything longer must be treated as blocking.  Performance
 * measurements and tuning are appropriate here.
 *
 * Schedulers may or may not implement checks and abort non-blocking steps that actually do block.
 *
 * Created by art on 12/8/14.
 */
public interface SchedulerProcess {

  Step getNextStep();
}
