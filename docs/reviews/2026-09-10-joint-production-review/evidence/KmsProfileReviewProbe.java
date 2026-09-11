import java.util.Set;
import com.geoknoesis.trustweave.saas.server.kms.KmsProviderConfig;

public class KmsProfileReviewProbe {
    public static void main(String[] args) {
        for (Set<String> profiles : java.util.List.of(Set.of("prod"), Set.of("prod", "local"), Set.of("staging", "local"))) {
            KmsProviderConfig config = new KmsProviderConfig();
            try {
                config.validateForProfiles(profiles);
                System.out.println(profiles + " -> ACCEPTED provider=" + config.getProvider());
            } catch (IllegalStateException expected) {
                System.out.println(profiles + " -> REJECTED");
            }
        }
    }
}
