package dev.buno.app;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Map;
@Service
public class OrderService {
 private final OrderRepository repo; private final KafkaTemplate<String,Object> kafka;
 public OrderService(OrderRepository r,KafkaTemplate<String,Object> k){repo=r;kafka=k;}
 @Transactional public Order create(String customer,BigDecimal total){
  if(customer==null||customer.isBlank()||total==null||total.signum()<=0)throw new IllegalArgumentException("customerId e total positivo são obrigatórios");
  Order o=repo.save(new Order(customer,total));
  // Demonstração: em produção, use transactional outbox para não perder evento entre commit e publish.
  kafka.send("orders.created",String.valueOf(o.id),Map.of("orderId",o.id,"customerId",customer,"total",total.toString()));
  return o;
 }
}
