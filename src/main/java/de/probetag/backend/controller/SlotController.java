package de.probetag.backend.controller;

import de.probetag.backend.dto.SlotRequest;
import de.probetag.backend.dto.SlotResponse;
import de.probetag.backend.security.AuthenticatedUser;
import de.probetag.backend.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    // ---------- öffentlich ----------

    @GetMapping("/slots")
    public List<SlotResponse> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from) {
        return slotService.search(city, sector, from);
    }

    @GetMapping("/slots/{id}")
    public SlotResponse findById(@PathVariable UUID id) {
        return slotService.findById(id);
    }

    // ---------- nur Unternehmen ----------

    @PostMapping("/slots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    public SlotResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody SlotRequest request) {
        return slotService.create(user.id(), request);
    }

    @PutMapping("/slots/{id}")
    @PreAuthorize("hasRole('COMPANY')")
    public SlotResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable UUID id,
                               @Valid @RequestBody SlotRequest request) {
        return slotService.update(user.id(), id, request);
    }

    @PostMapping("/slots/{id}/cancel")
    @PreAuthorize("hasRole('COMPANY')")
    public SlotResponse cancel(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable UUID id) {
        return slotService.cancel(user.id(), id);
    }

    @GetMapping("/companies/me/slots")
    @PreAuthorize("hasRole('COMPANY')")
    public List<SlotResponse> mySlots(@AuthenticationPrincipal AuthenticatedUser user) {
        return slotService.findMySlots(user.id());
    }
}