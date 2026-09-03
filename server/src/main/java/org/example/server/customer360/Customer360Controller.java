package org.example.server.customer360;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customer-360")
public class Customer360Controller {
    private final Customer360Service service;
    public Customer360Controller(Customer360Service service){this.service=service;}
    @GetMapping("/{id}/summary") public Customer360Dtos.Summary summary(@PathVariable int id){return service.summary(id);}
    @GetMapping("/{id}/contacts") public List<Customer360Dtos.ContactRow> contacts(@PathVariable int id){return service.contacts(id);}
    @PostMapping("/{id}/contacts") public Customer360Dtos.ContactRow saveContact(@PathVariable int id,@RequestBody Customer360Dtos.ContactSave d){return service.saveContact(id,d);}
    @DeleteMapping("/{id}/contacts/{contactId}") public Customer360Dtos.Ok deleteContact(@PathVariable int id,@PathVariable long contactId,@RequestParam long rowVersion){service.deleteContact(id,contactId,rowVersion);return new Customer360Dtos.Ok(true,"Deleted");}
    @GetMapping("/{id}/quotations") public List<Customer360Dtos.QuotationRow> quotations(@PathVariable int id){return service.quotations(id);}
    @GetMapping("/{id}/invoices") public List<Customer360Dtos.InvoiceRow> invoices(@PathVariable int id){return service.invoices(id);}
    @GetMapping("/{id}/payments") public List<Customer360Dtos.PaymentRow> payments(@PathVariable int id){return service.payments(id);}
    @GetMapping("/{id}/notes") public List<Customer360Dtos.NoteRow> notes(@PathVariable int id){return service.notes(id);}
    @PostMapping("/{id}/notes") public Customer360Dtos.NoteRow saveNote(@PathVariable int id,@RequestBody Customer360Dtos.NoteSave d){return service.saveNote(id,d);}
    @DeleteMapping("/{id}/notes/{noteId}") public Customer360Dtos.Ok deleteNote(@PathVariable int id,@PathVariable long noteId,@RequestParam long rowVersion){service.deleteNote(id,noteId,rowVersion);return new Customer360Dtos.Ok(true,"Deleted");}
}
