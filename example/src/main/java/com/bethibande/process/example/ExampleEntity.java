package com.bethibande.process.example;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.process.annotation.FilterType;
import com.bethibande.process.annotation.IgnoreProperty;
import com.bethibande.process.annotation.PropertyFilter;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Email;

import java.util.List;

@Entity
@EntityDTO(expandProperties = {"entity"})
@EntityDTO(filter = @PropertyFilter(properties = {"id", "entity.id"}), expandProperties = {"entity"})
@EntityDTO(filter = @PropertyFilter(type = FilterType.INCLUDE, properties = {"id", "someString"}))
@EntityDTO(filter = @PropertyFilter(type = FilterType.INCLUDE, properties = {"id", "someString", "email"}))
public class ExampleEntity extends EntityBase {

    @NotBlank
    @Size(min = 1, max = 100)
    public String someString;

    @Email
    @IgnoreProperty
    public String email;

    @Embedded
    public EmbeddableTimestamp timestamp;

    @ManyToOne
    public ReferencedEntity entity;

    @ElementCollection
    public List<String> list;

}
