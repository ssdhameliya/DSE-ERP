package org.example.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;

import java.util.*;
import java.util.prefs.Preferences;

/** Optional runtime UI-contract diagnostics. Never mutates business state. */
public final class UiDiagnostics {
    private static final Preferences PREFS=Preferences.userRoot().node("org/example/dseerp/ui-diagnostics");
    private static final String ENABLED="enabled";private static final int DETAIL_LIMIT=60;
    private UiDiagnostics(){}
    public static boolean isEnabled(){return Boolean.getBoolean("dse.erp.ui.diagnostics")||PREFS.getBoolean(ENABLED,false);}
    public static void setEnabled(boolean enabled){PREFS.putBoolean(ENABLED,enabled);DesktopLog.info("UI","UI_DIAGNOSTICS_CHANGED","enabled="+enabled);}
    public static void audit(Node root,String context){if(!isEnabled()||root==null)return;String screen=context==null||context.isBlank()?root.getClass().getSimpleName():context;Counters c=new Counters();List<String>issues=new ArrayList<>();Map<String,Set<String>> semantics=new LinkedHashMap<>();walk(root,c,issues,semantics);for(var e:semantics.entrySet())if(Set.of("reference","document").contains(e.getKey())&&e.getValue().size()>=3)issues.add("SEMANTIC_COLLISION semantic="+e.getKey()+" fields="+String.join(" | ",e.getValue()));DesktopLog.info("UI","UI_SCREEN_AUDIT","screen="+screen+" buttons="+c.buttons+" labels="+c.labels+" tables="+c.tables+" kpiSections="+c.kpiSections+" selectors="+c.selectors+" actionMenus="+c.actionMenus+" issues="+issues.size());for(int i=0;i<Math.min(DETAIL_LIMIT,issues.size());i++)DesktopLog.warn("UI","UI_CONTRACT_ISSUE","screen="+screen+" | "+issues.get(i));if(issues.size()>DETAIL_LIMIT)DesktopLog.warn("UI","UI_CONTRACT_ISSUE","screen="+screen+" | additionalIssues="+(issues.size()-DETAIL_LIMIT));}
    private static void walk(Node node,Counters c,List<String>issues,Map<String,Set<String>>semantics){if(node instanceof MenuButton m&&isActionMenu(m))c.actionMenus++;if(node instanceof ComboBox<?>)c.selectors++;if(node instanceof ButtonBase b){c.buttons++;if(importantAction(b)&&semantic(b)==null)issues.add("UNMAPPED_BUTTON id="+id(b)+" text="+safe(b.getText()));}if(node instanceof Label l){c.labels++;if(fieldLabel(l)){String sem=labelSemantic(l);if(sem==null)issues.add("UNMAPPED_FIELD_LABEL id="+id(l)+" text="+safe(l.getText()));else semantics.computeIfAbsent(sem,k->new LinkedHashSet<>()).add(safe(l.getText()));}}if(node instanceof TableView<?>t){c.tables++;if(!Boolean.TRUE.equals(t.getProperties().get("erp.table.dynamic-layout.installed")))issues.add("TABLE_WITHOUT_DYNAMIC_LAYOUT id="+id(t));}if(node instanceof Pane p&&p.getStyleClass().contains(ResponsiveKpiLayoutManager.KPI_SECTION_STYLE)){c.kpiSections++;Object profile=p.getProperties().get("erp.kpi.profile.resolved"),columns=p.getProperties().get("erp.kpi.columns.resolved");DesktopLog.info("UI","UI_KPI_LAYOUT","id="+id(p)+" profile="+safe(profile)+" columns="+safe(columns));}if(node instanceof Parent p)for(Node x:p.getChildrenUnmodifiable())walk(x,c,issues,semantics);}
    private static boolean isActionMenu(MenuButton m){String x=String.join(" ",m.getStyleClass()).toLowerCase(Locale.ROOT);return x.contains("action")||"actions".equalsIgnoreCase(safe(m.getText()));}
    private static boolean importantAction(ButtonBase b){if(Boolean.TRUE.equals(b.getProperties().get("erp.icon.skip"))||safe(b.getText()).isBlank())return false;String s=String.join(" ",b.getStyleClass()).toLowerCase(Locale.ROOT);return s.contains("approved-button")||s.contains("primary-button")||s.contains("secondary-button")||s.contains("danger-button")||s.contains("settings-action-button")||s.contains("backup-")||s.contains("row-action");}
    private static boolean fieldLabel(Label l){String s=String.join(" ",l.getStyleClass()).toLowerCase(Locale.ROOT);return s.contains("field-label")||s.contains("field-caption");}
    private static String labelSemantic(Label l){Object x=l.getProperties().get("erp.label.icon.semantic");if(x!=null&&!safe(x).isBlank())return safe(x);if(l.getGraphic()!=null){Object g=l.getGraphic().getProperties().get("erp.icon.semantic");if(g!=null&&!safe(g).isBlank())return safe(g);}return UiSemanticRegistry.fieldSemantic(l.getText());}
    private static String semantic(ButtonBase b){Object x=b.getProperties().get("erp.icon.semantic");if(x!=null&&!safe(x).isBlank())return safe(x);if(b.getGraphic()!=null){Object g=b.getGraphic().getProperties().get("erp.icon.semantic");if(g!=null&&!safe(g).isBlank())return safe(g);}return null;}
    private static String id(Node n){return n.getId()==null||n.getId().isBlank()?n.getClass().getSimpleName():n.getId();}
    private static String safe(Object x){return x==null?"":x.toString().replace('\n',' ').replace('\r',' ').trim();}
    private static final class Counters{int buttons,labels,tables,kpiSections,selectors,actionMenus;}
}
