package org.example.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class RoleEntity {
    @Id private Integer id;
    @Column(name = "role_name", nullable = false) private String name;
    @Column(nullable = false) private Integer active;

    public Integer getId() { return id; }
    public String getName() { return name; }
    public Integer getActive() { return active; }
}
