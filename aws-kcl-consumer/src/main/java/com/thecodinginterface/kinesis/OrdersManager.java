package com.thecodinginterface.kinesis;

import java.util.*;

public class OrdersManager {

    private final Set<Order> orders;

    public OrdersManager() {
        this.orders = new HashSet<>();
    }

    public void addOrder(Order order) {
        orders.add(order);
    }

    public double calcAverageOrderSize() {
        double avgOrderSize = 0.0;
        if (!orders.isEmpty()) {
            double totalSales = orders.stream()
                    .flatMap(o -> o.getOrderItems().stream())
                    .map(orderItem -> orderItem.getProductPrice() * orderItem.getProductQuantity())
                    .reduce(0.0, Double::sum);
            avgOrderSize = totalSales / orders.size();
        }
        return avgOrderSize;
    }
}
