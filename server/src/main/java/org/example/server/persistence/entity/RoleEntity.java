package org.example.server.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class RoleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name = "role_name", nullable = false) private String name;
    private Integer active;
    public Integer getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return Integer.valueOf(1).equals(active); }
}
