package org.example.api.runtime;
import javafx.application.Platform;import javafx.scene.control.*;import org.example.config.ConfigManager;import org.example.util.OwnedAlert;import java.time.Instant;import java.util.concurrent.atomic.AtomicReference;
/** User-visible shared-server connection state; dialogs occur only on state transitions. */
public final class RuntimeConnectionState{
 public enum State{CONNECTED,DISCONNECTED,RECONNECTING}
 private static final AtomicReference<State> STATE=new AtomicReference<>(State.CONNECTED);private static volatile Instant changedAt=Instant.now();private RuntimeConnectionState(){}
 public static State state(){return STATE.get();}public static Instant changedAt(){return changedAt;}
 public static void disconnected(String message){State prior=STATE.getAndSet(State.DISCONNECTED);changedAt=Instant.now();if(prior==State.CONNECTED&&ConfigManager.isSharedClient())show("Company server disconnected","DSE ERP cannot reach the company server. Your unsaved screen remains open. Check the network or server address; DSE ERP will retry automatically every 30 seconds.\n\n"+message,Alert.AlertType.WARNING);}
 public static void reconnecting(){if(STATE.compareAndSet(State.DISCONNECTED,State.RECONNECTING))changedAt=Instant.now();}
 public static void connected(){State prior=STATE.getAndSet(State.CONNECTED);changedAt=Instant.now();if(prior!=State.CONNECTED&&ConfigManager.isSharedClient())show("Connection restored","DSE ERP reconnected to the company server. You can continue working.",Alert.AlertType.INFORMATION);}
 private static void transition(State next,String title,String text,Alert.AlertType type){State prior=STATE.getAndSet(next);changedAt=Instant.now();if(prior!=next&&ConfigManager.isSharedClient())show(title,text,type);}
 private static void show(String title,String text,Alert.AlertType type){Platform.runLater(()->{Alert a=new OwnedAlert(type,text,ButtonType.OK);a.setTitle(title);a.setHeaderText(title);a.show();});}
}
