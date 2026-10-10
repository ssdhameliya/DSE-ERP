package org.example.server.creditdebit;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CreditDebitNoteController {

    private final CreditDebitNoteService service;

    public CreditDebitNoteController(CreditDebitNoteService service) {
        this.service = service;
    }

    @GetMapping("/credit-notes")
    public List<CreditDebitNoteDtos.CreditNoteDto> listCreditNotes() {
        return service.listCreditNotes();
    }

    @GetMapping("/credit-notes/{id}")
    public CreditDebitNoteDtos.CreditNoteDto getCreditNote(@PathVariable Long id) {
        return service.getCreditNote(id).orElse(null);
    }

    @PostMapping("/credit-notes")
    public CreditDebitNoteDtos.CreditNoteDto createCreditNote(
            @RequestBody CreditDebitNoteDtos.CreditNoteDto dto,
            @RequestParam(required = false) String user) {
        return service.createCreditNote(dto, user);
    }

    @GetMapping("/debit-notes")
    public List<CreditDebitNoteDtos.DebitNoteDto> listDebitNotes() {
        return service.listDebitNotes();
    }

    @GetMapping("/debit-notes/{id}")
    public CreditDebitNoteDtos.DebitNoteDto getDebitNote(@PathVariable Long id) {
        return service.getDebitNote(id).orElse(null);
    }

    @PostMapping("/debit-notes")
    public CreditDebitNoteDtos.DebitNoteDto createDebitNote(
            @RequestBody CreditDebitNoteDtos.DebitNoteDto dto,
            @RequestParam(required = false) String user) {
        return service.createDebitNote(dto, user);
    }

    @PostMapping("/credit-notes/{id}/cancel")
    public boolean cancelCreditNote(@PathVariable Long id, @RequestParam(required = false) String user) {
        return service.cancelCreditNote(id, user);
    }

    @PostMapping("/debit-notes/{id}/cancel")
    public boolean cancelDebitNote(@PathVariable Long id, @RequestParam(required = false) String user) {
        return service.cancelDebitNote(id, user);
    }
}
