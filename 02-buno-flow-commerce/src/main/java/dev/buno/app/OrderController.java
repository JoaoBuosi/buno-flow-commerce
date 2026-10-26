package dev.buno.app;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
@RestController @RequestMapping("/api/orders")
public class OrderController {
 private final OrderService service; private final OrderRepository repo;
 public OrderController(OrderService s,OrderRepository r){service=s;repo=r;}
 public record CreateOrder(String customerId,BigDecimal total){}
 @PostMapping public Order create(@RequestBody CreateOrder r){return service.create(r.customerId(),r.total());}
 @GetMapping public List<Order> all(){return repo.findAll();}
}
