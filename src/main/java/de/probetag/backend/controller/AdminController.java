package de.probetag.backend.controller;

import de.probetag.backend.dto.AdminCompanyResponse;
import de.probetag.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/companies")
    public List<AdminCompanyResponse> companies(@RequestParam(defaultValue = "false") boolean verified) {
        return adminService.findCompanies(verified);
    }

    @PostMapping("/companies/{id}/verify")
    public AdminCompanyResponse verify(@PathVariable UUID id) {
        return adminService.verifyCompany(id);
    }

    @PostMapping("/users/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable UUID id) {
        adminService.setUserEnabled(id, false);
    }

    @PostMapping("/users/{id}/unblock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable UUID id) {
        adminService.setUserEnabled(id, true);
    }
}