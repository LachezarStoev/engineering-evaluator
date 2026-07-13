package com.pronet.evaluator;

import com.pronet.evaluator.config.AppProperties;
import com.pronet.evaluator.config.ConnectorsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, ConnectorsProperties.class})
public class EngineeringEvaluatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(EngineeringEvaluatorApplication.class, args);
    }
}
