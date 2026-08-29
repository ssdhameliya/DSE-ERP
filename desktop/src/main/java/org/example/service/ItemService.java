package org.example.service;

import org.example.api.master.MasterApiClient;
import org.example.config.ConfigManager;
import org.example.dao.ItemDAO;
import org.example.model.Item;

import java.util.*;

public class ItemService {
    private final ItemDAO dao=new ItemDAO();
    private final MasterApiClient api=new MasterApiClient();
    private boolean useApi(){return ConfigManager.isApiDataEnabled();}
    public List<Item> search(String q,int limit){return useApi()?api.searchItems(q,limit):dao.getAll().stream().filter(i->{String h=((i.getItemCode()==null?"":i.getItemCode())+" "+(i.getDescription()==null?"":i.getDescription())+" "+(i.getRemarks()==null?"":i.getRemarks())+" "+(i.getHsn()==null?"":i.getHsn())).toLowerCase(Locale.ROOT);return q==null||q.isBlank()||h.contains(q.toLowerCase(Locale.ROOT));}).limit(Math.max(1,limit)).toList();}
    public void save(Item i){if(useApi())api.saveItem(i);else dao.save(i);invalidate();}
    public MasterApiClient.ItemBulkDeleteValidation validateBulkDelete(List<String> codes){if(!useApi())throw new IllegalStateException("Bulk delete requires managed server mode.");return api.validateBulkDeleteItems(codes);}
    public MasterApiClient.OperationResponse bulkDelete(List<String> codes){if(!useApi())throw new IllegalStateException("Bulk delete requires managed server mode.");var result=api.bulkDeleteItems(codes);invalidate();return result;}
    public void update(Item i){if(useApi())api.updateItem(i);else dao.update(i);invalidate();}
    public void delete(int id){if(useApi()){Item match=getAll().stream().filter(x->x.getId()==id).findFirst().orElseThrow();api.deleteItem(match.getItemCode(),match.getRowVersion());}else dao.delete(id);invalidate();}
    public void delete(Item item){if(item==null)return;if(useApi())api.deleteItem(item.getItemCode(),item.getRowVersion());else dao.delete(item);invalidate();}
    public void delete(String c){if(useApi()){Item match=getAll().stream().filter(x->Objects.equals(x.getItemCode(),c)).findFirst().orElseThrow();delete(match);}else{dao.deleteByCode(c);invalidate();}}
    public List<Item> getAll(){return useApi()?ReferenceDataCache.getList("ITEM:ALL",api::items,ItemService::copy):dao.getAll();}
    public String nextCode(){return useApi()?api.nextItemCode():dao.nextCode();}
    public void saveOrUpdate(Item i){if(existsByCode(i.getItemCode()))update(i);else save(i);}
    public boolean existsByCode(String c){return useApi()?api.itemExists(c):dao.existsByCode(c);}
    private void invalidate(){ReferenceDataCache.invalidate("ITEM");}
    private static Item copy(Item i){Item x=new Item();x.setId(i.getId());x.setRowVersion(i.getRowVersion());x.setItemCode(i.getItemCode());x.setDescription(i.getDescription());x.setCategory(i.getCategory());x.setBrand(i.getBrand());x.setMaterial(i.getMaterial());x.setSize(i.getSize());x.setUnit(i.getUnit());x.setHsn(i.getHsn());x.setGst(i.getGst());x.setDiscountPercent(i.getDiscountPercent());x.setPurchasePrice(i.getPurchasePrice());x.setSellingPrice(i.getSellingPrice());x.setOpeningStock(i.getOpeningStock());x.setMinimumStock(i.getMinimumStock());x.setReservedStock(i.getReservedStock());x.setLocation(i.getLocation());x.setRemarks(i.getRemarks());return x;}
}
