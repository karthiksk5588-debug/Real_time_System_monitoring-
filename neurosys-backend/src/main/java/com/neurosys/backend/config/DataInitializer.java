package com.neurosys.backend.config;

import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.entity.Lab;
import com.neurosys.backend.entity.User;
import com.neurosys.backend.enums.ComputerStatus;
import com.neurosys.backend.enums.Role;
import com.neurosys.backend.repository.ComputerRepository;
import com.neurosys.backend.repository.LabRepository;
import com.neurosys.backend.repository.SoftwareInventoryRepository;
import com.neurosys.backend.repository.SoftwarePackageRepository;
import com.neurosys.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ComputerRepository computerRepository;
    private final SoftwareInventoryRepository softwareInventoryRepository;
    private final LabRepository labRepository;
    private final SoftwarePackageRepository softwarePackageRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 1. Seed Default Administrator User
        if (userRepository.findByUsername("admin").isEmpty()) {
            log.info("Seeding default Administrator user: admin / admin123");
            User admin = User.builder()
                    .username("admin")
                    .email("admin@neurosys.com")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .role(Role.ROLE_ADMIN)
                    .active(true)
                    .build();
            userRepository.save(admin);
            log.info("Default Administrator user created successfully.");
        }

        // 2. Seed Default Computer Labs if none exist
        Lab lab1 = labRepository.findByCodeIgnoreCase("LAB-001").orElseGet(() -> {
            log.info("Seeding default Computer Lab 1 (LAB-001)...");
            return labRepository.save(Lab.builder()
                    .name("Computer Lab 1")
                    .code("LAB-001")
                    .location("Building A, Room 101")
                    .description("General Programming & Software Engineering Laboratory")
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .build());
        });

        labRepository.findByCodeIgnoreCase("LAB-002").orElseGet(() -> {
            log.info("Seeding default Computer Lab 2 (LAB-002)...");
            return labRepository.save(Lab.builder()
                    .name("Computer Lab 2")
                    .code("LAB-002")
                    .location("Building A, Room 102")
                    .description("Artificial Intelligence & Data Science Laboratory")
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .build());
        });

        labRepository.findByCodeIgnoreCase("LAB-003").orElseGet(() -> {
            log.info("Seeding default Computer Lab 3 (LAB-003)...");
            return labRepository.save(Lab.builder()
                    .name("Computer Lab 3")
                    .code("LAB-003")
                    .location("Building B, Room 201")
                    .description("Computer Networks & Cybersecurity Laboratory")
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .build());
        });

        // 3. Clean up old mock sample computers
        computerRepository.findAll().stream()
                .filter(c -> c.getHostname().startsWith("LAB-ALPHA") || c.getHostname().startsWith("LAB-BETA"))
                .forEach(c -> {
                    log.info("Cleaning up legacy sample computer {} and its inventory...", c.getHostname());
                    softwareInventoryRepository.deleteByComputerId(c.getId());
                    computerRepository.delete(c);
                });

        // 4. Ensure Primary Admin Computer (LAPTOP-PALBUQS2) exists in DB
        if (computerRepository.findByHostnameIgnoreCase("LAPTOP-PALBUQS2").isEmpty() && 
            computerRepository.findByAgentId("AGENT-9EA49A31").isEmpty()) {
            log.info("Seeding primary admin workstation endpoint (LAPTOP-PALBUQS2)...");

            Computer primary = Computer.builder()
                    .agentId("AGENT-9EA49A31")
                    .hostname("LAPTOP-PALBUQS2")
                    .computerName("Admin Workstation (LAPTOP-PALBUQS2)")
                    .ipAddress("10.33.199.161")
                    .macAddress("FA:54:F6:B4:98:23")
                    .osName("Windows 11 Pro 64-bit")
                    .osVersion("10.0.22631")
                    .lab(lab1)
                    .labName(lab1.getName())
                    .cpuModel("11th Gen Intel(R) Core(TM) i5-11260H @ 2.60GHz")
                    .totalRamMb(8192.0)
                    .agentVersion("1.0.0")
                    .status(ComputerStatus.ONLINE)
                    .lastSeenAt(Instant.now())
                    .build();

            computerRepository.save(primary);
            log.info("Primary admin workstation LAPTOP-PALBUQS2 seeded successfully in Computer Lab 1.");
        }

        // 5. Safely link any unassigned existing computers to Computer Lab 1
        computerRepository.findByLabIsNull().forEach(c -> {
            log.info("Migrating unassigned computer {} to Computer Lab 1", c.getHostname());
            c.setLab(lab1);
            c.setLabName(lab1.getName());
            computerRepository.save(c);
        });

        // 6. Seed Approved Software Catalog
        if (softwarePackageRepository.count() == 0) {
            log.info("Seeding pre-approved software packages catalog...");
            softwarePackageRepository.save(com.neurosys.backend.entity.SoftwarePackage.builder()
                    .name("VLC Media Player")
                    .version("3.0.21")
                    .installerUrl("https://get.videolan.org/vlc/3.0.21/win32/vlc-3.0.21-win32.exe")
                    .installerType(com.neurosys.backend.enums.InstallerType.EXE)
                    .silentArguments("/S")
                    .checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                    .supportedOs("Windows 10/11")
                    .active(true)
                    .build());

            softwarePackageRepository.save(com.neurosys.backend.entity.SoftwarePackage.builder()
                    .name("Notepad++")
                    .version("8.6.9")
                    .installerUrl("https://github.com/notepad-plus-plus/notepad-plus-plus/releases/download/v8.6.9/npp.8.6.9.Installer.x64.exe")
                    .installerType(com.neurosys.backend.enums.InstallerType.EXE)
                    .silentArguments("/S")
                    .checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                    .supportedOs("Windows 10/11")
                    .active(true)
                    .build());

            softwarePackageRepository.save(com.neurosys.backend.entity.SoftwarePackage.builder()
                    .name("7-Zip")
                    .version("24.07")
                    .installerUrl("https://www.7-zip.org/a/7z2407-x64.msi")
                    .installerType(com.neurosys.backend.enums.InstallerType.MSI)
                    .silentArguments("/qn /norestart")
                    .checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                    .supportedOs("Windows 10/11")
                    .active(true)
                    .build());

            softwarePackageRepository.save(com.neurosys.backend.entity.SoftwarePackage.builder()
                    .name("Git for Windows")
                    .version("2.46.0")
                    .installerUrl("https://github.com/git-for-windows/git/releases/download/v2.46.0.windows.1/Git-2.46.0-64-bit.exe")
                    .installerType(com.neurosys.backend.enums.InstallerType.EXE)
                    .silentArguments("/VERYSILENT /NORESTART")
                    .checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                    .supportedOs("Windows 10/11")
                    .active(true)
                    .build());

            log.info("Software packages catalog seeded successfully.");
        }
    }
}
