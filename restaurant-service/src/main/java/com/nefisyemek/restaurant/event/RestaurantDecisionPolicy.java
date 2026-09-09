package com.nefisyemek.restaurant.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.DoubleSupplier;

/**
 * Mutlu yolu ve compensate'i Postman'dan deterministik kılmak için force* bayrakları var.
 * Bayrak yoksa varsayılan %20 rastgele red — brief'teki öğrenme senaryosu.
 */
@Component
public class RestaurantDecisionPolicy {

    private final double rejectProbability;
    private final DoubleSupplier random;

    /**
     * İki kurucu var (test için DoubleSupplier). Spring birden fazla kurucuda
     * {@code @Autowired} görmezse no-arg arar — o da yok, uygulama ayağa kalkmaz.
     */
    @Autowired
    public RestaurantDecisionPolicy(
            @Value("${nefis.restaurant.reject-probability:0.2}") double rejectProbability
    ) {
        this(rejectProbability, Math::random);
    }

    RestaurantDecisionPolicy(double rejectProbability, DoubleSupplier random) {
        this.rejectProbability = rejectProbability;
        this.random = random;
    }

    public boolean shouldReject(boolean forceReject, boolean forceAccept) {
        if (forceReject) {
            return true;
        }
        if (forceAccept) {
            return false;
        }
        return random.getAsDouble() < rejectProbability;
    }

    public String reason(boolean forceReject, boolean forceAccept) {
        if (forceReject) {
            return "FORCE_REJECT";
        }
        if (forceAccept) {
            return null;
        }
        return "RANDOM_REJECT";
    }
}
