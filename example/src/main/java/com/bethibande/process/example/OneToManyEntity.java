package com.bethibande.process.example;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.process.annotation.FilterType;
import com.bethibande.process.annotation.PropertyFilter;
import com.bethibande.process.annotation.VirtualDTOField;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;

import java.util.List;

@Entity
// Instead of returning a large number of entities, we use a virtual field to return an entity count.
@EntityDTO(excludeProperties = "entities")
@EntityDTO(filter = @PropertyFilter(type = FilterType.INCLUDE, properties = {"id", "entityCount"}))
public class OneToManyEntity extends EntityBase {

    private List<ManyToOneEntity> entities;

    @OneToMany(mappedBy = "entity")
    public List<ManyToOneEntity> getEntities() {
        return entities;
    }

    public void setEntities(final List<ManyToOneEntity> entities) {
        this.entities = entities;
    }

    @Transient
    @VirtualDTOField
    public int getEntityCount() {
        return entities.size();
    }

}
