package org.example.server.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name="lookup_master")
public class LookupEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(name="lookup_type", nullable=false) private String lookupType;
    @Column(name="lookup_code", nullable=false) private String lookupCode;
    @Column(name="lookup_value", nullable=false) private String lookupValue;
    private String description;
    @Column(name="display_order") private Integer displayOrder;
    @Column(name="is_active") private Integer active;
    public Integer getId(){return id;} public void setId(Integer v){id=v;}
    public String getLookupType(){return lookupType;} public void setLookupType(String v){lookupType=v;}
    public String getLookupCode(){return lookupCode;} public void setLookupCode(String v){lookupCode=v;}
    public String getLookupValue(){return lookupValue;} public void setLookupValue(String v){lookupValue=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public Integer getDisplayOrder(){return displayOrder;} public void setDisplayOrder(Integer v){displayOrder=v;}
    public Integer getActive(){return active;} public void setActive(Integer v){active=v;}
}
