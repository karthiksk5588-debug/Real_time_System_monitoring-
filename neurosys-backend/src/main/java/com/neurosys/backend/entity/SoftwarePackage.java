package com.neurosys.backend.entity;

import com.neurosys.backend.enums.InstallerType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "software_packages")
@SQLDelete(sql = "UPDATE software_packages SET deleted = true, deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoftwarePackage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "installer_url", nullable = false, length = 500)
    private String installerUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "installer_type", nullable = false, length = 20)
    private InstallerType installerType;

    @Column(name = "silent_arguments", length = 300)
    private String silentArguments;

    @Column(name = "checksum", nullable = false, length = 128)
    private String checksum;

    @Column(name = "supported_os", length = 100)
    private String supportedOs;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
