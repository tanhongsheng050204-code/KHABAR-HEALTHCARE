package com.khabar.api.pathways;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientGate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** A patient's condition pathways: the doctor starts, changes and ends them; the patient and alert caregivers can read them. */
@RestController
public class PathwayController {

    private final CurrentUser currentUser;
    private final PatientGate gate;
    private final Pathways pathways;
    private final PatientPathwayRepository repository;

    public PathwayController(CurrentUser currentUser, PatientGate gate, Pathways pathways, PatientPathwayRepository repository) {
        this.currentUser = currentUser;
        this.gate = gate;
        this.pathways = pathways;
        this.repository = repository;
    }

    @GetMapping("/api/pathways")
    public Collection<Pathways.Definition> definitions() {
        return Pathways.DEFINITIONS.values();
    }

    @GetMapping("/api/patients/{id}/pathways")
    @Transactional
    public List<Pathways.PathwayView> list(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Patient patient = gate.read(currentUser.from(jwt), id);
        return pathways.views(patient.getId());
    }

    @PostMapping("/api/patients/{id}/pathways")
    @Transactional
    public Pathways.PathwayView start(@PathVariable UUID id, @RequestBody Pathways.Input input, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = currentUser.from(jwt);
        Patient patient = gate.doctorAt(doctor, id);
        return pathways.start(patient, doctor, input);
    }

    @PutMapping("/api/patients/{id}/pathways/{pathwayId}")
    @Transactional
    public Pathways.PathwayView update(@PathVariable UUID id, @PathVariable UUID pathwayId, @RequestBody Pathways.Input input,
                                       @AuthenticationPrincipal Jwt jwt) {
        gate.doctorAt(currentUser.from(jwt), id);
        return pathways.update(find(id, pathwayId), input);
    }

    @DeleteMapping("/api/patients/{id}/pathways/{pathwayId}")
    @Transactional
    public void end(@PathVariable UUID id, @PathVariable UUID pathwayId, @AuthenticationPrincipal Jwt jwt) {
        gate.doctorAt(currentUser.from(jwt), id);
        pathways.end(find(id, pathwayId));
    }

    private PatientPathway find(UUID patientId, UUID pathwayId) {
        return repository.findById(pathwayId)
                .filter(p -> p.getPatientId().equals(patientId) && p.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
