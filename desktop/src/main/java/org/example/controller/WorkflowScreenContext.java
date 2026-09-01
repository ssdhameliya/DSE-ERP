package org.example.controller;
/** One-shot deep link into an existing Project Execution register. */
public final class WorkflowScreenContext {
    public record Target(String type,int id){}
    private static Target pending;
    private WorkflowScreenContext(){}
    public static synchronized void select(String type,int id){pending=new Target(type,id);}
    public static synchronized Target consume(String type){if(pending==null||type==null||!type.equalsIgnoreCase(pending.type()))return null;Target t=pending;pending=null;return t;}
}
