package org.poolen.backend.engine;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.Literal;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ConstraintMatchmaker {
    private final List<Group> groups;
    private final List<Player> players;

    public ConstraintMatchmaker(List<Group> groups, Map<UUID, Player> attendingPlayers) {
        this.groups = groups;
        // Filter out DMs from the list of players to be matched
        this.players = attendingPlayers.values().stream()
                .filter(p -> !p.isDungeonMaster())
                .collect(Collectors.toList());
    }

    public List<Group> match() {
        if (players.isEmpty() || groups.isEmpty()) {
            return this.groups;
        }

        CpModel model = new CpModel();

        int numPlayers = players.size();
        int numGroups = groups.size();

        // --- 1. Create the variables ---
        // We now declare them as IntVar from the start for type safety!
        IntVar[][] x = new IntVar[numPlayers][numGroups];
        for (int i = 0; i < numPlayers; i++) {
            for (int j = 0; j < numGroups; j++) {
                x[i][j] = model.newBoolVar("x_" + i + "_" + j);
            }
        }

        // --- 2. Add the Constraints ---
        // For each player, the sum of their assignments must be exactly 1.
        for (int i = 0; i < numPlayers; i++) {
            model.addEquality(LinearExpr.sum(x[i]), 1);
        }

        int baseSize = numPlayers / numGroups;
        int remainder = numPlayers % numGroups;
        for (int j = 0; j < numGroups; j++) {
            int groupSize = baseSize + (j < remainder ? 1 : 0);
            List<IntVar> playersInGroup = new ArrayList<>();
            for (int i = 0; i < numPlayers; i++) {
                playersInGroup.add(x[i][j]);
            }
            model.addLessOrEqual(LinearExpr.sum(playersInGroup.toArray(new IntVar[0])), groupSize);
        }

        // --- 3. Define the Objective ---
        List<LinearExpr> objectiveTerms = new ArrayList<>();

        // -- Part A: House Preference Score --
        // This is now the only part of our objective!
        for (int i = 0; i < numPlayers; i++) {
            for (int j = 0; j < numGroups; j++) {
                int score = calculateScore(players.get(i), groups.get(j));
                objectiveTerms.add(LinearExpr.term(x[i][j], score));
            }
        }

        model.maximize(LinearExpr.sum(objectiveTerms.toArray(new LinearExpr[0])));

        // --- 4. Solve the model ---
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);

        // --- 5. Read the results ---
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            System.out.println("Total optimal score: " + solver.objectiveValue());
            for (int i = 0; i < numPlayers; i++) {
                for (int j = 0; j < numGroups; j++) {
                    // Added a cast here to keep the compiler happy
                    if (solver.booleanValue((Literal) x[i][j])) {
                        groups.get(j).addPartyMember(players.get(i));
                    }
                }
            }
        } else {
            System.out.println("No solution found.");
        }

        return groups;
    }

    private int calculateScore(Player player, Group group) {
        for (Character character : player.getCharacters()) {
            if (character != null && character.getHouse().equals(group.getHouse())) {
                return 10;
            }
        }
        return 1;
    }
}
