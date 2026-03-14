/*
 * Copyright 2024 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.Device;
import org.traccar.model.Position;
import org.traccar.redis.RedisConnectionManager;
import org.traccar.session.cache.CacheManager;

import jakarta.inject.Inject;

public class DeviceConnectionHandler extends BasePositionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceConnectionHandler.class);

    private final boolean enabled;
    private final RedisConnectionManager redisConnectionManager;
    private final CacheManager cacheManager;

    @Inject
    public DeviceConnectionHandler(Config config, RedisConnectionManager redisConnectionManager, CacheManager cacheManager) {
        this.enabled = config.getBoolean(Keys.REDIS_ENABLE);
        this.redisConnectionManager = redisConnectionManager;
        this.cacheManager = cacheManager;
    }

    @Override
    public void onPosition(Position position, Callback callback) {
        if (enabled && redisConnectionManager.isEnabled()) {
            try {
                Device device = cacheManager.getObject(Device.class, position.getDeviceId());
                if (device != null) {
                    // Marcar dispositivo como conectado cuando envía posición
                    redisConnectionManager.addDevice(device);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to handle device connection for position", e);
            }
        }

        callback.processed(false);
    }
}