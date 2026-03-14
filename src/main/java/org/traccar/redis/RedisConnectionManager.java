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
package org.traccar.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.Device;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Date;
import java.util.Set;

@Singleton
public class RedisConnectionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisConnectionManager.class);

    private final String redisUrl;
    private final boolean enabled;

    @Inject
    public RedisConnectionManager(Config config) {
        this.enabled = config.getBoolean(Keys.REDIS_ENABLE);
        this.redisUrl = config.getString(Keys.REDIS_URL);
        
        if (enabled && redisUrl != null) {
            clearAllConnected();
        }
    }

    /**
     * Limpia todas las conexiones al iniciar el servidor.
     * Previene estados inconsistentes entre reinicios.
     */
    private void clearAllConnected() {
        if (!enabled || redisUrl == null) {
            return;
        }

        try (Jedis jedis = new Jedis(redisUrl)) {
            Set<String> keys = jedis.keys("connected.*");
            if (!keys.isEmpty()) {
                LOGGER.info("Clearing {} connected device entries from Redis", keys.size());
                for (String key : keys) {
                    jedis.del(key);
                }
            }
        } catch (JedisException e) {
            LOGGER.warn("Failed to clear connected devices from Redis", e);
        }
    }

    /**
     * Marca un dispositivo como conectado en Redis.
     * Usa SETNX para no sobrescribir el timestamp original de conexión.
     */
    public void addDevice(Device device) {
        if (!enabled || redisUrl == null || device == null) {
            return;
        }

        try (Jedis jedis = new Jedis(redisUrl)) {
            String key = "connected." + device.getUniqueId();
            String timestamp = String.valueOf(new Date().getTime());
            
            // SETNX solo establece el valor si la clave no existe
            Long result = jedis.setnx(key, timestamp);
            
            if (result == 1) {
                LOGGER.debug("Device {} marked as connected in Redis", device.getUniqueId());
            }
        } catch (JedisException e) {
            LOGGER.warn("Failed to add device {} to Redis", device.getUniqueId(), e);
        }
    }

    /**
     * Marca un dispositivo como desconectado removiendo su entrada de Redis.
     */
    public void removeDevice(Device device) {
        if (!enabled || redisUrl == null || device == null) {
            return;
        }

        try (Jedis jedis = new Jedis(redisUrl)) {
            String key = "connected." + device.getUniqueId();
            Long result = jedis.del(key);
            
            if (result > 0) {
                LOGGER.debug("Device {} removed from Redis connections", device.getUniqueId());
            }
        } catch (JedisException e) {
            LOGGER.warn("Failed to remove device {} from Redis", device.getUniqueId(), e);
        }
    }

    /**
     * Verifica si un dispositivo está conectado según Redis.
     */
    public boolean isDeviceConnected(String uniqueId) {
        if (!enabled || redisUrl == null || uniqueId == null) {
            return false;
        }

        try (Jedis jedis = new Jedis(redisUrl)) {
            return jedis.exists("connected." + uniqueId);
        } catch (JedisException e) {
            LOGGER.warn("Failed to check device {} connection status in Redis", uniqueId, e);
            return false;
        }
    }

    /**
     * Obtiene el timestamp de conexión de un dispositivo.
     */
    public Long getConnectionTimestamp(String uniqueId) {
        if (!enabled || redisUrl == null || uniqueId == null) {
            return null;
        }

        try (Jedis jedis = new Jedis(redisUrl)) {
            String timestamp = jedis.get("connected." + uniqueId);
            return timestamp != null ? Long.valueOf(timestamp) : null;
        } catch (Exception e) {
            LOGGER.warn("Failed to get connection timestamp for device {}", uniqueId, e);
            return null;
        }
    }

    /**
     * Obtiene todos los dispositivos conectados.
     */
    public Set<String> getAllConnectedDevices() {
        if (!enabled || redisUrl == null) {
            return Set.of();
        }

        try (Jedis jedis = new Jedis(redisUrl)) {
            Set<String> keys = jedis.keys("connected.*");
            return keys.stream()
                    .map(key -> key.substring("connected.".length()))
                    .collect(java.util.stream.Collectors.toSet());
        } catch (JedisException e) {
            LOGGER.warn("Failed to get connected devices from Redis", e);
            return Set.of();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}