package ai.closette.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    public MinioClient minioClient(ClosetteProperties props) {
        ClosetteProperties.Storage s = props.getStorage();
        return MinioClient.builder()
                .endpoint(s.getEndpoint())
                .region(s.getRegion())
                .credentials(s.getAccessKey(), s.getSecretKey())
                .build();
    }
}
