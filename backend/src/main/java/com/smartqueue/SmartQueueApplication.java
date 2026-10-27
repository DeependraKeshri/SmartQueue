package com.smartqueue;

import com.smartqueue.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** UserDetailsServiceAutoConfiguration is excluded: authentication is JWT based and no default user must exist. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AppProperties.class)
public class SmartQueueApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartQueueApplication.class, args);
    }
}
