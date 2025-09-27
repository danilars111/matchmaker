package org.poolen.backend.db.interfaces;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A generic service interface for handling business logic and translation
 * between domain objects and database entities.
 *
 * @param <T> The domain object type (e.g., Player)
 * @param <E> The JPA entity type (e.g., PlayerEntity)
 */
public interface IService<T, E> {

    /**
     * Saves a domain object to the database.
     * Handles both creation of new records and updates to existing ones.
     * @param domainObject The domain object to save.
     * @return The saved domain object, translated back from the database entity.
     */
    T save(T domainObject);

    /**
     * Finds a domain object by its public UUID.
     * @param uuid The UUID of the object to find.
     * @return An Optional containing the domain object if found, otherwise empty.
     */
    Optional<T> findByUuid(UUID uuid);

    /**
     * Finds all domain objects.
     * @return A Set containing all domain objects.
     */
    Set<T> findAll();

    /**
     * Converts a database entity into a domain object.
     * @param entity The entity to convert.
     * @return The corresponding domain object.
     */
    T toDomainObject(E entity);

    /**
     * Updates an existing database entity with data from a domain object.
     * @param entity The entity from the database to update.
     * @param domainObject The domain object with the new data.
     */
    void updateEntity(E entity, T domainObject);
}
