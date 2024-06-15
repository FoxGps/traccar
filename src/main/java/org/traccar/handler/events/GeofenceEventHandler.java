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
import io.netty.channel.ChannelHandler; // Rogerio
import org.slf4j.Logger;  // Rogerio
import org.slf4j.LoggerFactory;  // Rogerio
import org.traccar.database.CommandsManager;  // Rogerio
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.Calendar;
import org.traccar.model.Command; // Rogerio
import org.traccar.model.Event;
import org.traccar.model.Geofence;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;

import jakarta.inject.Singleton; // Rogerio
import jakarta.ws.rs.core.Response; // Rogerio
import java.util.ArrayList;
import java.util.List;

@Singleton // Rogerio
@ChannelHandler.Sharable // Rogerio
public class GeofenceEventHandler extends BaseEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeofenceEventHandler.class); // Rogerio
    private final CacheManager cacheManager;
    private final CommandsManager commandsManager; // Rogerio

    @Inject
    public GeofenceEventHandler(CacheManager cacheManager, CommandsManager commandsManager) { // Rogerio
        this.cacheManager = cacheManager;
        this.commandsManager = commandsManager; // Rogerio
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
 // Rogerio Inicio
                    if (geofence.getStopOut()) {
                        Command command = new Command();
                        command.setDeviceId(position.getDeviceId());
                        command.setType(Command.TYPE_ENGINE_STOP);

                        try {
                            if (commandsManager.sendCommand(command) == null) {
                                LOGGER.info("FoxGPS - BLOQUEIO SAIU DA CERCA:" + Response.accepted(command).build());
                            }
                        } catch (Exception e) {
                            LOGGER.warn("FoxGPS - BLOQUEIO SAIU DA CERCA:" + e.getMessage());
                        }
                        event.setGeofenceId(geofenceId);
                    }
 // Rogerio FIM
                    event.setGeofenceId(geofenceId);
                    callback.eventDetected(event);
                }
            }
        }
        for (long geofenceId : newGeofences) {
            long calendarId = cacheManager.getObject(Geofence.class, geofenceId).getCalendarId();
            Calendar calendar = calendarId != 0 ? cacheManager.getObject(Calendar.class, calendarId) : null;
            if (calendar == null || calendar.checkMoment(position.getFixTime())) {
                Event event = new Event(Event.TYPE_GEOFENCE_ENTER, position);
 // Rogerio Inicio
                if (cacheManager.getObject(Geofence.class, geofenceId).getStopIn()) {
                    Command command = new Command();
                    command.setDeviceId(position.getDeviceId());
                    command.setType(Command.TYPE_ENGINE_STOP);

                    try {
                        if (commandsManager.sendCommand(command) == null) {
                            LOGGER.info("FoxGPS - BLOQUEIO ENTROU DA CERCA:" + Response.accepted(command).build());
                        }
                    } catch (Exception e) {
                        LOGGER.warn("FoxGPS - BLOQUEIO ENTROU DA CERCA:" + e.getMessage());
                    }
                    event.setGeofenceId(geofenceId);
                }
 // Rogerio FIM
                event.setGeofenceId(geofenceId);
                callback.eventDetected(event);
            }
        }
    }
}
