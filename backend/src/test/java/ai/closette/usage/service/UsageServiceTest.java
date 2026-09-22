package ai.closette.usage.service;

import ai.closette.ai.service.AIService;
import ai.closette.auth.service.AuthService;
import ai.closette.common.exception.ApiException;
import ai.closette.storage.service.StorageService;
import ai.closette.support.TestData;
import ai.closette.usage.model.AiOperation;
import ai.closette.usage.model.UsageGrant;
import ai.closette.usage.model.UsageOperation;
import ai.closette.usage.repository.UsageGrantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The allowance, and the spending of it.
 *
 * These cover the parts that cost money when they are wrong: charging twice for
 * one attempt, taking payment for work that produced nothing, letting one
 * operation quietly consume another, and minting the same month more than once.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "closette.usage.mode=enforce",
        "closette.usage.free.outfit=2",
        "closette.usage.free.photo-analysis=3",
        "closette.usage.welcome.outfit=4",
        "closette.usage.welcome.photo-analysis=7",
})
class UsageServiceTest {

    @Autowired
    AuthService authService;

    @Autowired
    UsageService usage;

    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactions;

    @Autowired
    UsageGrantRepository grants;

    @MockitoBean
    StorageService storage;

    @MockitoBean
    AIService aiService;

    private UUID user() {
        return TestData.newUser(authService);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private int remaining(UUID userId, AiOperation operation) {
        return usage.balances(userId).stream()
                .filter(b -> b.operation() == operation)
                .findFirst().orElseThrow().remaining();
    }

    @Test
    void spendingReducesWhatIsLeft() {
        UUID userId = user();

        usage.reserve(userId, AiOperation.OUTFIT, key());

        assertThat(remaining(userId, AiOperation.OUTFIT)).isEqualTo(1);
    }

    @Test
    void theSameAttemptArrivingTwiceIsChargedOnce() {
        // A phone that loses the reply retries with the same key. Charging again
        // takes an allowance for a result the user never saw.
        UUID userId = user();
        String attempt = key();

        usage.reserve(userId, AiOperation.OUTFIT, attempt);
        usage.reserve(userId, AiOperation.OUTFIT, attempt);

        assertThat(remaining(userId, AiOperation.OUTFIT)).isEqualTo(1);
    }

    @Test
    void workThatProducedNothingGivesTheAllowanceBack() {
        UUID userId = user();
        Optional<UsageOperation> reserved = usage.reserve(userId, AiOperation.OUTFIT, key());

        reserved.ifPresent(usage::release);

        assertThat(remaining(userId, AiOperation.OUTFIT)).isEqualTo(2);
    }

    @Test
    void aSettledOperationStaysCharged() {
        UUID userId = user();
        Optional<UsageOperation> reserved = usage.reserve(userId, AiOperation.OUTFIT, key());

        reserved.ifPresent(usage::settle);
        reserved.ifPresent(usage::release);   // too late: the user kept the result

        assertThat(remaining(userId, AiOperation.OUTFIT)).isEqualTo(1);
    }

    @Test
    void spendingTheLastOneRefusesTheNext() {
        UUID userId = user();
        usage.reserve(userId, AiOperation.OUTFIT, key());
        usage.reserve(userId, AiOperation.OUTFIT, key());

        assertThatThrownBy(() -> usage.reserve(userId, AiOperation.OUTFIT, key()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void operationsAreCountedSeparately() {
        // Reading an ingredient label must not quietly cost an outfit; that is the
        // whole reason these are allowances rather than one shared balance.
        UUID userId = user();
        usage.reserve(userId, AiOperation.OUTFIT, key());
        usage.reserve(userId, AiOperation.OUTFIT, key());

        assertThat(remaining(userId, AiOperation.PHOTO_ANALYSIS)).isEqualTo(3);
        usage.reserve(userId, AiOperation.PHOTO_ANALYSIS, key());
        assertThat(remaining(userId, AiOperation.PHOTO_ANALYSIS)).isEqualTo(2);
    }

    @Test
    void theMonthIsGrantedOnlyOnce() {
        // Asked for by every balance read and every reservation. Minting a grant
        // each time would mean the limit is never reached.
        UUID userId = user();
        usage.balances(userId);
        usage.balances(userId);
        usage.reserve(userId, AiOperation.OUTFIT, key());

        long monthly = grants.findLive(userId, Instant.now()).stream()
                .filter(g -> g.getOperation() == AiOperation.OUTFIT)
                .filter(g -> g.getSource() == UsageGrant.Source.MONTHLY)
                .count();
        assertThat(monthly).isEqualTo(1);
    }

    @Test
    void anotherAccountHasItsOwnAllowance() {
        UUID first = user();
        UUID second = user();
        usage.reserve(first, AiOperation.OUTFIT, key());
        usage.reserve(first, AiOperation.OUTFIT, key());

        assertThat(remaining(second, AiOperation.OUTFIT)).isEqualTo(2);
    }

    @Test
    void theWelcomeBonusCoversEveryOperationAndIsGivenOnce() {
        // The first version checked "has this account been welcomed?" inside the
        // loop, so the grant it had just written for the first operation stopped
        // the rest: an account welcomed to photos and to nothing else.
        UUID userId = user();
        int outfitBefore = remaining(userId, AiOperation.OUTFIT);
        int photosBefore = remaining(userId, AiOperation.PHOTO_ANALYSIS);

        usage.grantWelcome(userId);
        usage.grantWelcome(userId);   // a second call must add nothing

        assertThat(remaining(userId, AiOperation.OUTFIT)).isEqualTo(outfitBefore + 4);
        assertThat(remaining(userId, AiOperation.PHOTO_ANALYSIS)).isEqualTo(photosBefore + 7);
    }

    @Test
    void aReadOnlyCallerStillGetsCharged() {
        // Outfit generation reads the wardrobe in a read-only transaction. A charge
        // that joined it was silently discarded, so the most expensive flow in the
        // app was the one operation nobody paid for.
        UUID userId = user();

        new org.springframework.transaction.support.TransactionTemplate(transactions) {{
            setReadOnly(true);
        }}.execute(status -> usage.reserve(userId, AiOperation.OUTFIT, key()));

        assertThat(remaining(userId, AiOperation.OUTFIT)).isEqualTo(1);
    }
}
