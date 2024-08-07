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

package org.apache.flink.runtime.jobmaster.slotpool;

import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.clusterframework.types.SlotProfileTestingUtils;
import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutor;
import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutorServiceAdapter;
import org.apache.flink.runtime.jobmaster.SlotRequestId;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import javax.annotation.Nonnull;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

/**
 * {@code PhysicalSlotProviderExtension} is used for testing different {@link SlotSelectionStrategy}
 * implementations on {@link PhysicalSlotProviderImpl}.
 */
public class PhysicalSlotProviderExtension implements BeforeEachCallback, AfterEachCallback {

    private ScheduledExecutorService singleThreadScheduledExecutorService;

    private ComponentMainThreadExecutor mainThreadExecutor;

    private final SlotSelectionStrategy slotSelectionStrategy;

    private DeclarativeSlotPoolBridge slotPool;

    private PhysicalSlotProvider physicalSlotProvider;

    public PhysicalSlotProviderExtension(@Nonnull SlotSelectionStrategy slotSelectionStrategy) {
        this.slotSelectionStrategy = slotSelectionStrategy;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        this.singleThreadScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.mainThreadExecutor =
                ComponentMainThreadExecutorServiceAdapter.forSingleThreadExecutor(
                        singleThreadScheduledExecutorService);
        slotPool =
                new DeclarativeSlotPoolBridgeBuilder()
                        .setMainThreadExecutor(mainThreadExecutor)
                        .buildAndStart();
        physicalSlotProvider = new PhysicalSlotProviderImpl(slotSelectionStrategy, slotPool);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        CompletableFuture.runAsync(() -> slotPool.close(), mainThreadExecutor).join();
        singleThreadScheduledExecutorService.shutdown();
    }

    public CompletableFuture<PhysicalSlotRequest.Result> allocateSlot(PhysicalSlotRequest request) {
        return CompletableFuture.supplyAsync(
                        () ->
                                physicalSlotProvider
                                        .allocatePhysicalSlots(Collections.singletonList(request))
                                        .get(request.getSlotRequestId()),
                        mainThreadExecutor)
                .thenCompose(Function.identity());
    }

    public void registerSlotOffersFromNewTaskExecutor(ResourceProfile... resourceProfiles) {
        CompletableFuture.runAsync(
                        () -> {
                            slotPool.increaseResourceRequirementsBy(
                                    SlotPoolUtils.calculateResourceCounter(resourceProfiles));
                        },
                        mainThreadExecutor)
                .join();
        SlotPoolUtils.offerSlots(slotPool, mainThreadExecutor, Arrays.asList(resourceProfiles));
    }

    public PhysicalSlotRequest createSimpleRequest() {
        return new PhysicalSlotRequest(
                new SlotRequestId(),
                SlotProfileTestingUtils.noLocality(ResourceProfile.UNKNOWN),
                false);
    }

    public ComponentMainThreadExecutor getMainThreadExecutor() {
        return mainThreadExecutor;
    }
}
