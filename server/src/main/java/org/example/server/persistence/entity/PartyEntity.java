package org.example.server.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "party_master")
public class PartyEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name="party_type", nullable=false) private String partyType;
    @Column(name="party_code", nullable=false, unique=true) private String partyCode;
    @Column(nullable=false) private String name;
    @Column(name="contact_person") private String contactPerson;
    private String phone;
    private String email;
    private String gstin;
    private String address;
    @Column(name="opening_balance") private Double openingBalance;
    @Column(name="is_active") private Integer active;
    public Integer getId(){return id;} public void setId(Integer v){id=v;}
    public String getPartyType(){return partyType;} public void setPartyType(String v){partyType=v;}
    public String getPartyCode(){return partyCode;} public void setPartyCode(String v){partyCode=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getContactPerson(){return contactPerson;} public void setContactPerson(String v){contactPerson=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getGstin(){return gstin;} public void setGstin(String v){gstin=v;}
    public String getAddress(){return address;} public void setAddress(String v){address=v;}
    public Double getOpeningBalance(){return openingBalance;} public void setOpeningBalance(Double v){openingBalance=v;}
    public Integer getActive(){return active;} public void setActive(Integer v){active=v;}
}
