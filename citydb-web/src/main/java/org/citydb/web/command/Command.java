package org.citydb.web.command;

import org.citydb.cli.ExecutionException;
import org.citydb.web.config.ConfigLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@CommandLine.Command(name = "mailCommand")
public class Command implements Callable<Integer> {

    @CommandLine.Option(names = "--config-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Load configuration from this file.")
    private Path configFile;

    private final ConfigLoader configLoader;

    @Autowired
    public Command(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    @Override
    public Integer call() {
        try {
            configLoader.loadConfig(configFile);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        return CommandLine.ExitCode.OK;
    }
}