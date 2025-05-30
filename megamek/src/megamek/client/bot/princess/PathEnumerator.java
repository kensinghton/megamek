/*
 * MegaMek - Copyright (C) 2000-2011 Ben Mazur (bmazur@sev.org)
 * Copyright (c) 2024 - The MegaMek Team. All Rights Reserved.
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
package megamek.client.bot.princess;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import megamek.client.bot.BotClient;
import megamek.client.bot.princess.BotGeometry.ConvexBoardArea;
import megamek.client.bot.princess.BotGeometry.CoordFacingCombo;
import megamek.common.*;
import megamek.common.moves.MovePath;
import megamek.common.moves.MovePath.MoveStepType;
import megamek.common.pathfinder.*;
import megamek.common.pathfinder.AbstractPathFinder.Filter;
import megamek.common.pathfinder.AeroGroundPathFinder.AeroGroundOffBoardFilter;
import megamek.common.pathfinder.LongestPathFinder.MovePathMinefieldAvoidanceMinMPMaxDistanceComparator;
import megamek.common.util.BoardUtilities;
import megamek.logging.MMLogger;

/**
 * This class contains logic that calculates and stores
 * a) possible paths that units in play can take, and
 * b) their possible locations
 */
public class PathEnumerator {
    private final static MMLogger logger = MMLogger.create(PathEnumerator.class);

    private final Princess owner;
    private final Game game;
    private final Map<Integer, List<MovePath>> unitPaths = new ConcurrentHashMap<>();
    private final Map<Integer, List<BulldozerMovePath>> longRangePaths = new ConcurrentHashMap<>();
    private final Map<Integer, ConvexBoardArea> unitMovableAreas = new ConcurrentHashMap<>();
    private final Map<Integer, Set<CoordFacingCombo>> unitPotentialLocations = new ConcurrentHashMap<>();
    private final Map<Integer, CoordFacingCombo> lastKnownLocations = new ConcurrentHashMap<>();

    private AtomicBoolean mapHasBridges = null;
    private final Object BRIDGE_LOCK = new Object();

    public PathEnumerator(Princess owningPrincess, Game game) {
        owner = owningPrincess;
        this.game = game;
    }

    private Princess getOwner() {
        return owner;
    }

    void clear() {
        getUnitPaths().clear();
        getUnitPotentialLocations().clear();
        getLastKnownLocations().clear();
        getLongRangePaths().clear();
    }

    Coords getLastKnownCoords(Integer entityId) {
        CoordFacingCombo ccr = getLastKnownLocations().get(entityId);
        if (ccr == null) {
            return null;
        }
        return ccr.getCoords();
    }

    /**
     * Returns all {@link Entity} objects located at the given {@link Coords}.
     *
     * @param location   The {@link Coords} to be searched for units.
     * @param groundOnly Set TRUE to ignore {@link Aero} units.
     * @return A {@link Set} of {@link Entity} objects at the given {@link Coords}.
     */
    public Set<Integer> getEntitiesWithLocation(Coords location, boolean groundOnly) {
        Set<Integer> returnSet = new TreeSet<>();
        if (location == null) {
            return returnSet;
        }
        for (Integer id : getUnitPotentialLocations().keySet()) {
            if (groundOnly
                    && getGame().getEntity(id) != null
                    && getGame().getEntity(id).isAero()) {
                continue;
            }

            for (int facing = 0; facing < 5; facing++) {
                if (getUnitPotentialLocations().get(id)
                        .contains(CoordFacingCombo.createCoordFacingCombo(location, facing))) {
                    returnSet.add(id);
                    break;
                }
            }
        }
        return returnSet;
    }

    /**
     * From a list of potential moves, make a potential ending location chart
     */
    void updateUnitLocations(Entity entity, List<MovePath> paths) {
        // clear previous locations for this entity
        getUnitPotentialLocations().remove(entity.getId());
        //
        Set<CoordFacingCombo> toAdd = new HashSet<>();
        for (MovePath path : paths) {
            toAdd.add(CoordFacingCombo.createCoordFacingCombo(path));
        }
        getUnitPotentialLocations().put(entity.getId(), toAdd);
    }

    /**
     * Calculate what to do on my turn.
     * Has a retry mechanism for when the turn calculation fails due to concurrency
     * issues
     */
    public synchronized void recalculateMovesFor(final Entity mover) {
        int retryCount = 0;
        boolean success = false;

        while ((retryCount < BotClient.BOT_TURN_RETRY_COUNT) && !success) {
            success = recalculateMovesForWorker(mover);

            if (!success) {
                // if we fail, take a nap for 500-1500 milliseconds, then try again
                // as it may be due to some kind of thread-related issue
                // limit number of retries so we're not endlessly spinning
                // if we can't recover from the error
                retryCount++;
                try {
                    Thread.sleep(Compute.randomInt(1000) + 500);
                } catch (InterruptedException e) {
                    logger.error(e, "recalculateMovesFor");
                } catch (Exception e) {
                    logger.error(e, "Unexpected (non-interrupt) exception!");
                }
            }
        }
    }

    /**
     * calculates all moves for a given unit, keeping the shortest (or longest,
     * depending) path to each facing/pair
     */
    private boolean recalculateMovesForWorker(final Entity mover) {
        try {
            // Record it's current position.
            getLastKnownLocations().put(
                    mover.getId(),
                    CoordFacingCombo.createCoordFacingCombo(
                            mover.getPosition(), mover.getFacing()));

            // Clear out any already calculated paths.
            getUnitPaths().remove(mover.getId());
            getLongRangePaths().remove(mover.getId());

            // if the entity does not exist in the game for any reason, let's cut out safely
            // otherwise, we'll run into problems calculating paths
            if (getGame().getEntity(mover.getId()) == null) {
                // clean up orphaned entries in local storage
                getUnitMovableAreas().remove(mover.getId());
                getUnitPotentialLocations().remove(mover.getId());
                getLastKnownLocations().remove(mover.getId());
                return true;
            }

            // Start constructing the new list of paths.
            List<MovePath> paths = new ArrayList<>();
            Coords wayPoint = owner.getUnitBehaviorTracker().getWaypointForEntity(mover).orElse(null);
            // Aero movement on atmospheric ground maps
            // currently only applies to a) conventional aircraft, b) AeroTek units, c) lams
            // in air mode
            if (mover.isAirborneAeroOnGroundMap() && !((IAero) mover).isSpheroid()) {
                AeroGroundPathFinder apf = AeroGroundPathFinder.getInstance(getGame());
                MovePath startPath = new MovePath(getGame(), mover, wayPoint);
                apf.run(startPath);
                paths.addAll(apf.getAllComputedPathsUncategorized());

                // Remove illegal paths.
                Filter<MovePath> filter = new Filter<>() {
                    @Override
                    public boolean shouldStay(MovePath movePath) {
                        return isLegalAeroMove(movePath);
                    }
                };

                logger.debug("Unfiltered paths: " + paths.size());
                paths = new ArrayList<>(filter.doFilter(paths));
                logger.debug("Filtered out illegal paths: " + paths.size());
                AeroGroundOffBoardFilter offBoardFilter = new AeroGroundOffBoardFilter();
                paths = new ArrayList<>(offBoardFilter.doFilter(paths));

                MovePath offBoardPath = offBoardFilter.getShortestPath();
                if (offBoardPath != null) {
                    paths.add(offBoardFilter.getShortestPath());
                }

                logger.debug("Filtered out off board paths: " + paths.size());

                // This is code useful for debugging, but puts out a lot of log entries, which
                // slows things down.
                // disabled
                // logAllPaths(paths);
                // this handles the case of the mover being an aerospace unit and "advances
                // space flight" rules being on
            } else if (mover.isAero() && game.useVectorMove()) {
                NewtonianAerospacePathFinder npf = NewtonianAerospacePathFinder.getInstance(getGame());
                npf.run(new MovePath(game, mover, wayPoint));
                paths.addAll(npf.getAllComputedPathsUncategorized());
                // this handles the case of the mover being an aerospace unit on a space map
            } else if (mover.isAero() && game.getBoard().isSpace()) {
                AeroSpacePathFinder apf = AeroSpacePathFinder.getInstance(getGame());
                apf.run(new MovePath(game, mover, wayPoint));
                paths.addAll(apf.getAllComputedPathsUncategorized());
                // this handles the case of the mover being a winged aerospace unit on a
                // low-atmosphere map
            } else if (mover.isAero() && game.getBoard().isLowAltitude()
                    && !Compute.useSpheroidAtmosphere(game, mover)) {
                AeroLowAltitudePathFinder apf = AeroLowAltitudePathFinder.getInstance(getGame());
                apf.run(new MovePath(game, mover, wayPoint));
                paths.addAll(apf.getAllComputedPathsUncategorized());
                // this handles the case of the mover acting like a spheroid aerospace unit in
                // an atmosphere
            } else if (Compute.useSpheroidAtmosphere(game, mover)) {
                int dir = AeroPathUtil.getSpheroidDir(game, mover);
                SpheroidPathFinder spf = SpheroidPathFinder.getInstance(game, dir);
                spf.run(new MovePath(game, mover, wayPoint));
                paths.addAll(spf.getAllComputedPathsUncategorized());
                // this handles the case of the mover being an infantry unit of some kind,
                // that's not airborne.
            } else if (mover.hasETypeFlag(Entity.ETYPE_INFANTRY) && !mover.isAirborne()) {
                InfantryPathFinder ipf = InfantryPathFinder.getInstance(getGame());
                ipf.run(new MovePath(game, mover, wayPoint));
                paths.addAll(ipf.getAllComputedPathsUncategorized());

                // generate long-range paths appropriate to the bot's current state
                updateLongRangePaths(mover);
                // this handles situations where a unit is high up in the air, but is not an
                // aircraft
                // such as an ejected pilot or a unit hot dropping from a DropShip, as these
                // cannot move
            } else if (!mover.isAero() && mover.isAirborne()) {
                paths.add(new MovePath(game, mover, wayPoint));
            } else { // Non-Aero movement
                // TODO: Will this cause Princess to never use MASC?
                int maxMove = Math.min(mover.getRunMPwithoutMASC(), mover.getRunMP(MPCalculationSetting.NO_GRAVITY));

                LongestPathFinder lpf = LongestPathFinder.newInstanceOfLongestPath(maxMove,
                        MoveStepType.FORWARDS, getGame());
                lpf.setComparator(new MovePathMinefieldAvoidanceMinMPMaxDistanceComparator());
                lpf.run(new MovePath(game, mover, wayPoint));
                paths.addAll(lpf.getLongestComputedPaths());

                // add walking moves
                lpf = LongestPathFinder.newInstanceOfLongestPath(
                        mover.getWalkMP(), MoveStepType.BACKWARDS, getGame());
                lpf.setComparator(new MovePathMinefieldAvoidanceMinMPMaxDistanceComparator());
                lpf.run(new MovePath(getGame(), mover, wayPoint));
                paths.addAll(lpf.getLongestComputedPaths());

                // add all moves that involve the entity remaining prone
                PronePathFinder ppf = new PronePathFinder();
                ppf.run(new MovePath(getGame(), mover, wayPoint));
                paths.addAll(ppf.getPronePaths());

                // add jumping moves
                if (mover.getAnyTypeMaxJumpMP() > 0) {
                    ShortestPathFinder spf = ShortestPathFinder.newInstanceOfOneToAll(mover.getAnyTypeMaxJumpMP(),
                            MoveStepType.FORWARDS, getGame());
                    spf.setComparator(new MovePathMinefieldAvoidanceMinMPMaxDistanceComparator());
                    spf.run((new MovePath(game, mover, wayPoint)).addStep(MoveStepType.START_JUMP));
                    paths.addAll(spf.getAllComputedPathsUncategorized());
                }

                // calling .debug is expensive even if we don't actually log anything
                // so let's not do this unless we're debugging
                /*
                 * for (MovePath path : paths) {
                 * getOwner().getLogger().debug(path.toString());
                 * }
                 */

                // Try climbing over obstacles and onto bridges
                adjustPathsForBridges(paths);

                // filter those paths that end in illegal state
                Filter<MovePath> filter = new Filter<>() {
                    @Override
                    public boolean shouldStay(MovePath movePath) {
                        return movePath.isMoveLegal()
                                && (Compute.stackingViolation(getGame(), mover.getId(), movePath.getFinalCoords(),
                                        mover.climbMode()) == null);
                    }
                };
                paths = new ArrayList<>(filter.doFilter(paths));

                // generate long-range paths appropriate to the bot's current state
                updateLongRangePaths(mover);
            }

            // Update our locations and add the computed paths.
            updateUnitLocations(mover, paths);
            getUnitPaths().put(mover.getId(), paths);

            // calculate bounding area for move
            ConvexBoardArea myArea = new ConvexBoardArea();
            myArea.addCoordFacingCombos(getUnitPotentialLocations().get(
                    mover.getId()).iterator(), owner.getBoard());
            getUnitMovableAreas().put(mover.getId(), myArea);

            return true;
        } catch (IllegalArgumentException ex) {
            logger.debug(ex, "Lost sight of a unit while plotting predicted paths");
            return false;
        } catch (Exception e) {
            logger.error(e, "recalculateMovesForWorker");
            return false;
        }
    }

    /**
     * Worker function that updates the long-range path collection for a particular
     * entity
     */
    private void updateLongRangePaths(final Entity mover) {
        // don't bother doing this if the entity can't move anyway
        // or if it's not one of mine
        // or if I've already moved it
        if ((mover.getWalkMP() == 0) ||
                ((getOwner().getLocalPlayer() != null) && (mover.getOwnerId() != getOwner().getLocalPlayer().getId()))
                ||
                !mover.isSelectableThisTurn()) {
            return;
        }

        DestructionAwareDestinationPathfinder dpf = new DestructionAwareDestinationPathfinder();

        // where are we going?
        Set<Coords> destinations = new HashSet<>();
        // if we're going to an edge or can't see anyone, generate long-range paths to
        // the opposite edge
        switch (getOwner().getUnitBehaviorTracker().getBehaviorType(mover, getOwner())) {
            case ForcedWithdrawal:
                destinations = getOwner().getClusterTracker().getDestinationCoords(mover, getOwner().getHomeEdge(mover),
                    true);
                break;
            case MoveToDestination:
                getOwner().getUnitBehaviorTracker().getWaypointForEntity(mover).ifPresent(destinations::add);
                if (destinations.isEmpty()) {
                    destinations = getOwner().getClusterTracker().getDestinationCoords(mover, getOwner().getHomeEdge(mover), true);
                }
                break;
            case MoveToContact:

                // If there are no active or sensor contacts, check the heat maps for best
                // location
                // we've seen for finding targets
                List<Coords> enemyHotSpots = owner.getEnemyHotSpots();
                getOwner().getUnitBehaviorTracker().getWaypointForEntity(mover).ifPresent(destinations::add);
                if (enemyHotSpots != null && !enemyHotSpots.isEmpty()) {
                    destinations.addAll(enemyHotSpots);
                } else {

                    // If the heat map doesn't have any useful targets, just go to the other side of
                    // map and hope to stumble across something on the way
                    CardinalEdge oppositeEdge = BoardUtilities.determineOppositeEdge(mover);
                    destinations = getOwner().getClusterTracker().getDestinationCoords(mover, oppositeEdge, true);
                }

                break;
            default:
                for (Targetable target : FireControl.getAllTargetableEnemyEntities(getOwner().getLocalPlayer(),
                        getGame(), getOwner().getFireControlState())) {
                    // don't consider crippled units as valid long-range pathfinding targets
                    if ((target.getTargetType() == Targetable.TYPE_ENTITY) && ((Entity) target).isCrippled()) {
                        continue;
                    }
                    getOwner().getUnitBehaviorTracker().getWaypointForEntity(mover).ifPresent(destinations::add);
                    destinations.add(target.getPosition());
                    // we can easily shoot at an entity from right next to it as well
                    destinations.addAll(target.getPosition().allAdjacent());
                }
                break;
        }

        if (!getLongRangePaths().containsKey(mover.getId())) {
            getLongRangePaths().put(mover.getId(), new ArrayList<>());
        }

        // calculate a ground-bound long range path
        BulldozerMovePath bmp = dpf.findPathToCoords(mover, destinations, owner.getClusterTracker());

        if (bmp != null) {
            getLongRangePaths().get(mover.getId()).add(bmp);
        }

        // calculate a jumping long range path
        BulldozerMovePath jmp = dpf.findPathToCoords(mover, destinations, true, owner.getClusterTracker());
        if (jmp != null) {
            getLongRangePaths().get(mover.getId()).add(jmp);
        }
    }

    private void adjustPathsForBridges(List<MovePath> paths) {
        if (!worryAboutBridges()) {
            return;
        }

        for (MovePath path : paths) {
            adjustPathForBridge(path);
        }
    }

    private void adjustPathForBridge(MovePath path) {
        boolean needsAdjust = false;
        for (Coords c : path.getCoordsSet()) {
            Hex hex = getGame().getBoard().getHex(c);
            if ((hex != null) && hex.containsTerrain(Terrains.BRIDGE)) {
                if (getGame().getBoard().getBuildingAt(c).getCurrentCF(c) >= path.getEntity().getWeight()) {
                    needsAdjust = true;
                    break;
                } else {
                    break;
                }
            }
        }
        if (!needsAdjust) {
            return;
        }
        MovePath adjusted = new MovePath(getGame(), path.getEntity(), path.getWaypoint());
        adjusted.addStep(MoveStepType.CLIMB_MODE_ON);
        adjusted.addSteps(path.getStepVector(), true);
        adjusted.addStep(MoveStepType.CLIMB_MODE_OFF);
        path.replaceSteps(adjusted.getStepVector());
    }

    // public void debugPrintContents() {
    // getOwner().getLogger().methodBegin();
    // try {
    // for (Integer id : getUnitPaths().keySet()) {
    // Entity entity = getGame().getEntity(id);
    // List<MovePath> paths = getUnitPaths().get(id);
    // int pathsSize = paths.size();
    // String msg = "Unit " + entity.getDisplayName() + " has " + pathsSize + "
    // paths and " +
    // getUnitPotentialLocations().get(id).size() + " ending locations.";
    // getOwner().log(msg);
    // }
    // } finally {
    // getOwner().getLogger().methodEnd();
    // }
    // }

    /**
     * Returns whether a {@link MovePath} is legit for an {@link Aero} unit
     * isMoveLegal() seems to disagree with me
     * on some aero moves, but I can't exactly figure out why, and who is right. So,
     * I'm just going to put a list of
     * exceptions here instead of possibly screwing up
     * {@link MovePath#isMoveLegal()} for everyone. I think it has
     * to do with fly off or return at the end of a move. This also affects clip to
     * possible
     *
     * @param path The path to be examined.
     * @return TRUE if the path is legal.
     */
    public boolean isLegalAeroMove(MovePath path) {
        // no non-aero's allowed
        if (!path.getEntity().isAero()) {
            return true;
        }

        if (!path.isMoveLegal()) {
            if (path.getLastStep() == null) {
                LogAeroMoveLegalityEvaluation("illegal move with null last step", path);
                return false;
            }
            if ((path.getLastStep().getType() != MoveStepType.RETURN) &&
                    (path.getLastStep().getType() != MoveStepType.OFF)) {
                LogAeroMoveLegalityEvaluation("illegal move without return/off at the end", path);
                return false;
            }
        }

        // we have to have used all velocity by the last step
        if ((path.getLastStep() != null) && (path.getLastStep().getVelocityLeft() != 0)) {
            if ((path.getLastStep().getType() != MoveStepType.RETURN) &&
                    (path.getLastStep().getType() != MoveStepType.OFF)) {
                LogAeroMoveLegalityEvaluation("not all velocity used without return/off at the end", path);
                return false;
            }
        }
        return true;
    }

    private void LogAeroMoveLegalityEvaluation(String whyNot, MovePath path) {
        logger.debug(path.length() + ":" + path + ":" + whyNot);
    }

    protected Map<Integer, List<BulldozerMovePath>> getLongRangePaths() {
        return longRangePaths;
    }

    protected Map<Integer, List<MovePath>> getUnitPaths() {
        return unitPaths;
    }

    public Map<Integer, ConvexBoardArea> getUnitMovableAreas() {
        return unitMovableAreas;
    }

    protected Map<Integer, Set<CoordFacingCombo>> getUnitPotentialLocations() {
        return unitPotentialLocations;
    }

    protected Map<Integer, CoordFacingCombo> getLastKnownLocations() {
        return lastKnownLocations;
    }

    protected Game getGame() {
        return game;
    }

    private boolean worryAboutBridges() {
        if (mapHasBridges != null) {
            return mapHasBridges.get();
        }

        synchronized (BRIDGE_LOCK) {
            if (mapHasBridges != null) {
                return mapHasBridges.get();
            }

            mapHasBridges = new AtomicBoolean(getGame().getBoard().containsBridges());
        }

        return mapHasBridges.get();
    }

    /**
     * Find paths with a similar direction and step count to the provided path, within the selected unit's
     * already-computed unit paths.
     * @param moverId
     * @param prunedPath
     * @return
     */
    protected List<MovePath> getSimilarUnitPaths(int moverId, BulldozerMovePath prunedPath) {
        int mpDelta = 2;
        int distanceDelta = 2;

        List<MovePath> paths = new ArrayList<>();
        if (!getUnitPaths().containsKey(moverId)) {
            return paths;
        }
        List<MovePath> unitPaths = getUnitPaths().get(moverId);

        Coords target = prunedPath.getDestination();
        int prunedDistance = target.distance(prunedPath.getFinalCoords());

        for (MovePath movePath: unitPaths) {
            // We want unit paths that use similar amounts of MP to get similarly close to the BMP's destination
            if (Math.abs((target.distance(movePath.getFinalCoords()) - prunedDistance)) > distanceDelta) {
                continue;
            }

            if (Math.abs(movePath.getMpUsed() - prunedPath.getMpUsed()) > mpDelta ) {
                continue;
            }

            paths.add(movePath);
        }

        return paths;
    }
}
