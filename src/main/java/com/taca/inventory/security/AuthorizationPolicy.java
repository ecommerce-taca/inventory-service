package com.taca.inventory.security;

import com.taca.inventory.common.error.ErrorCode;
import com.taca.inventory.common.error.InventoryException;
import org.springframework.stereotype.Component;

/**
 * Enforces authorization policies, shop scope isolation (anti-IDOR),
 * and step-up permissions for inventory operations.
 */
@Component
public class AuthorizationPolicy {

    /**
     * Ensures an authenticated actor exists in the current request context.
     *
     * @return the current authenticated ActorContext
     * @throws InventoryException if unauthenticated
     */
    public ActorContext requireAuthenticated() {
        ActorContext actor = ActorContext.getCurrent();
        if (actor == null || actor.isAnonymous()) {
            throw new InventoryException(ErrorCode.INVENTORY_UNAUTHENTICATED, "Authentication required");
        }
        return actor;
    }

    /**
     * Enforces that the request is initiated by a trusted internal service.
     *
     * @throws InventoryException if unauthenticated or not an internal service
     */
    public void requireInternalService() {
        ActorContext actor = requireAuthenticated();
        if (!actor.isInternalService()) {
            throw new InventoryException(ErrorCode.INVENTORY_FORBIDDEN, "Internal service access required");
        }
    }

    /**
     * Enforces that the requesting actor has the SELLER role and owns the given shop scope.
     * Prevents Insecure Direct Object Reference (IDOR) attacks across shops.
     *
     * @param targetShopScope the shop identifier associated with the SKU/inventory item
     * @throws InventoryException if unauthenticated, not a seller, or shop scope mismatches
     */
    public void requireSellerShopScope(String targetShopScope) {
        ActorContext actor = requireAuthenticated();
        if (!actor.isSeller()) {
            throw new InventoryException(ErrorCode.INVENTORY_FORBIDDEN, "Seller role required");
        }
        if (targetShopScope == null || actor.getShopScope() == null || !actor.getShopScope().equals(targetShopScope)) {
            throw new InventoryException(ErrorCode.INVENTORY_FORBIDDEN, "Shop scope access denied");
        }
    }

    /**
     * Enforces that the requesting actor has the ADMIN role and possesses the required step-up permission.
     *
     * @param permission the required permission (e.g. inventory:reconcile)
     * @throws InventoryException if unauthenticated, not an admin, or missing the permission
     */
    public void requireAdminStepUp(String permission) {
        ActorContext actor = requireAuthenticated();
        if (!actor.isAdmin()) {
            throw new InventoryException(ErrorCode.INVENTORY_FORBIDDEN, "Admin role required");
        }
        if (permission != null && !actor.hasPermission(permission)) {
            throw new InventoryException(ErrorCode.INVENTORY_FORBIDDEN, "Step-up permission required: " + permission);
        }
    }
}
