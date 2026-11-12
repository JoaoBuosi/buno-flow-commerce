package dev.buno.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

/** HTTPS transactional mail transport. Does not expose provider responses or secrets in logs. */
@Component
public class ResendEmailSender {
  private static final URI ENDPOINT=URI.create("https://api.resend.com/emails");
  private final String apiKey;
  private final ObjectMapper json;
  private final HttpClient client;

  public ResendEmailSender(@Value("${commerce.mail.resend-api-key:}") String apiKey,
      ObjectMapper json) {
    this.apiKey=apiKey;
    this.json=json;
    this.client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public boolean configured() { return !apiKey.isBlank(); }

  public void send(SimpleMailMessage message) {
    if(!configured()||message.getFrom()==null||message.getFrom().isBlank()||
        message.getTo()==null||message.getTo().length==0) {
      throw new MailSendException("Transactional email configuration incomplete");
    }
    try {
      String body=json.writeValueAsString(Map.of(
        "from",message.getFrom(),
        "to",Arrays.asList(message.getTo()),
        "subject",message.getSubject()==null?"":message.getSubject(),
        "text",message.getText()==null?"":message.getText()));
      var request=HttpRequest.newBuilder(ENDPOINT)
        .timeout(Duration.ofSeconds(12))
        .header("Content-Type","application/json")
        .header("Accept","application/json")
        .header("User-Agent","BunoFlowCommerce/0.5.3")
        .header("Authorization","Bearer "+apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8))
        .build();
      var response=client.send(request,HttpResponse.BodyHandlers.discarding());
      if(response.statusCode()<200||response.statusCode()>=300)
        throw new MailSendException("Transactional email provider rejected message");
    } catch(JsonProcessingException exc) {
      throw new MailSendException("Could not serialize transactional email",exc);
    } catch(IOException exc) {
      throw new MailSendException("Transactional email network error",exc);
    } catch(InterruptedException exc) {
      Thread.currentThread().interrupt();
      throw new MailSendException("Transactional email request interrupted",exc);
    }
  }
}
