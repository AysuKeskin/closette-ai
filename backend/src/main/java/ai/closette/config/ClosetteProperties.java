package ai.closette.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for the {@code closette.*} configuration tree.
 */
@ConfigurationProperties(prefix = "closette")
public class ClosetteProperties {

    private Jwt jwt = new Jwt();
    private Ai ai = new Ai();
    private Storage storage = new Storage();
    private RateLimit ratelimit = new RateLimit();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public RateLimit getRatelimit() {
        return ratelimit;
    }

    public void setRatelimit(RateLimit ratelimit) {
        this.ratelimit = ratelimit;
    }

    public static class Jwt {
        private String secret;
        private long accessTtlMinutes = 60;
        private long refreshTtlDays = 30;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getAccessTtlMinutes() {
            return accessTtlMinutes;
        }

        public void setAccessTtlMinutes(long accessTtlMinutes) {
            this.accessTtlMinutes = accessTtlMinutes;
        }

        public long getRefreshTtlDays() {
            return refreshTtlDays;
        }

        public void setRefreshTtlDays(long refreshTtlDays) {
            this.refreshTtlDays = refreshTtlDays;
        }
    }

    public static class Ai {
        private String baseUrl = "http://localhost:8000";
        private int timeoutMs = 20000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Storage {
        private String endpoint = "http://localhost:9000";
        private String accessKey;
        private String secretKey;
        private String bucketWardrobe = "wardrobe";
        private String bucketBeauty = "beauty";
        private int signedUrlTtlMinutes = 60;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucketWardrobe() {
            return bucketWardrobe;
        }

        public void setBucketWardrobe(String bucketWardrobe) {
            this.bucketWardrobe = bucketWardrobe;
        }

        public String getBucketBeauty() {
            return bucketBeauty;
        }

        public void setBucketBeauty(String bucketBeauty) {
            this.bucketBeauty = bucketBeauty;
        }

        public int getSignedUrlTtlMinutes() {
            return signedUrlTtlMinutes;
        }

        public void setSignedUrlTtlMinutes(int signedUrlTtlMinutes) {
            this.signedUrlTtlMinutes = signedUrlTtlMinutes;
        }
    }

    public static class RateLimit {
        private int aiRequestsPerMinute = 20;

        public int getAiRequestsPerMinute() {
            return aiRequestsPerMinute;
        }

        public void setAiRequestsPerMinute(int aiRequestsPerMinute) {
            this.aiRequestsPerMinute = aiRequestsPerMinute;
        }
    }
}
