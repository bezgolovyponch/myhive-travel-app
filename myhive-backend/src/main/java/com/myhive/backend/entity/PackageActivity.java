package com.myhive.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
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
