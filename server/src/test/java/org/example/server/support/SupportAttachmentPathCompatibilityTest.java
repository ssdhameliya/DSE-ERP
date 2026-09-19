package org.example.server.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SupportAttachmentPathCompatibilityTest {
    @TempDir Path temp;

    @Test
    void resolvesPortableAndLegacyWindowsPaymentProofsInsideManagedAttachmentsOnly() throws Exception {
        Path proof = temp.resolve("Attachments/PaymentProofs/83/proof.pdf");
        Files.createDirectories(proof.getParent());
        Files.writeString(proof, "proof");

        SupportService service = new SupportService(null, null, null, null);
        Field workspace = SupportService.class.getDeclaredField("workspacePath");
        workspace.setAccessible(true);
        workspace.set(service, temp.toString());
        Method resolve = SupportService.class.getDeclaredMethod("resolveAttachmentReference", String.class);
        resolve.setAccessible(true);

        assertEquals(proof.toAbsolutePath().normalize(), resolve.invoke(service, "Attachments\\PaymentProofs\\83\\proof.pdf"));
        assertEquals(proof.toAbsolutePath().normalize(), resolve.invoke(service, "D:\\DSE ERP Workspace\\PaymentProofs\\83\\proof.pdf"));
        assertNull(resolve.invoke(service, "/etc/passwd"));
        assertNull(resolve.invoke(service, "../outside.txt"));
    }
}
