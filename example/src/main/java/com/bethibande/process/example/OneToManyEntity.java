package com.bethibande.process.example;

import com.bethibande.process.annotation.EntityDTO;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

import java.util.List;

@Entity
@EntityDTO(expandProperties = "entities")
public class OneToManyEntity extends EntityBase {

    @OneToMany(mappedBy = "entity")
    public List<ManyToOneEntity> entities;

}
