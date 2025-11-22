package com.bethibande.process.example;

import com.bethibande.process.annotation.EntityDTO;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
@EntityDTO(expandProperties = {"entity"})
public class ManyToOneEntity extends EntityBase {

    @ManyToOne
    public OneToManyEntity entity;

}
