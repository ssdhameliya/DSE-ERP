package org.example.model;

public interface InvoiceLine {

    String getItemDescription();

    double getQuantity();

    double getRate();

    double getGstPercent();

    double getLineTotal();

    void setLineTotal(double value);

}
