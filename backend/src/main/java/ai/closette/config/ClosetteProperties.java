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
    private RateLimits ratelimit = new RateLimits();

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

    public RateLimits getRatelimit() {
        return ratelimit;
    }

    public void setRatelimit(RateLimits ratelimit) {
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
        private String publicEndpoint;
        private String region = "us-east-1";
        public String getPublicEndpoint() { return publicEndpoint; }
        public void setPublicEndpoint(String value) { publicEndpoint = value; }
        public String getRegion() { return region; }
        public void setRegion(String value) { region = value; }
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

    /**
     * Budgets, in the cost units declared on each endpoint. Every bucket carries
     * three windows: the minute stops hammering, the day caps one day's bill, and
     * the week stops someone spending the daily maximum seven days running.
     */
    public static class RateLimits {

        public enum Mode {
            /** Development-only observation without enforcement. */
            LOG,
            /** Reject with 429 once a window is spent. */
            ENFORCE
        }

        private Mode mode = Mode.ENFORCE;
        private Window user = new Window(50, 288, 900);
        private Window unverifiedUser = new Window(20, 30, 60);
        private Window ip = new Window(120, 900, 2500);
        private Window verifyCode = new Window(5, 20, 40);
        private Window registerIp = new Window(3, 10, 25);
        private Window loginIp = new Window(10, 200, 600);
        // Per-email so one address can't be hammered, but generous enough that an
        // attacker targeting someone else's email cannot lock them out for long.
        private Window loginEmail = new Window(5, 50, 150);
        private Window passwordResetIp = new Window(3, 15, 40);
        private Window passwordResetEmail = new Window(1, 5, 15);

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public Window getUser() {
            return user;
        }

        public void setUser(Window user) {
            this.user = user;
        }

        public Window getUnverifiedUser() {
            return unverifiedUser;
        }

        public void setUnverifiedUser(Window unverifiedUser) {
            this.unverifiedUser = unverifiedUser;
        }


        public Window getVerifyCode() {
            return verifyCode;
        }

        public void setVerifyCode(Window verifyCode) {
            this.verifyCode = verifyCode;
        }

        public Window getRegisterIp() {
            return registerIp;
        }

        public void setRegisterIp(Window registerIp) {
            this.registerIp = registerIp;
        }

        public Window getLoginIp() {
            return loginIp;
        }

        public void setLoginIp(Window loginIp) {
            this.loginIp = loginIp;
        }

        public Window getLoginEmail() {
            return loginEmail;
        }

        public void setLoginEmail(Window loginEmail) {
            this.loginEmail = loginEmail;
        }

        public Window getPasswordResetIp() {
            return passwordResetIp;
        }

        public void setPasswordResetIp(Window passwordResetIp) {
            this.passwordResetIp = passwordResetIp;
        }

        public Window getPasswordResetEmail() {
            return passwordResetEmail;
        }

        public void setPasswordResetEmail(Window passwordResetEmail) {
            this.passwordResetEmail = passwordResetEmail;
        }

        /** The three windows of one budget. A zero or negative value disables that window. */
        public static class Window {
            private int perMinute;
            private int perDay;
            private int perWeek;

            public Window() {
            }

            public Window(int perMinute, int perDay, int perWeek) {
                this.perMinute = perMinute;
                this.perDay = perDay;
                this.perWeek = perWeek;
            }

            public int getPerMinute() {
                return perMinute;
            }

            public void setPerMinute(int perMinute) {
                this.perMinute = perMinute;
            }

            public int getPerDay() {
                return perDay;
            }

            public void setPerDay(int perDay) {
                this.perDay = perDay;
            }

            public int getPerWeek() {
                return perWeek;
            }

            public void setPerWeek(int perWeek) {
                this.perWeek = perWeek;
            }
        }
    }
}
