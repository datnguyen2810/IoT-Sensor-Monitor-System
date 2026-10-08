package com.iot.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "devices.command.timeout-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceCommandTimeoutTask {
    private static final Logger log = LoggerFactory.getLogger(DeviceCommandTimeoutTask.class);
    private final DeviceCommandTransactions commands;
    public DeviceCommandTimeoutTask(DeviceCommandTransactions commands) { this.commands = commands; }

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler deviceTimeoutScheduler() {
        // Never let a slow/offline broker's blocking connect attempt delay command expiration.
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("device-timeout-");
        return scheduler;
    }

    @Scheduled(fixedDelay = 1000)
    public void expirePendingCommands() {
        try {
            for (String device : commands.expiredDeviceCodes()) commands.expire(device);
        } catch (RuntimeException exception) {
            log.error("Unable to expire pending device commands; retry next tick", exception);
        }
    }
}
