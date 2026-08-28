package org.example.service;

import org.example.api.returns.ReturnApiClient;

/** Accounting-safe return state changes delegated to the Spring server. */
public final class ReturnWorkflowService {
    private static final ReturnApiClient API = new ReturnApiClient();
    private ReturnWorkflowService() {}
    public static void approve(String returnNo){API.approve(returnNo);}
    public static void reject(String returnNo,String reason){API.reject(returnNo,reason);}
    public static void recordRefund(String returnNo,double refundAmount){API.refund(returnNo,refundAmount);}
    public static void delete(String returnNo,boolean salesReturn){API.delete(returnNo,salesReturn);}
    public static void cancel(String returnNo,boolean salesReturn){API.cancel(returnNo,salesReturn);}
}
