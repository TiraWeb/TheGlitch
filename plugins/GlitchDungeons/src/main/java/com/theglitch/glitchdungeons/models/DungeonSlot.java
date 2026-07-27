package com.theglitch.glitchdungeons.models;

import java.util.UUID;

public class DungeonSlot {
    private final int id;
    private final int centerX;
    private final int centerZ;
    private boolean occupied;
    private UUID assignedParty;

    public DungeonSlot(int id, int centerX, int centerZ) {
        this.id = id;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.occupied = false;
        this.assignedParty = null;
    }

    public int getId() { return id; }
    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }
    public boolean isOccupied() { return occupied; }
    public UUID getAssignedParty() { return assignedParty; }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    public void setAssignedParty(UUID partyId) {
        this.assignedParty = partyId;
    }

    public void clear() {
        this.occupied = false;
        this.assignedParty = null;
    }
}
