package ai.closette;

import ai.closette.config.ClosetteProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ClosetteProperties.class)
public class ClosetteApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClosetteApplication.class, args);
    }
}
