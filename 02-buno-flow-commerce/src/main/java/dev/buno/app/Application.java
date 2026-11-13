package dev.buno.app;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    // Validate explicit production customer settings before JDBC/Flyway connects.
    CommercialStartupGuard.enforce(System.getenv());
    SpringApplication.run(Application.class,args);
  }
}
