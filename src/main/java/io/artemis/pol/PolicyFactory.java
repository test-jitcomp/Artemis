package io.artemis.pol;

import io.artemis.Artemis;
import io.artemis.util.CannotReachHereException;

public class PolicyFactory {

    public enum PolicyName {
        ARTEMIS("artemis");

        public final String name;

        PolicyName(String name) {
            this.name = name;
        }
    }

    public static MutationPolicy create(PolicyName which, Artemis ax, Artemis.ExtraOpts opts) {
        switch (which) {
            case ARTEMIS:
                return new ArtemisPolicy(ax, opts);
            default:
                throw new CannotReachHereException("Unsupported policy with name: " + which.name);
        }
    }
}
