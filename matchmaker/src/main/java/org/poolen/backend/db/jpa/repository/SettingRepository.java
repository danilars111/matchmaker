package org.poolen.backend.db.jpa.repository;

import org.poolen.backend.db.jpa.entities.SettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface SettingRepository extends JpaRepository<SettingEntity, Long> {

    /**
     * Finds a setting by its unique name.
     * @param name The name of the setting.
     * @return The SettingEntity, or null if not found.
     */
    SettingEntity findByName(String name);

    /**
     * Finds all settings whose names are in the given set.
     * @param names A set of setting names.
     * @return A list of matching SettingEntity objects.
     */
    List<SettingEntity> findAllByNameIn(Set<String> names);
}
