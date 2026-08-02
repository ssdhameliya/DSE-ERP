package org.example;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
public final class NewScreensSmoke {
 public static void main(String[] args) throws Exception {
  ConfigManager.load(); DatabaseManager.initialize(); CountDownLatch done=new CountDownLatch(1); java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
  Platform.startup(()->{try{for(String f:List.of("Masterdata.fxml","Reports.fxml","EmailSettings.fxml")){if(FXMLLoader.load(NewScreensSmoke.class.getResource("/fxml/pages/"+f))==null)throw new IllegalStateException(f);System.out.println("FXML_OK "+f);}}catch(Throwable e){failure.set(e);e.printStackTrace();}finally{done.countDown();Platform.exit();}});
  if(!done.await(45,TimeUnit.SECONDS))throw new IllegalStateException("FXML timeout");if(failure.get()!=null)throw new RuntimeException(failure.get());
 }
}
