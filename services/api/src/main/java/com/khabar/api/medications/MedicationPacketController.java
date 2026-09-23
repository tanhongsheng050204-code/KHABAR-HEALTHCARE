package com.khabar.api.medications;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientAccessPolicy;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.service.AgentClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Authenticated, non-persistent forwarding of a consented medicine-packet photo. */
@RestController
public class MedicationPacketController {
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final PatientAccessPolicy policy;
    private final AgentClientService agents;

    public MedicationPacketController(CurrentUser currentUser, PatientRepository patients,
                                      PatientAccessPolicy policy, AgentClientService agents) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.policy = policy;
        this.agents = agents;
    }

    @PostMapping(path = "/api/patients/{patientId}/medications/packet-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(readOnly = true)
    public Map<String, Object> readPacket(@PathVariable UUID patientId,
                                          @RequestParam("image") MultipartFile image,
                                          @RequestParam("consentConfirmed") boolean consentConfirmed,
                                          @AuthenticationPrincipal Jwt jwt) throws IOException {
        AppUser user = currentUser.from(jwt);
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!policy.isPatientOrTheirClinic(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!consentConfirmed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirm before sending the image for label reading.");
        }
        if (image.isEmpty() || image.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image no larger than 8 MB.");
        }
        byte[] bytes = image.getBytes();
        String mimeType = detectedImageType(bytes);
        if (mimeType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid JPEG, PNG, or WebP image.");
        }
        try {
            return agents.readMedicinePacket(bytes, mimeType);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Packet reading is unavailable. You can still add the medicine by typing its name.");
        }
    }

    private static String detectedImageType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
