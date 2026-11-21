package com.example.orders.persistence;

import com.example.orders.api.OrderResponse;
import com.example.orders.api.PaymentWebhookRequest;
import com.example.orders.api.ProcessedWebhookResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean createOrder(String orderId, long amountCents) {
        return jdbcTemplate.update("""
                INSERT INTO orders (order_id, amount_cents, status, payment_applications)
                VALUES (?, ?, 'PENDING', 0)
                ON CONFLICT (order_id) DO NOTHING
                """, orderId, amountCents) == 1;
    }

    public Optional<OrderResponse> findOrder(String orderId) {
        return queryOrder("SELECT order_id, amount_cents, status, payment_applications, paid_at "
                + "FROM orders WHERE order_id = ?", orderId);
    }

    public Optional<OrderResponse> lockOrder(String orderId) {
        return queryOrder("SELECT order_id, amount_cents, status, payment_applications, paid_at "
                + "FROM orders WHERE order_id = ? FOR UPDATE", orderId);
    }

    public int claimWebhook(PaymentWebhookRequest event) {
        return jdbcTemplate.update("""
                INSERT INTO processed_webhooks (event_id, event_type, order_id, amount_cents)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, event.eventId(), event.eventType(), event.orderId(), event.amountCents());
    }

    public Optional<ProcessedWebhookResponse> findWebhook(String eventId) {
        List<ProcessedWebhookResponse> rows = jdbcTemplate.query("""
                SELECT event_id, event_type, order_id, amount_cents, processed_at
                FROM processed_webhooks WHERE event_id = ?
                """, (resultSet, rowNumber) -> mapWebhook(resultSet), eventId);
        return rows.stream().findFirst();
    }

    public List<ProcessedWebhookResponse> findRecentWebhooks() {
        return jdbcTemplate.query("""
                SELECT event_id, event_type, order_id, amount_cents, processed_at
                FROM processed_webhooks ORDER BY processed_at DESC, event_id DESC LIMIT 50
                """, (resultSet, rowNumber) -> mapWebhook(resultSet));
    }

    public int markPaid(String orderId) {
        return jdbcTemplate.update("""
                UPDATE orders
                SET status = 'PAID', paid_at = CURRENT_TIMESTAMP,
                    payment_applications = payment_applications + 1
                WHERE order_id = ? AND status = 'PENDING'
                """, orderId);
    }

    private Optional<OrderResponse> queryOrder(String sql, String orderId) {
        return jdbcTemplate.query(sql, this::mapOrder, orderId).stream().findFirst();
    }

    private OrderResponse mapOrder(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OrderResponse(
                resultSet.getString("order_id"),
                resultSet.getLong("amount_cents"),
                resultSet.getString("status"),
                resultSet.getInt("payment_applications"),
                resultSet.getObject("paid_at", java.time.OffsetDateTime.class));
    }

    private ProcessedWebhookResponse mapWebhook(ResultSet resultSet) throws SQLException {
        return new ProcessedWebhookResponse(
                resultSet.getString("event_id"),
                resultSet.getString("event_type"),
                resultSet.getString("order_id"),
                resultSet.getLong("amount_cents"),
                resultSet.getObject("processed_at", java.time.OffsetDateTime.class));
    }
}
