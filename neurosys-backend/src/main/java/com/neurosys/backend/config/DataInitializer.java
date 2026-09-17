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

        // 6. Seed Approved Software Catalog (Popular 1-Click Apps)
        String[][] defaultPackages = {
            {"Google Chrome", "Latest", "https://dl.google.com/tag/s/appguid%3D%7B8A69D345-D564-463C-AFF1-A69D9E530F96%7D%26iid%3D%7BEB5A3CDA-9C78-DF7B-60C1-4D6F18F2EED6%7D%26lang%3Den%26name%3DChrome%26needsadmin%3Dtrue/chrome/install/ChromeStandaloneSetup64.exe", "EXE", "/silent /install"},
            {"Visual Studio Code", "1.93.0", "https://update.code.visualstudio.com/latest/win32-x64-user/stable", "EXE", "/verysilent /suppressmsgboxes /norestart"},
            {"Python 3.12", "3.12.5", "https://www.python.org/ftp/python/3.12.5/python-3.12.5-amd64.exe", "EXE", "/quiet InstallAllUsers=1 PrependPath=1"},
            {"VLC Media Player", "3.0.21", "https://get.videolan.org/vlc/3.0.21/win32/vlc-3.0.21-win32.exe", "EXE", "/S"},
            {"Git for Windows", "2.46.0", "https://github.com/git-for-windows/git/releases/download/v2.46.0.windows.1/Git-2.46.0-64-bit.exe", "EXE", "/VERYSILENT /NORESTART"},
            {"Notepad++", "8.6.9", "https://github.com/notepad-plus-plus/notepad-plus-plus/releases/download/v8.6.9/npp.8.6.9.Installer.x64.exe", "EXE", "/S"},
            {"7-Zip", "24.07", "https://www.7-zip.org/a/7z2407-x64.msi", "MSI", "/qn /norestart"},
            {"Zoom Meetings", "Latest", "https://zoom.us/client/latest/ZoomInstallerFull.msi", "MSI", "/qn /norestart"},
            {"WinRAR", "7.01", "https://www.rarlab.com/rar/winrar-x64-701.exe", "EXE", "/s"}
        };

        for (String[] pkgInfo : defaultPackages) {
            String name = pkgInfo[0];
            if (softwarePackageRepository.findAll().stream().noneMatch(p -> p.getName().equalsIgnoreCase(name))) {
                log.info("Seeding pre-approved software package: {}", name);
                softwarePackageRepository.save(com.neurosys.backend.entity.SoftwarePackage.builder()
                        .name(name)
                        .version(pkgInfo[1])
                        .installerUrl(pkgInfo[2])
                        .installerType("MSI".equalsIgnoreCase(pkgInfo[3]) ? com.neurosys.backend.enums.InstallerType.MSI : com.neurosys.backend.enums.InstallerType.EXE)
                        .silentArguments(pkgInfo[4])
                        .checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                        .supportedOs("Windows 10/11")
                        .active(true)
                        .build());
            }
        }
    }
}
