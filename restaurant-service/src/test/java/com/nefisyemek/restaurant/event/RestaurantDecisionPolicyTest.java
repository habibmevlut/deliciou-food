package com.nefisyemek.restaurant.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantDecisionPolicyTest {

    @Test
    void forceRejectWins() {
        RestaurantDecisionPolicy policy = new RestaurantDecisionPolicy(0.0, () -> 0.99);
        assertThat(policy.shouldReject(true, true)).isTrue();
        assertThat(policy.reason(true, false)).isEqualTo("FORCE_REJECT");
    }

    @Test
    void forceAcceptSkipsRandom() {
        RestaurantDecisionPolicy policy = new RestaurantDecisionPolicy(1.0, () -> 0.0);
        assertThat(policy.shouldReject(false, true)).isFalse();
    }

    @Test
    void randomRejectsBelowProbability() {
        RestaurantDecisionPolicy policy = new RestaurantDecisionPolicy(0.2, () -> 0.19);
        assertThat(policy.shouldReject(false, false)).isTrue();
        assertThat(policy.reason(false, false)).isEqualTo("RANDOM_REJECT");
    }

    @Test
    void randomAcceptsAtOrAboveProbability() {
        RestaurantDecisionPolicy policy = new RestaurantDecisionPolicy(0.2, () -> 0.2);
        assertThat(policy.shouldReject(false, false)).isFalse();
    }
}
