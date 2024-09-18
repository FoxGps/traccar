/*
 * Copyright 2016 - 2024 Anton Tananaev (anton@traccar.org)
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
package org.traccar.handler.events;

import jakarta.inject.Inject;
import io.netty.channel.ChannelHandler; //Roger
import org.slf4j.Logger; //Roger
import org.slf4j.LoggerFactory; //Roger
import org.traccar.database.CommandsManager; //Roger
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.Calendar;
import org.traccar.model.Command; //Roger
import org.traccar.model.Event;
import org.traccar.model.Geofence;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;

import jakarta.inject.Singleton; //Roger
import jakarta.ws.rs.core.Response; //Roger
import java.util.ArrayList;
import java.util.List;

@Singleton //Roger
@ChannelHandler.Sharable //Roger
public class GeofenceEventHandler extends BaseEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeofenceEventHandler.class); //Roger
    private final CacheManager cacheManager;
    private final CommandsManager commandsManager; //Roger

    @Inject
    public GeofenceEventHandler(CacheManager cacheManager, CommandsManager commandsManager) { //Roger
        this.cacheManager = cacheManager;
        this.commandsManager = commandsManager; //Roger
    }

    @Override
    public void analyzePosition(Position position, Callback callback) {
        if (!PositionUtil.isLatest(cacheManager, position)) {
            return;
        }

        List<Long> oldGeofences = new ArrayList<>();
        Position lastPosition = cacheManager.getPosition(position.getDeviceId());
        if (lastPosition != null && lastPosition.getGeofenceIds() != null) {
            oldGeofences.addAll(lastPosition.getGeofenceIds());
        }

        List<Long> newGeofences = new ArrayList<>();
        if (position.getGeofenceIds() != null) {
            newGeofences.addAll(position.getGeofenceIds());
            newGeofences.removeAll(oldGeofences);
            oldGeofences.removeAll(position.getGeofenceIds());
        }

        for (long geofenceId : oldGeofences) {
            Geofence geofence = cacheManager.getObject(Geofence.class, geofenceId);
            if (geofence != null) {
                long calendarId = geofence.getCalendarId();
                Calendar calendar = calendarId != 0 ? cacheManager.getObject(Calendar.class, calendarId) : null;
                if (calendar == null || calendar.checkMoment(position.getFixTime())) {
                    Event event = new Event(Event.TYPE_GEOFENCE_EXIT, position);
                    if (geofence.getStopOut()) {
                        sendEngineStopCommand(position, geofenceId, "SAIU");
                    }
                    event.setGeofenceId(geofenceId);
                    callback.eventDetected(event);
                }
            }
        }
        for (long geofenceId : newGeofences) {
            Geofence geofence = cacheManager.getObject(Geofence.class, geofenceId);
            if (geofence != null) {
                long calendarId = geofence.getCalendarId();
                Calendar calendar = calendarId != 0 ? cacheManager.getObject(Calendar.class, calendarId) : null;
                if (calendar == null || calendar.checkMoment(position.getFixTime())) {
                    Event event = new Event(Event.TYPE_GEOFENCE_ENTER, position);
                    if (geofence.getStopIn()) {
                        sendEngineStopCommand(position, geofenceId, "ENTROU");
                    }
                    event.setGeofenceId(geofenceId);
                    callback.eventDetected(event);
                }
            }
        }
    }

    private void sendEngineStopCommand(Position position, long geofenceId, String action) {
        Command command = new Command();
        command.setDeviceId(position.getDeviceId());
        command.setType(Command.TYPE_ENGINE_STOP);

        try {
            if (commandsManager.sendCommand(command) == null) {
                LOGGER.info("FoxGPS - BLOQUEIO {} DA CERCA {}: {}",
                    action, geofenceId, Response.accepted(command).build());
            }
        } catch (Exception e) {
            LOGGER.warn("FoxGPS - BLOQUEIO {} DA CERCA {}: Falha ao enviar comando para o dispositivo {}",
                action, geofenceId, position.getDeviceId(), e);
        }
    }
}
