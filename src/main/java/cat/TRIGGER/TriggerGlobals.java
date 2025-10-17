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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.Vec;

/**
 * A collection of global objects that are used across the system.
 */
public final class TriggerGlobals {
    public static TriggerTypeAdapter triggerTypeAdapter = new TriggerTypeAdapter();
    public static Gson GSON = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Trigger.class, triggerTypeAdapter).create();

    /**
     * A note about RGB_CODEC, since it is using an interface it needs to
     * boiled down to one of the implementations, I have chosen TextColor
     * arbitrarily, but DyeColor, or Color would work the same.
     **/
    private static final Codec<RGBLike> RGB_CODEC = StructCodec.struct(
            "red", Codec.INT, RGBLike::red,
            "green", Codec.INT, RGBLike::green,
            "blue", Codec.INT, RGBLike::blue,
            TextColor::color
    );

    private static final Codec<Vec> VEC_CODEC = StructCodec.struct(
            "x", Codec.DOUBLE, Vec::x,
            "y", Codec.DOUBLE, Vec::y,
            "z", Codec.DOUBLE, Vec::z,
            Vec::new
    );

    public static Codec<Trigger> TRIGGER_CODEC = StructCodec.struct(
            "anchors", VEC_CODEC.list(), Trigger::getAnchors,
            "position", VEC_CODEC, Trigger::getPosition,
            "uuid", Codec.UUID, Trigger::getUuid,
            "name", Codec.COMPONENT, Trigger::getName,
            "color", RGB_CODEC, Trigger::getColor,
            "callback", null, null,
            Trigger::new
    );
}
