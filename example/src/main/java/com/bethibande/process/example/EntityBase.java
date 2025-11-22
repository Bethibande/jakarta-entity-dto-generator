package com.bethibande.process.example;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public class EntityBase {

    @Id
    public Long id;

}
