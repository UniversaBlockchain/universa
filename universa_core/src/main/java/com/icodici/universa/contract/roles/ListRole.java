/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 9/28/17.
 *
 */

package com.icodici.universa.contract.roles;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.KeyRecord;
import com.sun.istack.internal.NotNull;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Role combining other roles (sub-roles) in the "and", "or" and "any N of" principle.
 */
@BiType(name = "ListRole")
public class ListRole extends Role {

    private Mode mode;

    private Set<Role> roles = new HashSet<>();

    private int quorumSize = 0;

    public ListRole() {
    }

    public ListRole(String name) {
        super(name);
    }

    public ListRole(String name, Mode mode, @NotNull Collection<Role> roles) {
        super(name);
        setMode(mode);
        addAll(roles);
    }

    public ListRole(String name, int quorumSize, @NotNull Collection<Role> roles) {
        super(name);
        this.mode = Mode.QUORUM;
        this.quorumSize = quorumSize;
        addAll(roles);
    }

    public void addAll(Collection<Role> roles) {
        this.roles.addAll(roles);
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public ListRole addRole(Role role) {
        this.roles.add(role);
        return this;
    }

    /**
     * Set mode to {@link Mode#QUORUM} and quorum size to any n roles.
     *
     * @param n how many subroles set of key must be able to play to play this role
     */
    public void setQuorum(int n) {
        mode = Mode.QUORUM;
        quorumSize = n;
    }

    /**
     * @return quorum size if in quorum mode, otherwise 0
     */
    public int getQuorum() {
        return Mode.QUORUM.equals(mode) ? this.quorumSize : 0;
    }

    /**
     * Set mode to either {@link Mode#ALL} or {@link Mode#ANY}, Quorum mode could be set only with {@link #setQuorum(int)}
     * call,
     *
     * @param newMode mode to set
     * @throws IllegalArgumentException if mode other than ANY/ALL is specified
     */
    public void setMode(Mode newMode) {
        if (!Mode.QUORUM.equals(newMode))
            this.mode = newMode;
        else
            throw new IllegalArgumentException("Only ANY or ALL of the modes should be set.");
    }


    @Override
    public boolean isAllowedForKeys(Set<? extends AbstractKey> keys) {
        return Mode.ANY.equals(this.mode) && this.processAnyMode(keys) ||
                Mode.ALL.equals(this.mode) && this.processAllMode(keys) ||
                Mode.QUORUM.equals(this.mode) && this.processQuorumMode(keys);
    }

    private boolean processQuorumMode(Set<? extends AbstractKey> keys) {
        long matchNumber = this.roles.stream().filter(role -> role.isAllowedForKeys(keys)).count();
        return matchNumber >= this.quorumSize;
    }

    private boolean processAllMode(Set<? extends AbstractKey> keys) {
        return this.roles.stream().allMatch(role -> role.isAllowedForKeys(keys));
    }

    private boolean processAnyMode(Set<? extends AbstractKey> keys) {
        return this.roles.stream().anyMatch(role -> role.isAllowedForKeys(keys));
    }

    @Override
    public boolean isValid() {
        return !this.roles.isEmpty();
    }

    @Override
    public Set<PublicKey> getKeys() {
        return this.roles.stream()
                .flatMap(role -> role.getKeyRecords().stream()
                        .map(KeyRecord::getPublicKey))
                .collect(Collectors.toSet());
    }

    /**
     * Mode of combining roles
     */
    enum Mode {
        /**
         * Role could be performed only if set of keys could play all sub-roles
         */
        ALL("all"),
        /**
         * Role could be performed if set of keys could play any role of the list
         */
        ANY("any"),
        /**
         * Role could be played if set of keys could play any quorrum set of roles, e.g. at least any N subroles,
         * controlled by the {@link #setQuorum(int)} method
         */
        QUORUM("quorum");

        private String value;

        Mode(String v) {
            this.value = v;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    @Override
    public String toString() {
        return String.format("ListRole<%s:%s:%s:%s>", System.identityHashCode(this), getName(),
                Mode.QUORUM.equals(this.mode) ? this.mode + "_" + this.quorumSize : this.mode, this.roles);
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);

        Object quorumSize = data.getOrDefault("quorumSize", null);
        if (quorumSize != null) {
            try {
                this.quorumSize = quorumSize instanceof Integer ? (Integer) quorumSize
                        : quorumSize instanceof String ? Integer.parseInt((String) quorumSize)
                        : 0;
            } catch (NumberFormatException ignored) {
            }
        }

        Object mode = data.getOrDefault("mode", null);
        if (mode != null && mode instanceof Mode) {
            this.mode = (Mode) mode;
        }

        List<Binder> roles = data.getList("roles", null);
        if (roles != null) {
            this.roles.clear();
            roles.forEach(role -> addRole(deserializer.deserialize(role)));
        }


    }

    @Override
    public Binder serialize(BiSerializer s) {
        return super.serialize(s).putAll(
                "quorumSize", s.serialize(this.quorumSize),
                "mode", s.serialize(this.mode),
                "roles", s.serialize(this.roles));
    }

    static {
        DefaultBiMapper.registerClass(ListRole.class);
    }
}