package org.example.server.master;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/master")
public class MasterDataController {
 private final MasterDataService service; public MasterDataController(MasterDataService s){service=s;}
 @GetMapping("/health") public Map<String,Object> health(){return Map.of("status","UP","phase",2);}
 @GetMapping("/parties") public List<MasterDtos.PartyDto> parties(@RequestParam String type){return service.parties(type);}
 @PostMapping("/parties") public MasterDtos.PartyDto saveParty(@RequestBody MasterDtos.PartyDto d){return service.saveParty(d);}
 @PutMapping("/parties") public MasterDtos.PartyDto updateParty(@RequestBody MasterDtos.PartyDto d){return service.updateParty(d);}
 @DeleteMapping("/parties/{id}") public MasterDtos.OperationResponse deleteParty(@PathVariable int id){service.deleteParty(id);return new MasterDtos.OperationResponse(true,"OK");}
 @GetMapping("/parties/exists") public MasterDtos.ExistsResponse partyExists(@RequestParam String code){return new MasterDtos.ExistsResponse(service.partyExists(code));}
 @GetMapping("/parties/next-code") public MasterDtos.NextCodeResponse partyNext(@RequestParam String type){return new MasterDtos.NextCodeResponse(service.nextPartyCode(type));}

 @GetMapping("/items") public List<MasterDtos.ItemDto> items(){return service.items();}
 @PostMapping("/items") public MasterDtos.ItemDto saveItem(@RequestBody MasterDtos.ItemDto d){return service.saveItem(d);}
 @PutMapping("/items") public MasterDtos.ItemDto updateItem(@RequestBody MasterDtos.ItemDto d){return service.updateItem(d);}
 @DeleteMapping("/items/{code}") public MasterDtos.OperationResponse deleteItem(@PathVariable String code){service.deleteItem(code);return new MasterDtos.OperationResponse(true,"OK");}
 @GetMapping("/items/exists") public MasterDtos.ExistsResponse itemExists(@RequestParam String code){return new MasterDtos.ExistsResponse(service.itemExists(code));}
 @GetMapping("/items/next-code") public MasterDtos.NextCodeResponse itemNext(){return new MasterDtos.NextCodeResponse(service.nextItemCode());}
 @PostMapping("/items/bulk") public MasterDtos.OperationResponse bulkItems(@RequestBody List<MasterDtos.ItemDto> rows){service.saveItems(rows);return new MasterDtos.OperationResponse(true,"OK");}

 @GetMapping("/lookups") public List<MasterDtos.LookupDto> lookups(@RequestParam String type){return service.lookups(type);}
 @GetMapping("/lookups/values") public MasterDtos.ValuesResponse values(@RequestParam String type){return new MasterDtos.ValuesResponse(service.values(type));}
 @GetMapping("/lookups/values-by-category-code") public MasterDtos.ValuesResponse valuesByCode(@RequestParam String code){return new MasterDtos.ValuesResponse(service.valuesByCategoryCode(code));}
 @PostMapping("/lookups") public MasterDtos.LookupDto saveLookup(@RequestBody MasterDtos.LookupDto d){return service.saveLookup(d);}
 @PutMapping("/lookups") public MasterDtos.LookupDto updateLookup(@RequestBody MasterDtos.LookupDto d){return service.updateLookup(d);}
 @DeleteMapping("/lookups/{id}") public MasterDtos.OperationResponse deleteLookup(@PathVariable int id){service.deleteLookup(id);return new MasterDtos.OperationResponse(true,"OK");}
 @GetMapping("/lookups/next-code") public MasterDtos.NextCodeResponse lookupNext(@RequestParam String type){return new MasterDtos.NextCodeResponse(service.nextLookupCode(type));}

 @GetMapping("/categories") public List<MasterDtos.CategoryDto> categories(){return service.categories();}
 @PostMapping("/categories") public MasterDtos.CategoryDto addCategory(@RequestParam String name){return service.addCategory(name);}
 @PutMapping("/categories/upsert") public MasterDtos.CategoryDto upsertCategory(@RequestBody MasterDtos.CategoryUpsertRequest d){return service.upsertCategory(d);}
 @PutMapping("/categories/rename") public MasterDtos.CategoryDto rename(@RequestBody MasterDtos.RenameCategoryRequest r){return service.renameCategory(r.oldName(),r.newName());}
 @DeleteMapping("/categories") public MasterDtos.OperationResponse deleteCategory(@RequestParam String name){service.deleteCategory(name);return new MasterDtos.OperationResponse(true,"OK");}
}
