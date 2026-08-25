package org.example.controller;
public final class PurchaseReconScreenContext{private static Integer pendingId;private PurchaseReconScreenContext(){}public static synchronized void select(Integer id){pendingId=id;}public static synchronized Integer consume(){Integer id=pendingId;pendingId=null;return id;}}
