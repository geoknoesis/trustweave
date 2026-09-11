import com.bettercloud.vault.response.LogicalResponse;
import com.bettercloud.vault.rest.RestResponse;
import com.bettercloud.vault.api.Logical;
import java.nio.charset.StandardCharsets;
import java.util.Map;
class VaultResponseReviewProbe {
    public static void main(String[] args) {
        String fixture = "{\"data\":{\"type\":\"ed25519\",\"latest_version\":1,\"keys\":{\"1\":{\"public_key\":\"fixture-public-key\"}}}}";
        var response = new LogicalResponse(new RestResponse(200, "application/json", fixture.getBytes(StandardCharsets.UTF_8)), 0, Logical.logicalOperations.readV1);
        Object keys = response.getData().get("keys");
        System.out.println("getData().keys runtime type = " + keys.getClass().getName());
        System.out.println("SDK Map cast can succeed = " + (keys instanceof Map));
        System.out.println("getDataObject().keys is structured = " + response.getDataObject().get("keys").isObject());
    }
}
