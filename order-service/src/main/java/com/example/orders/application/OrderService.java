package com.example.orders.application;

import com.example.orders.api.CreateOrderRequest;
import com.example.orders.api.OrderResponse;
import com.example.orders.persistence.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        if (!orderRepository.createOrder(request.orderId(), request.amountCents())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already exists");
        }
        logger.info("Created order orderId={} amountCents={}", request.orderId(), request.amountCents());
        return findOrder(request.orderId());
    }

    public OrderResponse findOrder(String orderId) {
        return orderRepository.findOrder(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
