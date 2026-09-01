package org.example.controller;
/** One-shot customer preselection for the existing Create Sale screen. */
public final class CustomerSaleContext {
    private static Integer customerId;
    private CustomerSaleContext(){}
    public static synchronized void select(int id){customerId=id;}
    public static synchronized Integer consume(){Integer v=customerId;customerId=null;return v;}
}
