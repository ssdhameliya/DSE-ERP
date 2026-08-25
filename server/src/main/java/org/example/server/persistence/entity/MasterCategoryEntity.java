package org.example.server.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name="master_category")
public class MasterCategoryEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Version @Column(name="row_version",nullable=false) private Long rowVersion=0L;
    @Column(name="category_code", nullable=false, unique=true) private String categoryCode;
    @Column(name="category_name", nullable=false, unique=true) private String categoryName;
    private String description;
    @Column(name="display_order") private Integer displayOrder;
    @Column(name="is_active") private Integer active;
    public Integer getId(){return id;} public void setId(Integer v){id=v;}
    public String getCategoryCode(){return categoryCode;} public void setCategoryCode(String v){categoryCode=v;}
    public String getCategoryName(){return categoryName;} public void setCategoryName(String v){categoryName=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public Integer getDisplayOrder(){return displayOrder;} public void setDisplayOrder(Integer v){displayOrder=v;}
    public Integer getActive(){return active;} public void setActive(Integer v){active=v;}

 public Long getRowVersion(){return rowVersion;} public void setRowVersion(Long v){rowVersion=v==null?0L:v;}
}
