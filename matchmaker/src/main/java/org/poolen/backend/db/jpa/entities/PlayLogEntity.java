package org.poolen.backend.db.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.poolen.backend.db.jpa.entities.ids.PlayLogEntityId;

import java.time.LocalDate;

@Entity
@Table(name = "player_playlog")
public class PlayLogEntity {

    @EmbeddedId
    private PlayLogEntityId id;

    @ManyToOne
    @MapsId("playerId")
    @JoinColumn(name = "player_id")
    private PlayerEntity owner;

    @ManyToOne
    @MapsId("playedWithPlayerId")
    @JoinColumn(name = "played_with_player_id")
    private PlayerEntity playedWith;

    @Column(name = "last_played_date", insertable = false, updatable = false)
    private LocalDate lastPlayedDate;

    // --- Constructors, Getters, and Setters ---

    public PlayLogEntity() {}

    public PlayLogEntity(PlayerEntity owner, PlayerEntity playedWith, LocalDate date) {
        this.id = new PlayLogEntityId(owner.getId(), playedWith.getId(), date);
        this.owner = owner;
        this.playedWith = playedWith;
        this.lastPlayedDate = date;
    }

    public PlayLogEntityId getId() {
        return id;
    }

    public void setId(PlayLogEntityId id) {
        this.id = id;
    }

    public PlayerEntity getOwner() {
        return owner;
    }

    public void setOwner(PlayerEntity owner) {
        this.owner = owner;
    }

    public PlayerEntity getPlayedWith() {
        return playedWith;
    }

    public void setPlayedWith(PlayerEntity playedWith) {
        this.playedWith = playedWith;
    }

    public LocalDate getLastPlayedDate() {
        return lastPlayedDate;
    }

    public void setLastPlayedDate(LocalDate lastPlayedDate) {
        this.lastPlayedDate = lastPlayedDate;
    }
}
