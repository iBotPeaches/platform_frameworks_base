/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.power.stats;

import static org.junit.Assert.assertNotNull;

import android.hardware.power.stats.Channel;
import android.hardware.power.stats.EnergyConsumer;
import android.hardware.power.stats.EnergyConsumerResult;
import android.hardware.power.stats.EnergyConsumerType;
import android.hardware.power.stats.EnergyMeasurement;
import android.hardware.power.stats.PowerEntity;
import android.hardware.power.stats.State;
import android.hardware.power.stats.StateResidency;
import android.hardware.power.stats.StateResidencyResult;
import android.power.PowerStatsInternal;
import android.util.SparseArray;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

class MockPowerStatsInternal extends PowerStatsInternal {
    private final SparseArray<PowerEntity> mPowerEntities = new SparseArray<>();
    private final SparseArray<StateResidencyResult> mStateResidencyResults = new SparseArray<>();
    private final SparseArray<EnergyConsumer> mEnergyConsumers = new SparseArray<>();
    private final SparseArray<EnergyConsumerResult> mEnergyConsumerResults = new SparseArray<>();
    private final int mTimeSinceBoot = 0;

    @Override
    public EnergyConsumer[] getEnergyConsumerInfo() {
        final int size = mEnergyConsumers.size();
        final EnergyConsumer[] consumers = new EnergyConsumer[size];
        for (int i = 0; i < size; i++) {
            consumers[i] = mEnergyConsumers.valueAt(i);
        }
        return consumers;
    }

    @Override
    public CompletableFuture<EnergyConsumerResult[]> getEnergyConsumedAsync(
            int[] energyConsumerIds) {
        final CompletableFuture<EnergyConsumerResult[]> future = new CompletableFuture();
        final EnergyConsumerResult[] results;
        final int length = energyConsumerIds.length;
        if (length == 0) {
            final int size = mEnergyConsumerResults.size();
            results = new EnergyConsumerResult[size];
            for (int i = 0; i < size; i++) {
                results[i] = mEnergyConsumerResults.valueAt(i);
            }
        } else {
            results = new EnergyConsumerResult[length];
            for (int i = 0; i < length; i++) {
                results[i] = mEnergyConsumerResults.get(energyConsumerIds[i]);
            }
        }
        future.complete(results);
        return future;
    }

    @Override
    public PowerEntity[] getPowerEntityInfo() {
        final int size = mPowerEntities.size();
        final PowerEntity[] entities = new PowerEntity[size];
        for (int i = 0; i < size; i++) {
            entities[i] = mPowerEntities.valueAt(i);
        }
        return entities;
    }

    @Override
    public CompletableFuture<StateResidencyResult[]> getStateResidencyAsync(
            int[] powerEntityIds) {
        final CompletableFuture<StateResidencyResult[]> future = new CompletableFuture<>();
        final StateResidencyResult[] results;
        final int length = powerEntityIds.length;
        if (length == 0) {
            final int size = mStateResidencyResults.size();
            results = new StateResidencyResult[size];
            for (int i = 0; i < size; i++) {
                results[i] = mStateResidencyResults.valueAt(i);
            }
        } else {
            results = new StateResidencyResult[length];
            for (int i = 0; i < length; i++) {
                results[i] = mStateResidencyResults.get(powerEntityIds[i]);
            }
        }
        future.complete(results);
        return future;
    }

    @Override
    public Channel[] getEnergyMeterInfo() {
        return new Channel[0];
    }

    @Override
    public CompletableFuture<EnergyMeasurement[]> readEnergyMeterAsync(
            int[] channelIds) {
        return new CompletableFuture<>();
    }

    public void reset() {
        mStateResidencyResults.clear();
        mEnergyConsumerResults.clear();
    }

    public void addPowerEntity(int id, String name) {
        PowerEntity powerEntity = new PowerEntity();
        powerEntity.id = id;
        powerEntity.name = name;
        powerEntity.states = new State[0];
        mPowerEntities.put(id, powerEntity);
    }

    public void addPowerEntityState(int powerEntityId, int stateId, String name) {
        State state = new State();
        state.id = stateId;
        state.name = name;

        PowerEntity powerEntity = mPowerEntities.get(powerEntityId);
        powerEntity.states = Arrays.copyOf(powerEntity.states, powerEntity.states.length + 1);
        powerEntity.states[powerEntity.states.length - 1] = state;
    }

    public void addStateResidencyResult(int entityId, int stateId, long totalTimeInStateMs,
            long totalStateEntryCount, long lastEntryTimestampMs) {
        StateResidencyResult result = mStateResidencyResults.get(entityId);
        if (result == null) {
            result = new StateResidencyResult();
            result.id = entityId;
            result.stateResidencyData = new StateResidency[0];
            mStateResidencyResults.put(entityId, result);
        }

        StateResidency residency = new StateResidency();
        residency.id = stateId;
        residency.totalTimeInStateMs = totalTimeInStateMs;
        residency.totalStateEntryCount = totalStateEntryCount;
        residency.lastEntryTimestampMs = lastEntryTimestampMs;

        result.stateResidencyData = Arrays.copyOf(result.stateResidencyData,
                result.stateResidencyData.length + 1);
        result.stateResidencyData[result.stateResidencyData.length - 1] = residency;
    }

    /**
     * Util method to add a new EnergyConsumer for testing
     *
     * @return the EnergyConsumer id of the new EnergyConsumer
     */
    public int addEnergyConsumer(@EnergyConsumerType byte type, int ordinal, String name) {
        final EnergyConsumer consumer = new EnergyConsumer();
        final int id = getNextAvailableId();
        consumer.id = id;
        consumer.type = type;
        consumer.ordinal = ordinal;
        consumer.name = name;
        mEnergyConsumers.put(id, consumer);

        final EnergyConsumerResult result = new EnergyConsumerResult();
        result.id = id;
        result.timestampMs = mTimeSinceBoot;
        result.energyUWs = 0;
        mEnergyConsumerResults.put(id, result);
        return id;
    }

    public void incrementEnergyConsumption(int id, long energyUWs) {
        EnergyConsumerResult result = mEnergyConsumerResults.get(id, null);
        assertNotNull(result);
        result.energyUWs += energyUWs;
    }

    private int getNextAvailableId() {
        final int size = mEnergyConsumers.size();
        // Just return the first index that does not match the key (aka the EnergyConsumer id)
        for (int i = size - 1; i >= 0; i--) {
            if (mEnergyConsumers.keyAt(i) == i) return i + 1;
        }
        // Otherwise return the lowest id
        return 0;
    }
}
