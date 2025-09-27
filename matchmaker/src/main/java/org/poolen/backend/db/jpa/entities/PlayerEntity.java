package org.poolen.backend.db.jpa.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "players")
public class PlayerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private UUID uuid;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_dungeon_master")
    private boolean isDungeonMaster;

    @Column(name = "last_seen")
    private LocalDate lastSeen;

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<CharacterEntity> characters = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "player_buddylist",
            joinColumns = @JoinColumn(name = "player_id"),
            inverseJoinColumns = @JoinColumn(name = "buddy_id")
    )
    private Set<PlayerEntity> buddylist = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "player_blacklist",
            joinColumns = @JoinColumn(name = "player_id"),
            inverseJoinColumns = @JoinColumn(name = "blacklisted_player_id")
    )
    private Set<PlayerEntity> blacklist = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "player_dm_blacklist",
            joinColumns = @JoinColumn(name = "player_id"),
            inverseJoinColumns = @JoinColumn(name = "blacklisted_dm_id")
    )
    private Set<PlayerEntity> dmBlacklist = new HashSet<>();

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<PlayLogEntity> playerLog = new HashSet<>();

    // --- Constructors ---

    public PlayerEntity() {
        //this.uuid = UUID.randomUUID();
    }

    public PlayerEntity(String name, boolean isDungeonMaster) {
        this.uuid = UUID.randomUUID();
        this.name = name;
        this.isDungeonMaster = isDungeonMaster;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDungeonMaster() {
        return isDungeonMaster;
    }

    public void setDungeonMaster(boolean dungeonMaster) {
        isDungeonMaster = dungeonMaster;
    }

    public LocalDate getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDate lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Set<CharacterEntity> getCharacters() {
        return characters;
    }

    public void setCharacters(Set<CharacterEntity> characters) {
        this.characters = characters;
    }

    public Set<PlayerEntity> getBuddylist() {
        return buddylist;
    }

    public void setBuddylist(Set<PlayerEntity> buddylist) {
        this.buddylist = buddylist;
    }

    public Set<PlayerEntity> getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(Set<PlayerEntity> blacklist) {
        this.blacklist = blacklist;
    }

    public Set<PlayerEntity> getDmBlacklist() {
        return dmBlacklist;
    }

    public void setDmBlacklist(Set<PlayerEntity> dmBlacklist) {
        this.dmBlacklist = dmBlacklist;
    }

    public Set<PlayLogEntity> getPlayerLog() {
        return playerLog;
    }

    public void setPlayerLog(Set<PlayLogEntity> playerLog) {
        this.playerLog = playerLog;
    }
}
