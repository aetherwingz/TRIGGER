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

import net.minestom.server.entity.Player;

/**
 * A record that contains the callback data.
 * @param player The player that triggered the trigger.
 * @param trigger The trigger that was triggered.
 * @param type The {@link Type} of the callback.
 */
public record TriggeredCallback(Player player, Trigger trigger, Type type) {
    /**
     * Represents what happened with a trigger. Names should be self-explanatory.
     */
    public enum Type {
        ENTERED,
        EXITED,
        TICK
    }
}
