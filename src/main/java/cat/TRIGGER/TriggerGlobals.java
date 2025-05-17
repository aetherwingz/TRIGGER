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

/**
 * A collection of global objects that are used across the system.
 */
public final class TriggerGlobals {
    public static TriggerTypeAdapter triggerTypeAdapter = new TriggerTypeAdapter();
    public static Gson GSON = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Trigger.class, triggerTypeAdapter).create();
}
