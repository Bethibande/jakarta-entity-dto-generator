# Jakarta entity dto generator
This is a simple annotation processor meant to generate DTO classes from JPA entity definitions.

> [!WARNING]
> Only many-to-one relationships are supported at the moment.
> One-to-many relationships and `@ElementCollection` are not yet supported.

### Example
See [here](example/src/main/java/com/bethibande/process/example/ExampleEntity.java) for the full example.

The following entity definition will generate two DTO classes: `ExampleEntityDTO` and `ExampleEntityDTOWithoutId`
```java
@Entity
// This will generate a DTO of the full model
@EntityDTO(expandProperties = {"entity"})
// This will generate the same DTO but without id fields
@EntityDTO(excludeProperties = {"id", "entity.id"}, expandProperties = {"entity"})
public class ExampleEntity extends EntityBase {

    public String someString;

    @Embedded
    public EmbeddableTimestamp timestamp;

    @ManyToOne
    public ReferencedEntity entity;

}
```
You can easily convert your entities into a DTO
```java
@GET
@Path("/api/v1/example-entity/{id}")
public ExampleEntityDTO findById(final @PathParam("id") long id) {
    final ExampleEntity entity = repository.findById(id);
    return ExampleEntityDTO.from(entity); // This is null-safe
}
```
The serialized output of this DTO might look like this:
```json
{
  "someString": "abc",
  "created": "2025-11-22T20:51",
  "updated": "2025-11-22T20:51",
  "entity": {
    "name": "test",
    "id": 12
  },
  "id": 1
}
```
If we remove `expandProperties = {"entity"}` from the annotation the entity will be deflated into a single `entityId` field like this:
```json
{
  "someString": "abc",
  "created": "2025-11-22T20:51",
  "updated": "2025-11-22T20:51",
  "entityId": 12,
  "id": 1
}
```