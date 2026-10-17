package com.aurapay.api;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<OrderController.DemoOrder> orderMapper = (ResultSet rs, int rowNum) ->
            new OrderController.DemoOrder(
                    rs.getString("order_id"),
                    rs.getString("pack_id"),
                    rs.getString("pack_name"),
                    rs.getInt("credits"),
                    rs.getLong("amount_in_paise"),
                    rs.getString("status"),
                    rs.getBoolean("real_payments_enabled"),
                    rs.getString("created_at")
            );

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(OrderController.DemoOrder order) {
        jdbcTemplate.update("""
                INSERT INTO demo_orders
                (order_id, pack_id, pack_name, credits, amount_in_paise, status, real_payments_enabled, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                order.orderId(), order.packId(), order.packName(), order.credits(),
                order.amountInPaise(), order.status(), order.realPaymentsEnabled(), order.createdAt());
    }

    public Optional<OrderController.DemoOrder> findById(String orderId) {
        List<OrderController.DemoOrder> orders = jdbcTemplate.query(
                "SELECT order_id, pack_id, pack_name, credits, amount_in_paise, status, real_payments_enabled, created_at FROM demo_orders WHERE order_id = ?",
                orderMapper, orderId);
        return orders.stream().findFirst();
    }
}
