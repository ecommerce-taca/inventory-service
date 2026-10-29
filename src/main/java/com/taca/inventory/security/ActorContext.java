package com.taca.inventory.security;

import java.util.Collections;
import java.util.Set;

/**
 * Request-scoped actor context capturing identity, roles, permissions, and shop scope
 * propagated by Gateway headers or internal service tokens.
 */
public class ActorContext {

    private static final ThreadLocal<ActorContext> CURRENT = new ThreadLocal<>();

    private String userId;
    private Set<String> roles = Collections.emptySet();
    private Set<String> permissions = Collections.emptySet();
    private String shopScope;
    private boolean internalService;

    public ActorContext() {}

    public static void clear() {
        CURRENT.remove();
    }

    public static void setCurrent(ActorContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }

    public static ActorContext getCurrent() {
        return CURRENT.get();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUserId() {
        return userId;
    }

    public Set<String> getRoles() {
        return roles != null ? roles : Collections.emptySet();
    }

    public Set<String> getPermissions() {
        return permissions != null ? permissions : Collections.emptySet();
    }

    public String getShopScope() {
        return shopScope;
    }

    public boolean isSeller() {
        return getRoles().contains("SELLER");
    }

    public boolean isAdmin() {
        return getRoles().contains("ADMIN");
    }

    public boolean hasRole(String role) {
        return role != null && getRoles().contains(role);
    }

    public boolean hasPermission(String permission) {
        return permission != null && getPermissions().contains(permission);
    }

    public boolean isInternalService() {
        return internalService;
    }

    public boolean isAnonymous() {
        return (userId == null || userId.isBlank()) && !internalService;
    }

    public static class Builder {
        private final ActorContext ctx = new ActorContext();

        public Builder userId(String userId) {
            ctx.userId = userId;
            return this;
        }

        public Builder roles(Set<String> roles) {
            ctx.roles = roles != null ? Set.copyOf(roles) : Collections.emptySet();
            return this;
        }

        public Builder permissions(Set<String> permissions) {
            ctx.permissions = permissions != null ? Set.copyOf(permissions) : Collections.emptySet();
            return this;
        }

        public Builder shopScope(String shopScope) {
            ctx.shopScope = shopScope;
            return this;
        }

        public Builder internalService(boolean internalService) {
            ctx.internalService = internalService;
            return this;
        }

        public ActorContext build() {
            return ctx;
        }
    }
}
