package ai.closette;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ClosetteApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the full Spring context (JPA mappings, security, beans) wires up.
    }
}
