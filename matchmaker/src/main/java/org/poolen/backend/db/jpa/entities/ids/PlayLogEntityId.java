package org.poolen.backend.db.jpa.entities.ids;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class PlayLogEntityId implements Serializable {

    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "played_with_player_id")
    private Long playedWithPlayerId;

    @Column(name = "last_played_date")
    private LocalDate lastPlayedDate;


    public PlayLogEntityId() {}

    public PlayLogEntityId(Long playerId, Long playedWithPlayerId, LocalDate lastPlayedDate) {
        this.playerId = playerId;
        this.playedWithPlayerId = playedWithPlayerId;
        this.lastPlayedDate = lastPlayedDate;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public Long getPlayedWithPlayerId() {
        return playedWithPlayerId;
    }

    public void setPlayedWithPlayerId(Long playedWithPlayerId) {
        this.playedWithPlayerId = playedWithPlayerId;
    }

    public LocalDate getLastPlayedDate() {
        return lastPlayedDate;
    }

    public void setLastPlayedDate(LocalDate lastPlayedDate) {
        this.lastPlayedDate = lastPlayedDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayLogEntityId that = (PlayLogEntityId) o;
        return Objects.equals(playerId, that.playerId) &&
                Objects.equals(playedWithPlayerId, that.playedWithPlayerId) &&
                Objects.equals(lastPlayedDate, that.lastPlayedDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, playedWithPlayerId, lastPlayedDate);
    }
}
