package pl.settly.settly_api.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableAsync} lets notification listeners run off the request thread;
 * {@code @EnableScheduling} drives the daily settle-up reminder.
 *
 * <p>Note the scheduler is in-process: with more than one API instance the reminder would fire once
 * per instance. Single instance today — revisit (a lock or an external scheduler) before scaling
 * out.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {}
