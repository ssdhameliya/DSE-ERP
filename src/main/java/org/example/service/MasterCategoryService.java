package org.example.service;

import org.example.api.master.MasterApiClient;
import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;
import java.sql.*;
import java.util.*;

/** Phase-2 service for Master Data categories. Keeps controller database-neutral. */
public class MasterCategoryService {
    private final MasterApiClient api = new MasterApiClient();
    public record Category(String code, String name, long valueCount) {}
    private boolean useApi(){ return ConfigManager.isApiDataEnabled(); }

    public List<Category> getAll(){
        if(useApi()) return api.categories().stream().map(c->new Category(c.categoryCode(),c.categoryName(),c.valueCount())).toList();
        List<Category> out=new ArrayList<>();
        String sql="""
            SELECT mc.category_code,
                   mc.category_name,
                   COUNT(lm.id) AS value_count
            FROM master_category mc
            LEFT JOIN lookup_master lm
                   ON lm.lookup_type = mc.category_name
            GROUP BY mc.category_code,
                     mc.category_name,
                     mc.display_order
            ORDER BY mc.display_order,
                     mc.category_name
            """;
        try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){while(r.next())out.add(new Category(r.getString(1),r.getString(2),r.getLong(3)));}catch(SQLException e){throw new IllegalStateException("Could not load master categories",e);}return out;
    }
    public void add(String name){ if(useApi()){api.addCategory(name);return;} String n=normalize(name);try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO master_category(category_code,category_name) VALUES(?,?)")){p.setString(1,code(n));p.setString(2,n);p.executeUpdate();}catch(SQLException e){throw new IllegalStateException("Could not add category",e);} }
    public void rename(String oldName,String newName){ if(useApi()){api.renameCategory(oldName,normalize(newName));return;} String n=normalize(newName);try(Connection c=DatabaseManager.getConnection()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("UPDATE master_category SET category_name=? WHERE category_name=?")){p.setString(1,n);p.setString(2,oldName);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("UPDATE lookup_master SET lookup_type=? WHERE lookup_type=?")){p.setString(1,n);p.setString(2,oldName);p.executeUpdate();}c.commit();}catch(SQLException e){throw new IllegalStateException("Could not rename category",e);} }
    public void delete(String name){ if(useApi()){api.deleteCategory(name);return;} try(Connection c=DatabaseManager.getConnection()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("DELETE FROM lookup_master WHERE lookup_type=?")){p.setString(1,name);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("DELETE FROM master_category WHERE category_name=?")){p.setString(1,name);p.executeUpdate();}c.commit();}catch(SQLException e){throw new IllegalStateException("Could not delete category",e);} }
    private String normalize(String v){return v==null?"":v.trim().toUpperCase(Locale.ROOT);} private String code(String n){String c=n.replaceAll("[^A-Z0-9]+","_").replaceAll("^_+|_+$","");return c.isBlank()?"CATEGORY":c;}
}
