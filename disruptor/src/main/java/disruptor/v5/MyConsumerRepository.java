/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package disruptor.v5;

import disruptor.v5.api.EventProcessorV5;

import java.util.ArrayList;

/**
 *
 */
public class MyConsumerRepository<T> {

    private final ArrayList<ConsumerInfo> consumerInfos = new ArrayList<>();

    public ArrayList<ConsumerInfo> getConsumerInfos() {
        return consumerInfos;
    }

    public void add(final EventProcessorV5 processor) {
        final EventProcessorInfo<T> consumerInfo = new EventProcessorInfo<>(processor);
        consumerInfos.add(consumerInfo);
    }


    public void add(final WorkerPoolV5<T> workerPool) {
        final WorkerPoolInfo<T> workerPoolInfo = new WorkerPoolInfo<>(workerPool);
        consumerInfos.add(workerPoolInfo);
    }
}
