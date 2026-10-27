package com.smartqueue.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "smartqueue")
public class AppProperties {
    private String publicBaseUrl = "http://localhost:5173";
    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Redis redis = new Redis();
    private Mail mail = new Mail();
    private RateLimit rateLimit = new RateLimit();
    private Scheduling scheduling = new Scheduling();

    @Getter @Setter
    public static class Jwt {
        private String secret;
        private long expirationMinutes = 60;
    }

    @Getter @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Getter @Setter
    public static class Redis {
        private boolean enabled = false;
        private int boardTtlSeconds = 5;
    }

    @Getter @Setter
    public static class Mail {
        private boolean enabled = false;
        private String from = "no-reply@smartqueue.local";
    }

    @Getter @Setter
    public static class RateLimit {
        private boolean enabled = true;
        private int authPerMinute = 20;
        private int publicPerMinute = 60;
    }

    @Getter @Setter
    public static class Scheduling {
        private boolean enabled = true;
    }
}
