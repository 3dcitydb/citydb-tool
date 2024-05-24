package org.citydb.web.command;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.ServletContext;
import org.apache.logging.log4j.Logger;
import org.citydb.config.Config;
import org.citydb.config.ConfigManager;
import org.citydb.logging.LoggerManager;
import org.citydb.web.config.Constants;
import org.citydb.web.config.WebOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@CommandLine.Command(name = "command")
public class Command implements Callable<Integer> {

    @CommandLine.Option(names = "--config-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Load configuration from this file.")
    private Path configFile;

    private final WebOptions webOptions;
    private final ServletContext servletContext;

    private final Logger logger = LoggerManager.getInstance().getLogger(Command.class);

    @Autowired
    public Command(WebOptions webOptions, ServletContext servletContext) {
        this.webOptions = webOptions;
        this.servletContext = servletContext;
    }

    @Override
    public Integer call() {
        Config config = new Config();

        if (configFile != null) {
            logger.info("Loading configuration from file " + configFile + "...");
            try {
                config.putAll(ConfigManager.newInstance().read(configFile, Config.class, Config::new));
            } catch (Exception e) {
                logger.error("Failed to load config file.", e);
            }
        } else {
            // try to load from resource
            try (InputStream stream = new BufferedInputStream(servletContext.getResourceAsStream(Constants.CONFIG_FILE))) {
                config.putAll(JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8), Config.class));
            } catch (Exception e) {
                //
            }
        }

        webOptions.apply(config);

        return CommandLine.ExitCode.OK;
    }
}