/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.core.function;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class Pipeline<T, R> {
    public enum Action {CONTINUE, STOP, SKIP}

    private final Context context = new Context();

    protected abstract Action process(T item, Context context);

    protected abstract R getResult(Context context);

    public final Action process(T item) {
        return process(item, context);
    }

    public final R getResult() {
        return getResult(context);
    }

    protected final Context getContext() {
        return context;
    }

    public final void reset() {
        context.clear();
    }

    public Pipeline<T, R> when(Predicate<T> condition) {
        return new Pipeline<>() {
            @Override
            protected Action process(T item, Context context) {
                return condition.test(item) ? Pipeline.this.process(item, context) : Action.CONTINUE;
            }

            @Override
            protected R getResult(Context context) {
                return Pipeline.this.getResult(context);
            }
        };
    }

    public <U, V> Pipeline<T, V> then(Pipeline<T, U> next, BiFunction<R, U, V> combiner) {
        return new Pipeline<>() {
            @Override
            protected Action process(T item, Context context) {
                Action action = Pipeline.this.process(item, context);
                return action == Action.CONTINUE ? next.process(item, context) : action;
            }

            @Override
            protected V getResult(Context context) {
                return combiner.apply(Pipeline.this.getResult(context), next.getResult(context));
            }
        };
    }

    public static <T> Pipeline<T, Void> forEach(Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "Consumer must not be null.");
        return new Pipeline<>() {
            @Override
            protected Action process(T item, Context context) {
                consumer.accept(item);
                return Action.CONTINUE;
            }

            @Override
            protected Void getResult(Context context) {
                return null;
            }
        };
    }

    public static class Context {
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        public <V> V get(String key) {
            return (V) data.get(key);
        }

        public <V> V get(String key, Class<V> type) {
            Object value = data.get(key);
            return type.isInstance(value) ? type.cast(value) : null;
        }

        public <V> V getOrDefault(String key, V defaultValue) {
            V value = get(key);
            return value != null ? value : defaultValue;
        }

        public <V> V getOrDefault(String key, V defaultValue, Class<V> type) {
            V value = get(key, type);
            return value != null ? value : defaultValue;
        }

        public void put(String key, Object value) {
            data.put(key, value);
        }

        public Set<String> keys() {
            return Collections.unmodifiableSet(data.keySet());
        }

        public boolean contains(String key) {
            return data.containsKey(key);
        }

        public void remove(String key) {
            data.remove(key);
        }

        public void clear() {
            data.clear();
        }
    }
}
