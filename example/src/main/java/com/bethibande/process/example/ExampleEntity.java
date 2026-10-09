package com.bethibande.process.example;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.process.annotation.FilterType;
import com.bethibande.process.annotation.PropertyFilter;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import java.util.List;

@Entity
@EntityDTO(expandProperties = {"entity"})
@EntityDTO(filter = @PropertyFilter(properties = {"id", "entity.id"}), expandProperties = {"entity"})
@EntityDTO(filter = @PropertyFilter(type = FilterType.INCLUDE, properties = {"id", "someString"}))
public class ExampleEntity extends EntityBase {

    public String someString;

    @Embedded
    public EmbeddableTimestamp timestamp;

    @ManyToOne
    public ReferencedEntity entity;

    @ElementCollection
    public List<String> list;

}
