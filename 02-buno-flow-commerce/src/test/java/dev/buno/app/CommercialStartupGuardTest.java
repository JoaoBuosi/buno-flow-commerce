package dev.buno.app;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline-only negative checks. No DB, tokens, HTTP, credentials or deployment. */
class CommercialStartupGuardTest {
  private Map<String,String> acceptableSyntheticConfig() {
    var env=new HashMap<String,String>();
    env.put("RAILWAY_ENVIRONMENT_NAME","production");
    env.put("COMMERCE_LIVE_CUSTOMERS_ENABLED","true");
    env.put("COMMERCE_PILOT_ONLY","true");
    env.put("COMMERCE_PILOT_INVITE_CODE","A".repeat(32));
    env.put("COMMERCE_IP_RATE_LIMIT_ENABLED","true");
    env.put("COMMERCE_IP_HASH_KEY","B".repeat(40));
    env.put("COMMERCE_OWNER_MFA_REQUIRED","true");
    env.put("COMMERCE_OWNER_MFA_SELF_ENROLLMENT_ENABLED","true");
    env.put("COMMERCE_MFA_ENCRYPTION_KEY","C".repeat(40));
    env.put("COMMERCE_MAIL_ENABLED","true");
    env.put("COMMERCE_MAIL_FROM","no-reply@example.test");
    env.put("COMMERCE_MAIL_PROVIDER","resend");
    env.put("RESEND_API_KEY","fake-only-for-unit-test");
    env.put("COMMERCE_FRONTEND_URL","https://qa.example.test");
    env.put("COMMERCE_CORS_ORIGIN","https://qa.example.test");
    env.put("SPRING_DATASOURCE_URL","jdbc:postgresql://db.example.test:5432/commerce?sslmode=verify-full");
    env.put("SPRING_DATASOURCE_USERNAME","commerce_app");
    env.put("SPRING_DATASOURCE_PASSWORD","not-a-real-credential");
    return env;
  }

  @Test void localAndStagingRemainUnchanged() {
    assertTrue(CommercialStartupGuard.inspect(Map.of()).isEmpty());
    assertTrue(CommercialStartupGuard.inspect(Map.of("RAILWAY_ENVIRONMENT_NAME","staging")).isEmpty());
  }
  @Test void railwayProductionWithoutApprovalFailsClosed() {
    var errors=CommercialStartupGuard.inspect(Map.of("RAILWAY_ENVIRONMENT_NAME","production"));
    assertTrue(errors.size()>=9);
  }
  @Test void explicitCommercialModeOnStagingRequiresTheSameProtection() {
    var errors=CommercialStartupGuard.inspect(Map.of("RAILWAY_ENVIRONMENT_NAME","staging","COMMERCE_LIVE_CUSTOMERS_ENABLED","true"));
    assertFalse(errors.isEmpty());
  }
  @Test void validSyntheticConfigurationPassesStructuralPreflightOnly() {
    var env=acceptableSyntheticConfig();
    assertTrue(CommercialStartupGuard.inspect(env).isEmpty(),CommercialStartupGuard.inspect(env).toString());
  }
  @Test void refusesOpenRegistrationAndDisabledRateLimits() {
    var env=acceptableSyntheticConfig();
    env.put("COMMERCE_PILOT_ONLY","false");
    env.put("COMMERCE_IP_RATE_LIMIT_ENABLED","false");
    assertTrue(CommercialStartupGuard.inspect(env).stream().anyMatch(s->s.contains("PILOT_ONLY")));
    assertTrue(CommercialStartupGuard.inspect(env).stream().anyMatch(s->s.contains("IP_RATE_LIMIT")));
  }
  @Test void refusesDisabledMailAndMissingMfaVaultKey() {
    var env=acceptableSyntheticConfig();
    env.put("COMMERCE_MAIL_ENABLED","false");
    env.remove("COMMERCE_MFA_ENCRYPTION_KEY");
    assertTrue(CommercialStartupGuard.inspect(env).stream().anyMatch(s->s.contains("MAIL_ENABLED")));
    assertTrue(CommercialStartupGuard.inspect(env).stream().anyMatch(s->s.contains("MFA_ENCRYPTION_KEY")));
  }
  @Test void refusesInsecureLocalDbAndWeakPassword() {
    var env=acceptableSyntheticConfig();
    env.put("SPRING_DATASOURCE_URL","jdbc:postgresql://localhost:5432/orders");
    env.put("SPRING_DATASOURCE_PASSWORD","app");
    assertTrue(CommercialStartupGuard.inspect(env).stream().anyMatch(s->s.contains("SPRING_DATASOURCE_URL")));
    assertTrue(CommercialStartupGuard.inspect(env).stream().anyMatch(s->s.contains("database account")));
  }
  @Test void rejectsNonTlsDb() {
    var env=acceptableSyntheticConfig();
    env.put("SPRING_DATASOURCE_URL","jdbc:postgresql://db.example.test:5432/orders?sslmode=disable");
    assertFalse(CommercialStartupGuard.inspect(env).isEmpty());
  }
  @Test void rejectsCorsOriginMismatch() {
    var env=acceptableSyntheticConfig();
    env.put("COMMERCE_CORS_ORIGIN","https://unrelated.example.test");
    assertTrue(CommercialStartupGuard.inspect(env).stream().anyMatch(s->s.contains("CORS_ORIGIN")));
  }
  @Test void doesNotLeakValuesOnError() {
    var env=acceptableSyntheticConfig();
    env.put("COMMERCE_IP_HASH_KEY","confidential-example-not-to-print");
    env.put("COMMERCE_MAIL_FROM","bad-value");
    var ex=assertThrows(IllegalStateException.class,()->CommercialStartupGuard.enforce(env));
    assertFalse(ex.getMessage().contains("confidential-example-not-to-print"));
    assertFalse(ex.getMessage().contains("not-a-real-credential"));
  }
  @Test void refusesWeakInviteAndInsecureFrontend() {
    var env=acceptableSyntheticConfig();
    env.put("COMMERCE_PILOT_INVITE_CODE","short");
    env.put("COMMERCE_FRONTEND_URL","http://localhost:5173");
    assertTrue(CommercialStartupGuard.inspect(env).stream().anyMatch(s->s.contains("PILOT_INVITE_CODE")));
    assertTrue(CommercialStartupGuard.inspect(env).stream().anyMatch(s->s.contains("FRONTEND_URL")));
  }
}
