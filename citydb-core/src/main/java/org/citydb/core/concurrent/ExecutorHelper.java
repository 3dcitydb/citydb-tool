/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.core.concurrent;

import java.util.concurrent.*;

public class ExecutorHelper {

    public static ExecutorService newFixedAndBlockingThreadPool(int nThreads, int capacity, ThreadFactory factory) {
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(capacity) {
            @Override
            public boolean offer(Runnable o) {
                try {
                    put(o);
                    return true;
                } catch (InterruptedException e) {
                    return false;
                }
            }
        };

        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, queue, factory);
    }

    public static ExecutorService newFixedAndBlockingThreadPool(int nThreads, int capacity) {
        return newFixedAndBlockingThreadPool(nThreads, capacity, Executors.defaultThreadFactory());
    }

    public static ExecutorService newFixedAndBlockingThreadPool(int nThreads) {
        return newFixedAndBlockingThreadPool(nThreads, nThreads * 2);
    }
}
