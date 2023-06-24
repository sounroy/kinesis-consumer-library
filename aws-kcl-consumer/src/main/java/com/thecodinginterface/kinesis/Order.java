package com.thecodinginterface.kinesis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Order {
    @JsonProperty("order_items")
    private List<OrderItem> orderItems = new ArrayList<>();

    @JsonProperty("customer_id")
    private String customerID;

    @JsonProperty("order_id")
    private String orderID;

    @JsonProperty("seller_id")
    private String sellerID;

    public Order() {}

    public Order(List<OrderItem> orderItems, String customerID, String orderID, String sellerID) {
        this.orderItems = orderItems;
        this.customerID = customerID;
        this.orderID = orderID;
        this.sellerID = sellerID;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public String getCustomerID() {
        return customerID;
    }

    public void setCustomerID(String customerID) {
        this.customerID = customerID;
    }

    public String getOrderID() {
        return orderID;
    }

    public void setOrderID(String orderID) {
        this.orderID = orderID;
    }

    public String getSellerID() {
        return sellerID;
    }

    public void setSellerID(String sellerID) {
        this.sellerID = sellerID;
    }

    @Override
    public String toString() {
        return String.format("Order{orderID='%s', customerID='%s', sellerID='%s', orderItems=%s}",
                orderID, customerID, sellerID, Arrays.toString(orderItems.toArray()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equal(orderItems, order.orderItems) &&
                Objects.equal(customerID, order.customerID) &&
                Objects.equal(orderID, order.orderID) &&
                Objects.equal(sellerID, order.sellerID);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(orderItems, customerID, orderID, sellerID);
    }
}
