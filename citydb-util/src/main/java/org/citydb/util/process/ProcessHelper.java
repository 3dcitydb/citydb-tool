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

package org.citydb.util.process;

import org.citydb.core.CoreConstants;
import org.citydb.core.function.CheckedConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ProcessHelper {
    private static final int GRACEFUL_KILL_TIMEOUT = 2;
    private static final int SERVICE_SHUTDOWN_TIMEOUT = 2;
    private final Logger logger = LoggerFactory.getLogger(ProcessHelper.class);
    private final ProcessBuilder builder;
    private final Map<String, String> envVariables = new HashMap<>();

    private int processTimeout = 60;
    private int inputTimeout = 60;
    private int streamReadTimeout = 0;
    private Charset inputCharset = StandardCharsets.UTF_8;
    private Charset outputCharset = StandardCharsets.UTF_8;
    private Path workingDir = CoreConstants.WORKING_DIR;

    private ProcessHelper(ProcessBuilder builder) {
        this.builder = Objects.requireNonNull(builder, "The process builder must not be null.");
    }

    public static ProcessHelper of(ProcessBuilder builder) {
        return new ProcessHelper(builder);
    }

    public static ProcessHelper of(List<String> command) {
        Objects.requireNonNull(command, "The process command must not be null.");
        return new ProcessHelper(new ProcessBuilder(command));
    }

    public static ProcessHelper of(String... command) {
        Objects.requireNonNull(command, "The process command must not be null.");
        return new ProcessHelper(new ProcessBuilder(command));
    }

    public Map<String, String> getEnvVariables() {
        return envVariables;
    }

    public ProcessHelper setEnvVariable(String key, String value) {
        envVariables.put(key, value);
        return this;
    }

    public ProcessHelper setEnvVariables(Map<String, String> envVariables) {
        if (envVariables != null) {
            this.envVariables.putAll(envVariables);
        }

        return this;
    }

    public int getProcessTimeout() {
        return processTimeout;
    }

    public ProcessHelper setProcessTimeout(int processTimeout) {
        this.processTimeout = Math.max(processTimeout, 0);
        return this;
    }

    public int getInputTimeout() {
        return inputTimeout;
    }

    public ProcessHelper setInputTimeout(int inputTimeout) {
        this.inputTimeout = Math.max(inputTimeout, 0);
        return this;
    }

    public int getStreamReadTimeout() {
        return streamReadTimeout;
    }

    public ProcessHelper setStreamReadTimeout(int streamReadTimeout) {
        this.streamReadTimeout = Math.max(streamReadTimeout, 0);
        return this;
    }

    public Charset getInputCharset() {
        return inputCharset;
    }

    public ProcessHelper setInputCharset(Charset inputCharset) {
        this.inputCharset = inputCharset != null ? inputCharset : StandardCharsets.UTF_8;
        return this;
    }

    public Charset getOutputCharset() {
        return outputCharset;
    }

    public ProcessHelper setOutputCharset(Charset outputCharset) {
        this.outputCharset = outputCharset != null ? outputCharset : StandardCharsets.UTF_8;
        return this;
    }

    public Path getWorkingDir() {
        return workingDir;
    }

    public ProcessHelper setWorkingDir(Path workingDir) {
        this.workingDir = workingDir != null ? workingDir : CoreConstants.WORKING_DIR;
        return this;
    }

    public int run(Consumer<String> stdoutConsumer, Consumer<String> stderrConsumer) throws ProcessException {
        return run(stdoutConsumer, stderrConsumer, (CheckedConsumer<OutputStream, IOException>) null);
    }

    public int run(Consumer<String> stdoutConsumer, Consumer<String> stderrConsumer, String input) throws ProcessException {
        return run(stdoutConsumer, stderrConsumer, outputStream -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, inputCharset))) {
                writer.write(input);
                writer.flush();
            }
        });
    }

    public int run(Consumer<String> stdoutConsumer, Consumer<String> stderrConsumer, CheckedConsumer<OutputStream, IOException> stdinConsumer) throws ProcessException {
        Objects.requireNonNull(stdoutConsumer, "The stdout consumer must not be null.");
        Objects.requireNonNull(stderrConsumer, "The stderr consumer must not be null.");

        Process process;
        try {
            builder.environment().putAll(envVariables);
            builder.directory(workingDir.toFile());
            process = builder.start();
        } catch (Exception e) {
            throw new ProcessException("Failed to start process.", e);
        }

        ExecutorService service = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });

        try {
            Future<?> inTask = service.submit(() -> {
                try (OutputStream in = process.getOutputStream()) {
                    if (stdinConsumer != null) {
                        stdinConsumer.accept(in);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("I/O error while writing to process input.", e);
                }
            });

            Future<?> outTask = service.submit(() -> consumeOutput(process.getInputStream(), stdoutConsumer));
            Future<?> errTask = service.submit(() -> consumeOutput(process.getErrorStream(), stderrConsumer));

            try {
                if (inputTimeout > 0) {
                    inTask.get(inputTimeout, TimeUnit.SECONDS);
                } else {
                    inTask.get();
                }
            } catch (TimeoutException e) {
                inTask.cancel(true);
                throw new ProcessException("Failed to write to process input within " + inputTimeout + " seconds.", e);
            } catch (ExecutionException e) {
                throw new ProcessException("Failed to write to process input.", e.getCause());
            }

            ProcessHandle processHandle = process.toHandle();
            waitForProcess(processHandle);

            waitForStream(outTask);
            waitForStream(errTask);

            if (!inTask.isDone()) {
                inTask.cancel(true);
            }

            return process.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessException("Process helper was interrupted while running process.", e);
        } catch (Exception e) {
            throw new ProcessException("Failed to run process.", e);
        } finally {
            shutdown(service);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private void consumeOutput(InputStream stream, Consumer<String> consumer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, outputCharset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                consumer.accept(line);
            }
        } catch (IOException e) {
            logger.debug("Failed to read output from process.", e);
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void waitForProcess(ProcessHandle processHandle) throws ProcessException {
        CompletableFuture<ProcessHandle> onExit = processHandle.onExit();

        if (processTimeout > 0) {
            ProcessHandle exitHandle = onExit.completeOnTimeout(null, processTimeout, TimeUnit.SECONDS).join();
            if (exitHandle == null) {
                logger.debug("Process did not exit within {} seconds. Killing process.", processTimeout);
                processHandle.destroy();
                exitHandle = onExit.completeOnTimeout(null, GRACEFUL_KILL_TIMEOUT, TimeUnit.SECONDS).join();
                if (exitHandle == null) {
                    processHandle.destroyForcibly();
                    exitHandle = onExit.completeOnTimeout(null, GRACEFUL_KILL_TIMEOUT, TimeUnit.SECONDS).join();
                    if (exitHandle == null) {
                        throw new ProcessException("Process did not terminate after forced kill within timeout.");
                    }
                }
            }
        } else {
            onExit.join();
        }
    }

    private void waitForStream(Future<?> streamTask) throws ProcessException, InterruptedException {
        try {
            if (streamReadTimeout > 0) {
                streamTask.get(streamReadTimeout, TimeUnit.SECONDS);
            } else {
                streamTask.get();
            }
        } catch (TimeoutException e) {
            streamTask.cancel(true);
            throw new ProcessException("Failed to read process output within " + streamReadTimeout + " seconds.", e);
        } catch (ExecutionException e) {
            throw new ProcessException("Failed to read process output.", e.getCause());
        }
    }

    private void shutdown(ExecutorService service) {
        service.shutdown();
        try {
            if (!service.awaitTermination(SERVICE_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException e) {
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static class Redirect {
        public static final Consumer<String> STDOUT = System.out::println;
        public static final Consumer<String> STDERR = System.err::println;
        public static final Consumer<String> DISCARD = s -> {
        };

        public static Consumer<String> filter(Predicate<String> predicate, Consumer<String> delegate) {
            Objects.requireNonNull(predicate, "The predicate must not be null.");
            Objects.requireNonNull(delegate, "The delegate must not be null.");
            return line -> {
                if (predicate.test(line)) {
                    delegate.accept(line);
                }
            };
        }

        public static Consumer<String> map(Function<String, String> mapper, Consumer<String> delegate) {
            Objects.requireNonNull(mapper, "The mapper must not be null.");
            Objects.requireNonNull(delegate, "The delegate must not be null.");
            return line -> delegate.accept(mapper.apply(line));
        }

        @SafeVarargs
        public static Consumer<String> tee(Consumer<String>... consumers) {
            Objects.requireNonNull(consumers, "The consumers must not be null.");
            return line -> {
                for (Consumer<String> consumer : consumers) {
                    if (consumer != null) {
                        consumer.accept(line);
                    }
                }
            };
        }
    }
}
