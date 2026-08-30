package org.example.service;

import org.example.api.ApiRuntime;
import org.example.api.reporting.ReportingApiClient.ReportRequest;

/** Versioned Saved Report payload. Dynamic date presets are stored by name, never flattened to stale fixed dates. */
public record ReportingSavedConfig(int version,String name,String datePreset,ReportRequest request) {
    public static final int CURRENT_VERSION=2;
    public static String encode(String name,String datePreset,ReportRequest request){
        try{return "REPORT_V2:"+ApiRuntime.JSON.writeValueAsString(new ReportingSavedConfig(CURRENT_VERSION,name,datePreset,request));}
        catch(Exception e){throw new IllegalStateException("Could not encode saved report",e);}
    }
    public static ReportingSavedConfig decode(String value){
        if(value==null||!value.startsWith("REPORT_V2:"))return null;
        try{return ApiRuntime.JSON.readValue(value.substring("REPORT_V2:".length()),ReportingSavedConfig.class);}
        catch(Exception e){return null;}
    }
}
