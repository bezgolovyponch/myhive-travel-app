package com.myhive.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "package_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(PackageActivity.PackageActivityId.class)
@ToString(exclude = {"pkg", "activity"})
public class PackageActivity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id")
    private Package pkg;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Column(nullable = false)
    private Integer position;

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageActivityId implements Serializable {
        private UUID pkg;
        private UUID activity;
    }
}
