package org.citydb.web;

import org.citydb.web.command.Command;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine;

@SpringBootApplication
@EnableCaching
public class Launcher extends SpringBootServletInitializer implements CommandLineRunner  {
    private Command command;
    private ApplicationContext applicationContext;

    @Autowired
    void setCommand(Command command) {
        this.command = command;
    }

    @Autowired
    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        int exitCode = new CommandLine(command).execute(args);
        if (exitCode != CommandLine.ExitCode.OK) {
            System.exit(SpringApplication.exit(applicationContext, () -> exitCode));
        }
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Launcher.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(Launcher.class, args);
    }
}
