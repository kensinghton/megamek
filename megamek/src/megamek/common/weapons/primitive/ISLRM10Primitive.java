/*
 * MegaMek -
 * Copyright (C) 2000-2007 Ben Mazur (bmazur@sev.org)
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
package megamek.common.weapons.primitive;

import megamek.common.AmmoType;
import megamek.common.Game;
import megamek.common.SimpleTechLevel;
import megamek.common.ToHitData;
import megamek.common.actions.WeaponAttackAction;
import megamek.common.weapons.AttackHandler;
import megamek.common.weapons.LRMHandler;
import megamek.common.weapons.lrms.LRMWeapon;
import megamek.server.totalwarfare.TWGameManager;

/**
 * @author Deric "Netzilla" Page (deric dot page at usa dot net)
 */
public class ISLRM10Primitive extends LRMWeapon {
    private static final long serialVersionUID = 6236976752458107991L;

    public ISLRM10Primitive() {
        super();
  
        name = "Primitive Prototype LRM 10";
        setInternalName(name);
        addLookupName("IS LRM-10 Primitive");
        addLookupName("ISLRM10p");
        addLookupName("IS LRM 10 Primitive");
        shortName = "LRM/10 p";
        sortingName = name;
        flags = flags.or(F_PROTOTYPE).andNot(F_ARTEMIS_COMPATIBLE);
        heat = 4;
        rackSize = 10;
        minimumRange = 6;
        tonnage = 5.0;
        criticals = 2;
        bv = 90;
        cost = 100000;
        shortAV = 6;
        medAV = 6;
        longAV = 6;
        maxRange = RANGE_LONG;
        ammoType = AmmoType.AmmoTypeEnum.LRM_PRIMITIVE;
        // IO Doesn't strictly define when these weapons stop production. Checked with Herb, and
        // they would always be around. This is to cover some of the back worlds in the Periphery.
        rulesRefs = "118, IO";
        techAdvancement.setTechBase(TechBase.IS)
                .setIntroLevel(false)
                .setUnofficial(false)
                .setTechRating(TechRating.C)
                .setAvailability(AvailabilityValue.F, AvailabilityValue.X, AvailabilityValue.X, AvailabilityValue.X)
                .setISAdvancement(2295, DATE_NONE, DATE_NONE, DATE_NONE, DATE_NONE)
                .setISApproximate(false, false, false, false, false)
                .setPrototypeFactions(Faction.TA)
                .setProductionFactions(Faction.TA)
                .setStaticTechLevel(SimpleTechLevel.EXPERIMENTAL);
    }

    @Override
    protected AttackHandler getCorrectHandler(ToHitData toHit, WeaponAttackAction waa, Game game,
                                              TWGameManager manager) {
        return new LRMHandler(toHit, waa, game, manager, -2);
    }
}
