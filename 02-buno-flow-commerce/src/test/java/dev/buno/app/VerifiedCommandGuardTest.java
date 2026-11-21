package dev.buno.app;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class VerifiedCommandGuardTest {
  private final String secret="demo-only-not-for-prod-0123456789abcdef";
  private final UUID store=UUID.fromString("11111111-1111-4111-8111-111111111111");
  private final UUID order=UUID.fromString("22222222-2222-4222-8222-222222222222");
  private final String operation="op_33333333333343338333333333333333";

  @Test void disabledGateAllowsLegacyFlow() {
    var guard=new VerifiedCommandGuard(false,"");
    assertNull(guard.authorize(store,order,"PROCESSING",null,null,null,null));
  }
  @Test void enabledGateFailsClosedWithoutSecret() {
    var guard=new VerifiedCommandGuard(true,"");
    assertEquals(503,assertThrows(ResponseStatusException.class,
      ()->guard.authorize(store,order,"PROCESSING",null,null,null,null)).getStatusCode().value());
  }
  @Test void validProofBindsStoreOrderOperationAndVersion() {
    var guard=new VerifiedCommandGuard(true,secret);
    long expires=Instant.now().getEpochSecond()+60;
    String sig=VerifiedCommandGuard.sign(secret,
      VerifiedCommandGuard.message(store,order,operation,expires,2));
    var proof=guard.authorize(store,order,"PROCESSING",operation,Long.toString(expires),"2",sig);
    assertEquals(2,proof.expectedVersion());
    String id=operation.substring(3);
    assertEquals(UUID.fromString(id.substring(0,8)+"-"+id.substring(8,12)+"-"+
      id.substring(12,16)+"-"+id.substring(16,20)+"-"+id.substring(20)),
      proof.operationId());
    assertEquals(403,assertThrows(ResponseStatusException.class,
      ()->guard.authorize(UUID.randomUUID(),order,"PROCESSING",operation,Long.toString(expires),"2",sig)).getStatusCode().value());
    assertEquals(403,assertThrows(ResponseStatusException.class,
      ()->guard.authorize(store,order,"PROCESSING",operation,Long.toString(expires),"3",sig)).getStatusCode().value());
    assertEquals(403,assertThrows(ResponseStatusException.class,
      ()->guard.authorize(store,order,"PROCESSING",operation,Long.toString(expires),"2","0".repeat(64))).getStatusCode().value());
  }
  @Test void staleProofRejected() {
    var guard=new VerifiedCommandGuard(true,secret);
    long expired=Instant.now().getEpochSecond()-20;
    String sig=VerifiedCommandGuard.sign(secret,
      VerifiedCommandGuard.message(store,order,operation,expired,1));
    assertEquals(403,assertThrows(ResponseStatusException.class,
      ()->guard.authorize(store,order,"PROCESSING",operation,Long.toString(expired),"1",sig)).getStatusCode().value());
  }
}
