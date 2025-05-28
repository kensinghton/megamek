/*
 * Copyright (c) 2025 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMek.
 *
 * MegaMek is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MegaMek is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MegaMek. If not, see <http://www.gnu.org/licenses/>.
 */
package megamek.common.weapons;

import megamek.common.*;
import megamek.common.actions.ArtilleryAttackAction;
import megamek.common.actions.WeaponAttackAction;
import megamek.common.enums.GamePhase;
import megamek.logging.MMLogger;
import megamek.server.totalwarfare.TWGameManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Vector;

import static megamek.common.weapons.ArtilleryHandlerHelper.*;

public class CapitalLaserBayOrbitalBombardmentHandler extends BayWeaponHandler {

    private static final MMLogger LOGGER = MMLogger.create(CapitalLaserBayOrbitalBombardmentHandler.class);

    private boolean isReported = false;
    private final ArtilleryAttackAction attackAction;

    public CapitalLaserBayOrbitalBombardmentHandler(ToHitData t, WeaponAttackAction w, Game g, TWGameManager m) {
        super(t, w, g, m);
        attackAction = (ArtilleryAttackAction) w;
    }

    @Override
    public boolean cares(GamePhase phase) {
        return phase.isOffboard() || phase.isTargeting();
    }

    @Override
    public boolean handle(GamePhase phase, Vector<Report> reports) {
        if (ae == null || target == null || wtype == null || !game.hasBoardLocationOf(target)) {
            LOGGER.error("Attack info incomplete!");
            return false;
        }

        // This attack has just been declared; report it once
        if (!isReported) {
            addHeat();
            reportFiring(reports, attackAction);
            isReported = true;
        }

        if (phase.isTargeting()) {
            // I have no clue how/why this is used:
            setAnnouncedEntityFiring(false);
            return true;
        }

        // If at least one valid spotter, then get the benefits thereof.
        Optional<Entity> spotter = findSpotter(attackAction.getSpotterIds(), attackAction.getPlayerId(), game,
              target);

        if (spotter.isPresent()) {
            int modifier = (spotter.get().getCrew().getGunnery() - 4) / 2;
            modifier += isForwardObserver(spotter.get()) ? -1 : 0;
            toHit.addModifier(modifier, "Spotting modifier");
        }

        // do we hit?
        bMissed = roll.getIntValue() < toHit.getValue();
        // Set Margin of Success/Failure.
        toHit.setMoS(roll.getIntValue() - Math.max(2, toHit.getValue()));

        // If the shot hit the target hex, then all subsequent fire will hit the hex automatically.
        if (!bMissed) {
            ae.aTracker.setModifier(TargetRoll.AUTOMATIC_SUCCESS, target.getPosition());
        } else if (spotter.isPresent()) {
            // If the shot missed, but was adjusted by a spotter, future shots are more likely to hit.
            // only add mods if it's not an automatic success
            int currentModifier = ae.aTracker.getModifier(weapon, target.getPosition());
            if (currentModifier != TargetRoll.AUTOMATIC_SUCCESS) {
                if (isForwardObserver(spotter.get())) {
                    ae.aTracker.setSpotterHasForwardObs(true);
                }
                ae.aTracker.setModifier(currentModifier - 1, target.getPosition());
            }
        }

        // Report weapon attack and its to-hit value.
        Report report = new Report(3120).indent().noNL().subject(subjectId).add(wtype.getName());
        report.add(target.getDisplayName(), true);
        reports.addElement(report);

        if (toHit.getValue() == TargetRoll.IMPOSSIBLE) {
            reports.addElement(new Report(3135).subject(subjectId).add(toHit.getDesc()));
            return false;
        } else if (toHit.getValue() == TargetRoll.AUTOMATIC_FAIL) {
            reports.addElement(new Report(3140).noNL().subject(subjectId).add(toHit.getDesc()));
        } else if (toHit.getValue() == TargetRoll.AUTOMATIC_SUCCESS) {
            reports.addElement(new Report(3145).noNL().subject(subjectId).add(toHit.getDesc()));
        } else {
            // roll to hit
            reports.addElement(new Report(3150).noNL().subject(subjectId).add(toHit));
        }

        // dice have been rolled, thanks
        reports.addElement(new Report(3155).noNL().subject(subjectId).add(roll));

        // In the case of misses, we'll need to hit multiple hexes
        List<Coords> actualHits = new ArrayList<>();
        Board board = game.getBoard(target);

        if (!bMissed) {
            report = new Report(3203).subject(subjectId).add(nweaponsHit).add(target.getPosition().getBoardNum());
            reports.addElement(report);
            String message = "Orbital Bombardment by %s hit here on round %d (this hex is now an auto-hit)"
                  .formatted(owner().getName(), game.getRoundCount());

            board.addSpecialHexDisplay(target.getPosition(),
                  new SpecialHexDisplay(SpecialHexDisplay.Type.ARTILLERY_HIT, game.getRoundCount(), owner(), message));
            for (int i = 0; i < nweaponsHit; i++) {
                actualHits.add(target.getPosition());
            }
        } else {
            // We're only going to display one missed shot hex on the board, at the intended target
            // Any drifted shots will be indicated at their end points
            String message = "Orbital Bombardment by %s missed here on round %d"
                  .formatted(owner().getName(), game.getRoundCount());
            board.addSpecialHexDisplay(target.getPosition(),
                  SpecialHexDisplay.createArtyMiss(owner(), game.getRoundCount(), message));

            while (nweaponsHit > 0) {
                // Scatter individual weapons (not sure where this is applicable)
                // Scatter distance, see SO:AA, p.91; I decided to have Oblique Artilleryman (CO, p.78) not apply to
                // Capital Laser Weapons as they are not "artillery pieces"
                int scatterDistance = 2 * Math.abs(toHit.getMoS());
                Coords scatteredPosition = Compute.scatterDirectArty(target.getPosition(), scatterDistance);
                if (board.contains(scatteredPosition)) {
                    actualHits.add(scatteredPosition);
                    // misses and scatters to another hex
                    reports.addElement(new Report(3202).subject(subjectId).add("One").add(scatteredPosition.getBoardNum()));
                } else {
                    // misses and scatters off-board
                    reports.addElement(new Report(3200).subject(subjectId));
                }
                nweaponsHit--;
            }

            // If we managed to land everything off the board, this handler is finished for good
            if (actualHits.isEmpty()) {
                return false;
            }
        }

        AreaEffectHelper.DamageFalloff falloff = new AreaEffectHelper.DamageFalloff();
        falloff.damage = calcAttackValue() * 10;
        falloff.falloff = calcAttackValue() * 2;
        falloff.radius = 4;
        falloff.clusterMunitionsFlag = false;

        for (Coords actualHit : actualHits) {
            clearMines(reports, actualHit, game, ae, gameManager);

            Vector<Integer> alreadyHit = new Vector<>();
            var blastShape = AreaEffectHelper.shapeBlast(null, actualHit, falloff, board.getHex(actualHit).getLevel(),
                  false, false, false, game, false);

            for (var entry : blastShape.keySet()) {
                alreadyHit = gameManager.artilleryDamageHex(entry.getValue(), board.getBoardId(), actualHit,
                      blastShape.get(entry), null, subjectId, ae, null, false, entry.getKey(),
                      board.getHex(actualHit).getLevel(), reports,
                      false, alreadyHit, false, falloff);
            }
        }
        return false;
    }

    private Player owner() {
        return game.getPlayer(attackAction.getPlayerId());
    }

    private void reportFiring(Vector<Report> reports, ArtilleryAttackAction aaa) {
        Report report = new Report(3121).indent().noNL().subject(subjectId);
        report.add(wtype.getName());
        report.add(aaa.getTurnsTilHit());
        reports.addElement(report);
        Report.addNewline(reports);

        Player owner = game.getPlayer(aaa.getPlayerId());
        int landingRound = game.getRoundCount() + aaa.getTurnsTilHit();
        String message = "Orbital Bombardment incoming, landing this round, fired by " + owner.getName();
        SpecialHexDisplay incomingMarker = SpecialHexDisplay.createIncomingFire(owner, landingRound, message);
        game.getBoard(target).addSpecialHexDisplay(target.getPosition(), incomingMarker);
    }
}
