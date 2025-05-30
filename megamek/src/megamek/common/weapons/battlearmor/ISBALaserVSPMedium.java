/*
 * MegaMek - Copyright (C) 2004, 2005 Ben Mazur (bmazur@sev.org)
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
package megamek.common.weapons.battlearmor;

import megamek.common.alphaStrike.AlphaStrikeElement;
import megamek.common.weapons.lasers.VariableSpeedPulseLaserWeapon;

/**
 * @author Jason Tighe
 * @since Sep 12, 2004
 */
public class ISBALaserVSPMedium extends VariableSpeedPulseLaserWeapon {
    private static final long serialVersionUID = 2676144961105838316L;

    public ISBALaserVSPMedium() {
        super();
        name = "Medium VSP Laser";
        setInternalName("ISBAMediumVSPLaser");
        addLookupName("ISBAMVSPL");
        addLookupName("ISBAMediumVariableSpeedLaser");
        addLookupName("ISBAMediumVSP");
        sortingName = "Laser VSP C";
        heat = 7;
        damage = DAMAGE_VARIABLE;
        toHitModifier = -4;
        shortRange = 2;
        mediumRange = 5;
        longRange = 9;
        extremeRange = 13;
        waterShortRange = 1;
        waterMediumRange = 3;
        waterLongRange = 6;
        waterExtremeRange = 9;
        damageShort = 9;
        damageMedium = 7;
        damageLong = 5;
        tonnage = .9;
        criticals = 4;
        bv = 56;
        cost = 200000;
        shortAV = 7;
        maxRange = RANGE_SHORT;
        flags = flags.or(F_NO_FIRES).or(F_BA_WEAPON).andNot(F_MEK_WEAPON).andNot(F_TANK_WEAPON)
                .andNot(F_AERO_WEAPON).andNot(F_PROTO_WEAPON);
        // Tech Progression Missing in IO. Confirmed with Herb uses the same as the Mek Weapon.
        rulesRefs = "321, TO";
        techAdvancement.setTechBase(TechBase.IS)
                .setIntroLevel(false)
                .setUnofficial(false)
                .setTechRating(TechRating.E)
                .setAvailability(AvailabilityValue.X, AvailabilityValue.X, AvailabilityValue.E, AvailabilityValue.D)
                .setISAdvancement(3070, 3072, 3080, DATE_NONE, DATE_NONE)
                .setISApproximate(false, false, false, false, false)
                .setPrototypeFactions(Faction.FW, Faction.WB)
                .setProductionFactions(Faction.FW, Faction.WB);
    }

    @Override
    public double getBattleForceDamage(int range) {
        if (range == AlphaStrikeElement.SHORT_RANGE) {
            return 1.035;
        } else if (range == AlphaStrikeElement.MEDIUM_RANGE) {
            return 0.525;
        } else {
            return 0;
        }
    }

}
