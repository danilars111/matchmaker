package org.poolen.backend.db.interfaces;

import org.poolen.backend.db.entities.Setting;
import org.poolen.backend.db.jpa.entities.SettingEntity;

/**
 * A specific service interface for handling Settings, extending the generic IService.
 * The domain object is Setting<?>, allowing for generic setting types.
 */
public interface ISettingService extends IService<Setting<?>, SettingEntity> {
}
