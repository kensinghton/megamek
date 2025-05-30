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
package megamek.common.weapons.autocannons;

import megamek.common.SimpleTechLevel;

/**
 * @author Jason Tighe
 * @since Oct 2, 2004
 */
public class CLProtoMekAC8 extends ProtoMekACWeapon {
    private static final long serialVersionUID = 4371171653960292873L;

    public CLProtoMekAC8() {
        super();

        // CHECKSTYLE IGNORE ForbiddenWords FOR 3 LINES
        name = "ProtoMech AC/8";
        setInternalName("CLProtoMechAC8");
        addLookupName("Clan ProtoMech AC/8");
        heat = 2;
        damage = 8;
        rackSize = 8;
        minimumRange = 0;
        shortRange = 3;
        mediumRange = 7;
        longRange = 10;
        extremeRange = 15;
        tonnage = 5.5;
        criticals = 4;
        bv = 66;
        cost = 175000;
        shortAV = 8;
        medAV = 8;
        longAV = 8;
        maxRange = RANGE_SHORT;
        explosionDamage = damage;
        rulesRefs = "286, TO";
        //Tech Progression tweaked to combine IntOps with TRO Prototypes/3145 NTNU RS
        techAdvancement.setTechBase(TechBase.CLAN)
                .setTechRating(TechRating.F).setAvailability(AvailabilityValue.X, AvailabilityValue.X, AvailabilityValue.F, AvailabilityValue.E)
                .setClanAdvancement(DATE_NONE, 3070, 3073, DATE_NONE,DATE_NONE)
                .setClanApproximate(false, true, false, false, false)
                .setPrototypeFactions(Faction.CBS).setProductionFactions(Faction.CBS)
                .setStaticTechLevel(SimpleTechLevel.STANDARD);
    }
}
