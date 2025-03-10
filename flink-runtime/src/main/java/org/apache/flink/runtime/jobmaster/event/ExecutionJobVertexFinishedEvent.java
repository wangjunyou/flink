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

package org.apache.flink.runtime.jobmaster.event;

import org.apache.flink.runtime.executiongraph.ExecutionJobVertex;
import org.apache.flink.runtime.jobgraph.IntermediateDataSetID;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.scheduler.adaptivebatch.BlockingResultInfo;

import java.util.Map;

import static org.apache.flink.util.Preconditions.checkNotNull;

/** This class is used to record the completion info of {@link ExecutionJobVertex}. */
public class ExecutionJobVertexFinishedEvent implements JobEvent {

    private final JobVertexID vertexId;

    private final Map<IntermediateDataSetID, BlockingResultInfo> resultInfo;

    public ExecutionJobVertexFinishedEvent(
            JobVertexID vertexId, Map<IntermediateDataSetID, BlockingResultInfo> resultInfo) {
        this.vertexId = checkNotNull(vertexId);
        this.resultInfo = checkNotNull(resultInfo);
    }

    public JobVertexID getVertexId() {
        return vertexId;
    }

    public Map<IntermediateDataSetID, BlockingResultInfo> getResultInfo() {
        return resultInfo;
    }

    @Override
    public String toString() {
        return "ExecutionJobVertexFinishedEvent{"
                + "vertexId="
                + vertexId
                + ", resultInfos="
                + resultInfo
                + '}';
    }
}
