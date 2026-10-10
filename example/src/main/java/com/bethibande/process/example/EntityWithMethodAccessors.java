package com.bethibande.process.example;

import com.bethibande.process.annotation.EntityDTO;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;

@Entity
@EntityDTO
public class EntityWithMethodAccessors {

    private Long id;

    @NotNull
    public void setId(final Long id) {
        this.id = id;
    }

    @Id
    @NotNull
    public Long getId() {
        return id;
    }

    @Transient
    public String getSomeString() {
        return "";
    }

}
