/*
 * MegaMek - Copyright (C) 2000-2004 Ben Mazur (bmazur@sev.org)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */
package megamek.common.actions;

import megamek.common.*;
import megamek.common.options.OptionsConstants;

/**
 * The attacker kicks the target.
 */
public class TripAttackAction extends PhysicalAttackAction {
    private static final long serialVersionUID = -8639566786588420601L;

    public TripAttackAction(int entityId, int targetId) {
        super(entityId, targetId);
    }

    public TripAttackAction(int entityId, int targetType, int targetId) {
        super(entityId, targetType, targetId);
    }

    public ToHitData toHit(Game game) {
        return TripAttackAction.toHit(game, getEntityId(), game.getTarget(getTargetType(), getTargetId()));
    }

    /**
     * To-hit number for the specified leg to kick
     */
    public static ToHitData toHit(Game game, int attackerId, Targetable target) {
        final Entity ae = game.getEntity(attackerId);
        if (ae == null) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "You can't attack from a null entity!");
        }

        if (!game.getOptions().booleanOption(OptionsConstants.ADVCOMBAT_TACOPS_TRIP_ATTACK)) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "no Trip attack");
        }

        String impossible = PhysicalAttackAction.toHitIsImpossible(game, ae, target);
        if (impossible != null) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "impossible");
        }

        if (ae.getGrappled() != Entity.NONE) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "impossible");
        }

        if (!game.getOptions().booleanOption(OptionsConstants.BASE_FRIENDLY_FIRE)) {
            // a friendly unit can never be the target of a direct attack.
            if ((target.getTargetType() == Targetable.TYPE_ENTITY)
                    && ((((Entity) target).getOwnerId() == ae.getOwnerId())
                            || ((((Entity) target).getOwner().getTeam() != Player.TEAM_NONE)
                                    && (ae.getOwner().getTeam() != Player.TEAM_NONE)
                                    && (ae.getOwner().getTeam() == ((Entity) target).getOwner().getTeam())))) {
                return new ToHitData(TargetRoll.IMPOSSIBLE,
                        "A friendly unit can never be the target of a direct attack.");
            }
        }

        ToHitData toHit;

        // non-meks can't trip or be tripped
        if (!(ae instanceof Mek) || !(target instanceof Mek)) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "Only meks can trip other meks");
        }

        // LAM AirMeks can only trip when grounded.
        if (ae.isAirborneVTOLorWIGE()) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "Cannot trip while airborne");
        }

        // described as a leg hook
        // needs 2 legs present
        if (ae.isLocationBad(Mek.LOC_LLEG) || ae.isLocationBad(Mek.LOC_RLEG)) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "Leg missing");
        }

        // check range
        final int range = ae.getPosition().distance(target.getPosition());
        if (range > 1) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "Target out of range");
        }

        int limb1 = Entity.LOC_NONE;

        // check facing
        if (!ComputeArc.isInArc(ae.getPosition(), ae.getFacing(), target, Compute.ARC_FORWARD)) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "Target not in arc");
        }

        // can't trip while prone
        if (ae.isProne()) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "Attacker is prone");
        }
        if (((Entity) target).isProne()) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "Target is prone");
        }

        Hex attHex = game.getBoard().getHex(ae.getPosition());
        Hex targHex = game.getBoard().getHex(target.getPosition());
        final int attackerElevation = ae.getElevation() + attHex.getLevel();
        final int targetElevation = target.getElevation() + targHex.getLevel();

        if (attackerElevation != targetElevation) {
            return new ToHitData(TargetRoll.IMPOSSIBLE, "Attacker and Target must be at the same elevation");
        }

        // check if attacker has fired leg-mounted weapons
        boolean[] usedWeapons = new boolean[ae.locations()];
        for (int i = 0; i < ae.locations(); i++) {
            usedWeapons[i] = false;
        }

        for (Mounted<?> mounted : ae.getWeaponList()) {
            if (mounted.isUsedThisRound()) {
                int loc = mounted.getLocation();
                if (loc != Entity.LOC_NONE) {
                    usedWeapons[loc] = true;
                }
            }
        }

        // check for good hips / shoulders
        if (!ae.hasWorkingSystem(Mek.ACTUATOR_HIP, Mek.LOC_RLEG)) {
            usedWeapons[Mek.LOC_RLEG] = true;
        }
        if (!ae.hasWorkingSystem(Mek.ACTUATOR_HIP, Mek.LOC_LLEG)) {
            usedWeapons[Mek.LOC_LLEG] = true;
        }
        if (!ae.hasWorkingSystem(Mek.ACTUATOR_HIP, Mek.LOC_RARM)) {
            usedWeapons[Mek.LOC_RARM] = true;
        }
        if (!ae.hasWorkingSystem(Mek.ACTUATOR_HIP, Mek.LOC_LARM)) {
            usedWeapons[Mek.LOC_LARM] = true;
        }

        if (ae instanceof QuadMek) {
            if (usedWeapons[Mek.LOC_RARM]) {
                if (usedWeapons[Mek.LOC_LARM]) {
                    return new ToHitData(TargetRoll.IMPOSSIBLE, "both legs unusable");
                }
                limb1 = Mek.LOC_LARM;
            }
        } else if (usedWeapons[Mek.LOC_RLEG]) { // normal attack uses both legs
            if (usedWeapons[Mek.LOC_LLEG]) {
                return new ToHitData(TargetRoll.IMPOSSIBLE, "both legs unusable");
            }
            limb1 = Mek.LOC_LLEG;
        }

        // Set the base BTH
        int base = ae.getCrew().getPiloting();

        // Start the To-Hit
        toHit = new ToHitData(base, "base");
        toHit.addModifier(-1, "Trip");

        PhysicalAttackAction.setCommonModifiers(toHit, game, ae, target);

        // Get best leg
        if (ae instanceof QuadMek) {
            if (limb1 == Entity.LOC_NONE) {
                ToHitData left = TripAttackAction.getLimbModifier(Mek.LOC_LARM, ae);
                ToHitData right = TripAttackAction.getLimbModifier(Mek.LOC_RARM, ae);
                if (left.getValue() < right.getValue()) {
                    toHit.append(left);
                } else {
                    toHit.append(right);
                }
            } else {
                toHit.append(TripAttackAction.getLimbModifier(limb1, ae));
            }
        } else if (limb1 == Entity.LOC_NONE) {
            ToHitData left = TripAttackAction.getLimbModifier(Mek.LOC_LLEG, ae);
            ToHitData right = TripAttackAction.getLimbModifier(Mek.LOC_RLEG, ae);
            if (left.getValue() < right.getValue()) {
                toHit.append(left);
            } else {
                toHit.append(right);
            }
        } else {
            toHit.append(TripAttackAction.getLimbModifier(limb1, ae));
        }

        if (ae.hasFunctionalLegAES()) {
            toHit.addModifier(-1, "AES modifier");
        }

        // done!
        return toHit;
    }

    private static ToHitData getLimbModifier(int loc, Entity ae) {
        ToHitData toHit = new ToHitData();
        // damaged or missing actuators
        if (!ae.hasWorkingSystem(Mek.ACTUATOR_UPPER_LEG, loc)) {
            toHit.addModifier(2, "Upper leg actuator destroyed");
        }

        if (!ae.hasWorkingSystem(Mek.ACTUATOR_LOWER_LEG, loc)) {
            toHit.addModifier(2, "Lower leg actuator destroyed");
        }

        if (!ae.hasWorkingSystem(Mek.ACTUATOR_FOOT, loc)) {
            toHit.addModifier(1, "Foot actuator destroyed");
        }
        return toHit;
    }
}
