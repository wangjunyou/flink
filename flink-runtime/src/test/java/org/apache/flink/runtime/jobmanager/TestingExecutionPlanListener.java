/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.jobmanager;

import org.apache.flink.api.common.JobID;

import java.util.ArrayList;
import java.util.List;

/** {@link ExecutionPlanStore.ExecutionPlanListener} implementation for testing purposes. */
public class TestingExecutionPlanListener implements ExecutionPlanStore.ExecutionPlanListener {

    private final List<JobID> addedExecutionPlans = new ArrayList<>();

    private final List<JobID> removedExecutionPlans = new ArrayList<>();

    @Override
    public void onAddedExecutionPlan(JobID jobId) {
        addedExecutionPlans.add(jobId);
    }

    @Override
    public void onRemovedExecutionPlan(JobID jobId) {
        removedExecutionPlans.add(jobId);
    }

    public List<JobID> getAddedExecutionPlans() {
        return addedExecutionPlans;
    }

    public List<JobID> getRemovedExecutionPlans() {
        return removedExecutionPlans;
    }
}
