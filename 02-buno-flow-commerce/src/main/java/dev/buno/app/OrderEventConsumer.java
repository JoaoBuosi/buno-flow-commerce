package dev.buno.app;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class OrderEventConsumer {
 @KafkaListener(topics="orders.created",groupId="buno-fulfillment")
 public void onCreated(String event){System.out.println("Fulfillment event received: "+event);}
}
