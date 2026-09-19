package org.example.server.support;

import org.example.server.web.AttachmentUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="dse.uat.attachment.workspace", matches=".+")
class SupportAttachmentRealUatProbeTest {
    @Test
    void recoveredUatPaymentProofOpensAndMissingLegacyPathFailsCleanly() throws Exception {
        SupportService service=new SupportService(null,null,null,null);
        Field workspace=SupportService.class.getDeclaredField("workspacePath"); workspace.setAccessible(true); workspace.set(service,System.getProperty("dse.uat.attachment.workspace"));
        Method read=SupportService.class.getDeclaredMethod("readAttachment",String.class); read.setAccessible(true);
        String managed=System.getProperty("dse.uat.attachment.reference");
        SupportDtos.AttachmentFile file=(SupportDtos.AttachmentFile)read.invoke(service,managed);
        assertTrue(file.data().length>0);
        String legacy=System.getProperty("dse.uat.attachment.missingLegacy");
        if(legacy!=null&&!legacy.isBlank()){
            InvocationTargetException thrown=assertThrows(InvocationTargetException.class,()->read.invoke(service,legacy));
            assertInstanceOf(AttachmentUnavailableException.class,thrown.getCause());
        }
        System.out.println("PAYMENT_PROOF_UAT opened="+file.fileName()+" bytes="+file.data().length+" missingLegacyHandled="+(legacy!=null&&!legacy.isBlank()));
    }
}
