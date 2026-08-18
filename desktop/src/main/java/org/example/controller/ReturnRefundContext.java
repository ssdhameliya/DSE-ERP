package org.example.controller;
public final class ReturnRefundContext {
    private static String returnNo;
    private ReturnRefundContext(){}
    public static void select(String no){ returnNo=no; }
    public static String value(){ return returnNo; }
}
