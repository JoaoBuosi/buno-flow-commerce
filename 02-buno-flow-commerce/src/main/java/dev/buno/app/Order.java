package dev.buno.app;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="orders")
public class Order {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public String customerId;
 @Column(precision=19,scale=2) public BigDecimal total;
 public String status;
 public Instant createdAt=Instant.now();
 protected Order(){}
 public Order(String c,BigDecimal t){customerId=c;total=t;status="CREATED";}
}
