package com.bethibande.process.example;

import com.bethibande.process.annotation.EntityDTO;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
@EntityDTO(expandProperties = {"entity"})
@EntityDTO(excludeProperties = {"id", "entity.id"}, expandProperties = {"entity"})
public class ExampleEntity extends EntityBase {

    public String someString;

    @Embedded
    public EmbeddableTimestamp timestamp;

    @ManyToOne
    public ReferencedEntity entity;

}
