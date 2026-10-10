# Jakarta entity dto generator
This is a tool that generates DTO classes from your JPA entity classes

Features
- Generate multiple DTO classes from a single Entity
- Easily convert entities to DTOs
- Convert DTOs back into new or existing entities
- Whitelist and blacklist field filtering using `@PropertyFilter` or `includeProperties`/`excludeProperties`
- Generate virtual fields, see [here](example/src/main/java/com/bethibande/process/example/OneToManyEntity.java) for example
- Generates NotNull annotations for fields if jakarta validation is used

### Usage

> [!NOTE]
> Please note that the library is built for Java 25.

Gradle
```kts
repositories {
    maven {
        url = uri("https://repo.bethibande.com/repositories/maven/releases")
        name = "bethibande-releases"
    }
}

dependencies {
    implementation("com.bethibande.process:annotations:1.7")
    annotationProcessor("com.bethibande.process:processor:1.7")
}
```

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
And the other way around
```java
@POST
@Path("/api/v1/example-entity")
public void findById(final ExampleEntityDTO dto) {
    // 1. Loads the ExampleEntity by id
    // 2. Applies all fields
    // 2.1. Fetches ReferencedEntity by id
    // 2.1. Applies resulting entity to ExampleEntity
    // 3. Returns entity
    final ExampleEntity entity = dto.toEntity(/*existingEntityInstance, */this.entityManager);
    this.entityManager.persist(entity); // Persist updated entity
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
If we remove `expandProperties = {"entity"}` from the annotation the entity field will be deflated into a single `entityId` field like this:
```json
{
  "someString": "abc",
  "created": "2025-11-22T20:51",
  "updated": "2025-11-22T20:51",
  "entityId": 12,
  "id": 1
}
```
