/*
package org.poolen.backend.engine;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;

import java.util.List;
import java.util.Map;

public class GroupSwapper {
    int BLACKLIST_BONUS = -5;
    int BUDDY_BONUS = 1;
    List<Group> groups;
    Map<Player, Group> preferredGroups;
    public GroupSwapper(List<Group> groups) {
        this.groups = groups;
        for(Group group : groups) {
            for(Player player : group.getParty().values()) {
                preferredGroups.put(player, group);
            }
        }
    }

    public void run() {
        int score = 0;
        for(Group group : groups) {
            int newScore = 0;
            for(Player player : group.getParty().values()) {
                    newScore += calculateScore(player, group, group.getHouse());
                if(newScore > score) {
                    preferredGroups.put(player, group);
                }
            }
        }
    }

    private int calculateScore(Player player, Group group, House house) {
        int score = 0;
        for(Player otherPlayer : group.getParty().values()) {
            // Don't compare to ourselves and keep players to the same house as we assume the first
            // pass put them in the "best house"
            if(player.getUuid().equals(otherPlayer.getUuid()) || !group.getHouse().equals(house)) {
                continue;
            }
            if(player.getBlacklist().containsKey(otherPlayer.getUuid()) ||
            player.getDmBlacklist().containsKey(group.getDungeonMaster().getUuid())) {
                score += BLACKLIST_BONUS;
            }
            if(player.getBuddylist().containsKey(otherPlayer.getUuid())) {
                score += BUDDY_BONUS;
            }
            if(player.getPlayerLog().containsKey(otherPlayer)) {
                score -= player.getPlayerLog().get(otherPlayer.getUuid());
            }
            return score;

        }
        return 0;
    }


}
*/
