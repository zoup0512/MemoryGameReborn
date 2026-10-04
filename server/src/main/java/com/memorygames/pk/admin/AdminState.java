package com.memorygames.pk.admin;

import org.springframework.stereotype.Component;

/** Runtime toggles owned by the admin plane (maintenance mode, 4.7). */
@Component
public class AdminState {

    private volatile boolean maintenance;
    private volatile String maintenanceMessage = "";

    public boolean isMaintenance() {
        return this.maintenance;
    }

    public String getMaintenanceMessage() {
        return this.maintenanceMessage;
    }

    public void setMaintenance(boolean maintenance, String message) {
        this.maintenance = maintenance;
        this.maintenanceMessage = message == null ? "" : message;
    }
}
