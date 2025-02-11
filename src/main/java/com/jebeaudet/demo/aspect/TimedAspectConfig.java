package com.jebeaudet.demo.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimedAspectConfig {

    @Bean
    @ConditionalOnProperty(name="micrometer.observations.annotations.enabled", havingValue = "false")
    TimedAspect1_9_15 timedAspect1_9_15(MeterRegistry registry) {
        TimedAspect1_9_15 timedAspect = new TimedAspect1_9_15(registry);
        return timedAspect;
    }
}
