package org.example.server.reconciliation;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/bank-statements")
public class BankReconciliationController {
 private final BankReconciliationService service; public BankReconciliationController(BankReconciliationService service){this.service=service;}
 @PostMapping("/imports") public BankReconciliationDtos.ImportResult importStatement(@RequestBody BankReconciliationDtos.ImportRequest r){return service.importStatement(r);}
 @GetMapping("/imports") public List<BankReconciliationDtos.BatchDto> batches(){return service.batches();}
 @GetMapping("/imports/{id}/source") public BankReconciliationDtos.SourceDto source(@PathVariable Long id){return service.source(id);}
 @GetMapping("/imports/{id}/transactions") public List<BankReconciliationDtos.TransactionDto> transactions(@PathVariable Long id){return service.transactions(id);}
 @GetMapping("/imports/{id}/metrics") public BankReconciliationDtos.Metrics metrics(@PathVariable Long id){return service.metrics(id);}
 @PostMapping("/transactions/{id}/suggest") public List<BankReconciliationDtos.CandidateDto> suggest(@PathVariable Long id){return service.suggest(id);}
 @GetMapping("/transactions/{id}/candidates") public List<BankReconciliationDtos.CandidateDto> candidates(@PathVariable Long id){return service.candidates(id);}
 @PostMapping("/transactions/{id}/match") public BankReconciliationDtos.OperationResult match(@PathVariable Long id,@RequestBody BankReconciliationDtos.MatchRequest r){return service.match(id,r);}
 @PostMapping("/transactions/{id}/expense") public BankReconciliationDtos.OperationResult expense(@PathVariable Long id,@RequestBody BankReconciliationDtos.ExpenseRequest r){return service.expense(id,r);}
 @PostMapping("/transactions/{id}/note") public BankReconciliationDtos.OperationResult note(@PathVariable Long id,@RequestBody BankReconciliationDtos.NoteRequest r){return service.updateNote(id,r);}
 @PostMapping("/transactions/{id}/ignore") public BankReconciliationDtos.OperationResult ignore(@PathVariable Long id,@RequestBody BankReconciliationDtos.IgnoreRequest r){return service.ignore(id,r);}
 @PostMapping("/transactions/{id}/review") public BankReconciliationDtos.OperationResult review(@PathVariable Long id,@RequestBody BankReconciliationDtos.NoteRequest r){return service.review(id,r);}
 @PostMapping("/transactions/{id}/reverse") public BankReconciliationDtos.OperationResult reverse(@PathVariable Long id,@RequestParam(required=false) String user){return service.reverse(id,user);}
 @GetMapping("/transactions/{id}/audit") public List<BankReconciliationDtos.AuditDto> audit(@PathVariable Long id){return service.audit(id);}
}
