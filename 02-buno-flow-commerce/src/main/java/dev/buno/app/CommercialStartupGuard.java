package dev.buno.app;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fail-fast guard executed by Application.main BEFORE Spring Boot, Flyway or
 * the datasource is initialized. This does not certify an operational release:
 * real restore, independently reviewed security and delivered mail/alerts are
 * separate mandatory gates.
 *
 * Reads environment values for checks but NEVER includes them in exceptions.
 */
public final class CommercialStartupGuard {
  private CommercialStartupGuard() {}

  private static final Pattern INVITE = Pattern.compile("[A-Za-z0-9_-]{24,128}");
  private static final Pattern ADDRESS = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[A-Za-z]{2,}");
  private static final Pattern MODE = Pattern.compile("(?i)(require|verify-ca|verify-full)");

  private static boolean isTrue(Map<String,String> env, String name) {
    return "true".equals(env.get(name));
  }
  private static String value(Map<String,String> env,String name) {
    return env.getOrDefault(name,"").trim();
  }
  private static boolean production(Map<String,String> env) {
    return "production".equalsIgnoreCase(value(env,"RAILWAY_ENVIRONMENT_NAME"))
        || "production".equalsIgnoreCase(value(env,"COMMERCE_RUNTIME_ENV"))
        || isTrue(env,"COMMERCE_LIVE_CUSTOMERS_ENABLED");
  }

  public static List<String> inspect(Map<String,String> env) {
    if (!production(env)) return List.of();
    var problems = new ArrayList<String>();
    if (!isTrue(env,"COMMERCE_LIVE_CUSTOMERS_ENABLED"))
      problems.add("COMMERCE_LIVE_CUSTOMERS_ENABLED must be explicitly true in production");
    if (!isTrue(env,"COMMERCE_PILOT_ONLY"))
      problems.add("COMMERCE_PILOT_ONLY must be true (invite-only first customers)");
    if (!INVITE.matcher(value(env,"COMMERCE_PILOT_INVITE_CODE")).matches())
      problems.add("COMMERCE_PILOT_INVITE_CODE must be a strong 24+ character invite value");
    if (!isTrue(env,"COMMERCE_IP_RATE_LIMIT_ENABLED"))
      problems.add("COMMERCE_IP_RATE_LIMIT_ENABLED must be true");
    if (value(env,"COMMERCE_IP_HASH_KEY").length()<32)
      problems.add("COMMERCE_IP_HASH_KEY must be provisioned independently (32+ characters)");
    if (!isTrue(env,"COMMERCE_OWNER_MFA_REQUIRED"))
      problems.add("COMMERCE_OWNER_MFA_REQUIRED must be true");
    if (!isTrue(env,"COMMERCE_OWNER_MFA_SELF_ENROLLMENT_ENABLED"))
      problems.add("COMMERCE_OWNER_MFA_SELF_ENROLLMENT_ENABLED must be true");
    if (value(env,"COMMERCE_MFA_ENCRYPTION_KEY").isEmpty())
      problems.add("COMMERCE_MFA_ENCRYPTION_KEY must be provided from an approved vault");

    if (!isTrue(env,"COMMERCE_MAIL_ENABLED"))
      problems.add("COMMERCE_MAIL_ENABLED must be true");
    if (!ADDRESS.matcher(value(env,"COMMERCE_MAIL_FROM")).matches())
      problems.add("COMMERCE_MAIL_FROM must be a plausible verified-sender address");
    var provider = value(env,"COMMERCE_MAIL_PROVIDER");
    if (!provider.equals("resend") && !provider.equals("smtp"))
      problems.add("COMMERCE_MAIL_PROVIDER must be explicit: resend or smtp");
    if (provider.equals("resend") && value(env,"RESEND_API_KEY").isEmpty())
      problems.add("RESEND_API_KEY must be configured");
    if (provider.equals("smtp") &&
        (value(env,"SMTP_HOST").isEmpty() || value(env,"SMTP_USERNAME").isEmpty() ||
         value(env,"SMTP_PASSWORD").isEmpty() || !"true".equals(value(env,"SMTP_STARTTLS"))))
      problems.add("SMTP must have host/user/password and required STARTTLS enabled");

    String frontend=value(env,"COMMERCE_FRONTEND_URL");
    String cors=value(env,"COMMERCE_CORS_ORIGIN");
    if (!httpsOrigin(frontend))
      problems.add("COMMERCE_FRONTEND_URL must use HTTPS without embedded credentials");
    if (!httpsOrigin(cors))
      problems.add("COMMERCE_CORS_ORIGIN must be an HTTPS origin");
    if (httpsOrigin(frontend) && httpsOrigin(cors) && !origin(frontend).equals(origin(cors)))
      problems.add("COMMERCE_CORS_ORIGIN must match the approved frontend origin");

    String url=value(env,"SPRING_DATASOURCE_URL");
    String normalized=url.toLowerCase(Locale.ROOT);
    if (!normalized.startsWith("jdbc:postgresql://") ||
        normalized.contains("localhost") || normalized.contains("127.0.0.1") ||
        normalized.contains("[::1]") || !MODE.matcher(jdbcSslMode(url)).matches())
      problems.add("SPRING_DATASOURCE_URL must be a remote PostgreSQL JDBC URL with sslmode=require/verify-ca/verify-full");
    if (value(env,"SPRING_DATASOURCE_USERNAME").isEmpty() ||
        value(env,"SPRING_DATASOURCE_PASSWORD").isEmpty() ||
        "app".equals(value(env,"SPRING_DATASOURCE_PASSWORD")))
      problems.add("A non-default database account and password must be configured");

    return List.copyOf(problems);
  }

  private static String jdbcSslMode(String jdbcUrl) {
    int at=jdbcUrl.indexOf('?');
    if(at<0)return "";
    for(String part:jdbcUrl.substring(at+1).split("&")) {
      String[] bits=part.split("=",2);
      if(bits.length==2 && bits[0].equalsIgnoreCase("sslmode"))return bits[1];
    }
    return "";
  }
  private static String origin(String input) {
    try {
      URI u=URI.create(input);
      if(!"https".equals(u.getScheme()) || u.getRawUserInfo()!=null ||
          u.getHost()==null || u.getHost().isBlank() || u.getFragment()!=null ||
          u.getRawQuery()!=null) return "";
      int port=u.getPort();
      if(port!=-1 && port!=443)return "";
      return u.getScheme()+"://"+u.getHost().toLowerCase(Locale.ROOT);
    } catch(IllegalArgumentException e) {return "";}
  }
  private static boolean httpsOrigin(String input) {
    return !origin(input).isBlank();
  }

  public static void enforce(Map<String,String> env) {
    List<String> errors=inspect(env);
    if(!errors.isEmpty()) {
      // Never print config values: messages contain variable names/policy only.
      throw new IllegalStateException(
          "COMMERCIAL STARTUP BLOCKED: "+String.join("; ",errors));
    }
  }
}
