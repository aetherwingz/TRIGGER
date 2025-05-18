/*
 *     This file is part of TRIGGER by @catkillsreality.
 *
 *     TRIGGER is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *     TRIGGER is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along with TRIGGER. If not, see <https://www.gnu.org/licenses/>.
 */

package cat.TRIGGER;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A {@link TypeAdapter} for {@link Trigger} used for JSON serialization and deserialization.
 */
public class TriggerTypeAdapter extends TypeAdapter<Trigger> {


    private static final Logger LOGGER = LoggerFactory.getLogger(TriggerTypeAdapter.class);

    @Override
    public void write(JsonWriter out, Trigger trigger) throws IOException {
        out.beginObject();

        out.name("name");
        out.jsonValue(JSONComponentSerializer.json().serializeOr(trigger.getName(), "NameSerializerError"));

        out.name("position");
        out.beginObject();
        out.name("x").value(trigger.getPosition().x());
        out.name("y").value(trigger.getPosition().y());
        out.name("z").value(trigger.getPosition().z());
        out.endObject();

        out.name("anchors");
        out.beginArray();
        trigger.getAnchors().forEach(anchor -> {
            try {
                out.beginObject();
                out.name("x").value(anchor.x());
                out.name("y").value(anchor.y());
                out.name("z").value(anchor.z());
                out.endObject();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        out.endArray();
        out.endObject();
    }

    @Override
    public Trigger read(JsonReader in) throws IOException {
        List<Vec> anchors = new ArrayList<>();
        Vec position = Vec.ZERO;
        Component name = Component.text("unnamed");

        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "name" -> {
                    String json = in.nextString();
                    name = JSONComponentSerializer.json().deserializeOr(json, Component.text("DeserializerError"));
                }
                case "position" -> {
                    in.beginObject();
                    double x = 0, y = 0, z = 0;
                    while (in.hasNext()) {
                        switch (in.nextName()) {
                            case "x" -> x = in.nextDouble();
                            case "y" -> y = in.nextDouble();
                            case "z" -> z = in.nextDouble();
                        }
                    }
                    in.endObject();
                    position = new Vec(x, y, z);
                }
                case "anchors" -> {
                    in.beginArray();
                    while (in.hasNext()) {
                        in.beginObject();
                        double ax = 0, ay = 0, az = 0;
                        while (in.hasNext()) {
                            switch (in.nextName()) {
                                case "x" -> ax = in.nextDouble();
                                case "y" -> ay = in.nextDouble();
                                case "z" -> az = in.nextDouble();
                            }
                        }
                        in.endObject();
                        anchors.add(new Vec(ax, ay, az));
                    }
                    in.endArray();
                }
                default -> in.skipValue();
            }
        }
        in.endObject();
        return new Trigger(anchors, position, UUID.randomUUID(), name, NamedTextColor.RED, null);
    }
}
