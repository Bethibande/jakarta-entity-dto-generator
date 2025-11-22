package com.bethibande.process.example;

import com.bethibande.process.annotation.EntityDTO;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

@Entity
@EntityDTO
public class EntityWithMethodAccessors {

    private Long id;

    public void setId(final Long id) {
        this.id = id;
    }

    @Id
    public Long getId() {
        return id;
    }

    @Transient
    public String getSomeString() {
        return "";
    }

}
