package org.example.controller;
public final class SalesScreenContext { private static String invoiceNo; private SalesScreenContext(){} public static void select(String value){invoiceNo=value;} public static String invoice(){return invoiceNo;} }
