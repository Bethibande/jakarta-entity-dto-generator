package com.bethibande.process.example;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class ReferencedEntity {

    @Id
    public UUID id;

    @Column
    public String name;
}
